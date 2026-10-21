package com.manjaniq

import com.manjaniq.core.AetherJobPolling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Aether job-polling deadline arithmetic.
 *
 * The bug these pin: `AetherService.pollJob(tunnelJobId, timeoutMs =
 * Long.MAX_VALUE)` computed the deadline as `now + Long.MAX_VALUE`, which
 * overflows to a negative number. The poll loop therefore exited on its first
 * check, `pollJob` returned null, and the service freed the tunnel job it had
 * just started — and `aether_job_free` cancels the job it removes
 * (third_party/Aether/aether/src/ffi.rs). The tunnel died right after "up".
 *
 * Plain JUnit: [AetherJobPolling] is pure arithmetic, no Android types.
 */
class AetherJobPollingTest {

    @Test
    fun nullTimeoutMeansUnboundedPolling() {
        assertNull(AetherJobPolling.deadlineFor(1_000L, null))
        assertTrue(AetherJobPolling.shouldKeepPolling(Long.MAX_VALUE, null))
        assertTrue(AetherJobPolling.shouldKeepPolling(0L, null))
    }

    @Test
    fun maxLongTimeoutNoLongerWrapsToAnExpiredDeadline() {
        val now = System.currentTimeMillis()
        // The old expression: now + Long.MAX_VALUE < 0, i.e. already expired.
        assertTrue("precondition: the naive sum must overflow", now + Long.MAX_VALUE < 0)
        // The fix: an overflowing bound is treated as unbounded, not as expired.
        assertNull(AetherJobPolling.deadlineFor(now, Long.MAX_VALUE))
        assertTrue(AetherJobPolling.shouldKeepPolling(now, AetherJobPolling.deadlineFor(now, Long.MAX_VALUE)))
    }

    @Test
    fun overflowNearTheEndOfTheClockIsAlsoUnbounded() {
        assertNull(AetherJobPolling.deadlineFor(Long.MAX_VALUE - 10, 100))
        assertNull(AetherJobPolling.deadlineFor(Long.MAX_VALUE, 1))
    }

    @Test
    fun ordinaryTimeoutsKeepTheirMeaning() {
        assertEquals(1_500L, AetherJobPolling.deadlineFor(1_000L, 500L))
        assertEquals(1_000L, AetherJobPolling.deadlineFor(1_000L, 0L))
        assertTrue(AetherJobPolling.shouldKeepPolling(1_499L, 1_500L))
        assertFalse("the deadline itself is exclusive, as before", AetherJobPolling.shouldKeepPolling(1_500L, 1_500L))
        assertFalse(AetherJobPolling.shouldKeepPolling(1_501L, 1_500L))
        // A zero timeout still means "do not wait".
        assertFalse(AetherJobPolling.shouldKeepPolling(1_000L, AetherJobPolling.deadlineFor(1_000L, 0L)))
    }

    @Test
    fun scanBudgetsMatchTheEnginesOwnDeadlines() {
        // prober::ScanMode::strategy (MASQUE) and wg_prober::WgScanMode::strategy
        // (WireGuard) in third_party/Aether/aether/src.
        val masque = mapOf(
            "turbo" to 45_000L, "balanced" to 120_000L, "thorough" to 300_000L,
            "verified" to 60_000L, "ironclad" to 180_000L
        )
        val wireguard = mapOf(
            "turbo" to 30_000L, "balanced" to 80_000L, "thorough" to 250_000L,
            "verified" to 60_000L, "ironclad" to 180_000L
        )
        masque.forEach { (mode, budget) ->
            assertEquals("masque/$mode", budget, AetherJobPolling.scanBudgetFor("masque", mode))
        }
        wireguard.forEach { (mode, budget) ->
            assertEquals("wg/$mode", budget, AetherJobPolling.scanBudgetFor("wg", mode))
        }
    }

