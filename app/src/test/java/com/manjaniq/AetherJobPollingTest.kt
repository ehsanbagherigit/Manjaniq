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
    fun boundsAreSaneForAForegroundService() {
        assertTrue(AetherJobPolling.DEFAULT_TIMEOUT_MS >= 30_000L)
        assertTrue(AetherJobPolling.DEFAULT_TIMEOUT_MS <= 600_000L)
        assertTrue(AetherJobPolling.POLL_INTERVAL_MS in 50L..5_000L)
        assertTrue(AetherJobPolling.POLL_INTERVAL_MS < AetherJobPolling.DEFAULT_TIMEOUT_MS)
    }
}