    @Test
    fun theScanWaitIsNeverShorterThanTheScanItself() {
        listOf("masque", "wg").forEach { transport ->
            listOf("turbo", "balanced", "thorough", "verified", "ironclad").forEach { mode ->
                val budget = AetherJobPolling.scanBudgetFor(transport, mode)
                val timeout = AetherJobPolling.scanTimeoutFor(transport, mode)
                assertTrue(
                    "$transport/$mode must wait at least as long as Aether scans",
                    timeout >= budget
                )
                assertEquals(budget + AetherJobPolling.SCAN_MARGIN_MS, timeout)
                // The modes that used to be cut off by the flat 120 s default.
                if (budget > AetherJobPolling.DEFAULT_TIMEOUT_MS) {
                    assertTrue(
                        "$transport/$mode outlives the old flat default",
                        timeout > AetherJobPolling.DEFAULT_TIMEOUT_MS
                    )
                }
            }
        }
    }

    @Test
    fun thoroughAndIroncladAreNoLongerImpossible() {
        // With the flat DEFAULT_TIMEOUT_MS these two could never succeed: the poll
        // expired first and `aether_job_free` cancelled the scan that was working.
        listOf("thorough", "ironclad").forEach { mode ->
            assertTrue(AetherJobPolling.scanBudgetFor("masque", mode) > AetherJobPolling.DEFAULT_TIMEOUT_MS)
            assertTrue(AetherJobPolling.scanBudgetFor("wg", mode) > AetherJobPolling.DEFAULT_TIMEOUT_MS)
        }
    }

    @Test
    fun unknownTransportsAndModesFallBackTheWayTheCoreDoes() {
        // api::Transport::parse: anything that is not "wg" is MASQUE.
        assertEquals(
            AetherJobPolling.scanBudgetFor("masque", "balanced"),
            AetherJobPolling.scanBudgetFor("gool", "balanced")
        )
        assertEquals(
            AetherJobPolling.scanBudgetFor("masque", "balanced"),
            AetherJobPolling.scanBudgetFor("", "balanced")
        )
        // ScanMode::parse / WgScanMode::parse: an unrecognised mode is Balanced.
        listOf("masque", "wg").forEach { transport ->
            assertEquals(
                AetherJobPolling.scanBudgetFor(transport, "balanced"),
                AetherJobPolling.scanBudgetFor(transport, "yolo")
            )
            assertEquals(
                AetherJobPolling.scanBudgetFor(transport, "balanced"),
                AetherJobPolling.scanBudgetFor(transport, "")
            )
            // The parsers lowercase and trim their input.
            assertEquals(
                AetherJobPolling.scanBudgetFor(transport, "turbo"),
                AetherJobPolling.scanBudgetFor(transport, "  TURBO ")
            )
        }
    }

    @Test
    fun aLongerScanWaitDoesNotMakeCancellationSlower() {
        // Cancellation is not a deadline: it goes through jobFree/jobCancel, and
        // the poll loop's granularity only bounds how quickly the loop itself can
        // notice anything. Waiting longer for Aether's own budget must not make a
        // stop less responsive, and the wait must stay bounded.
        assertTrue(AetherJobPolling.POLL_INTERVAL_MS <= 1_000L)
        val longest = AetherJobPolling.scanTimeoutFor("masque", "thorough")
        assertTrue("the wait stays bounded: $longest", longest <= 400_000L)
        // The deadline is exclusive, so the loop stops at it instead of spinning on.
        assertFalse(AetherJobPolling.shouldKeepPolling(longest, longest))
        assertTrue(AetherJobPolling.shouldKeepPolling(longest - 1, longest))
    }

    @Test
    fun boundsAreSaneForAForegroundService() {
        assertTrue(AetherJobPolling.DEFAULT_TIMEOUT_MS >= 30_000L)
        assertTrue(AetherJobPolling.DEFAULT_TIMEOUT_MS <= 600_000L)
        assertTrue(AetherJobPolling.POLL_INTERVAL_MS in 50L..5_000L)
        assertTrue(AetherJobPolling.POLL_INTERVAL_MS < AetherJobPolling.DEFAULT_TIMEOUT_MS)
    }
}
