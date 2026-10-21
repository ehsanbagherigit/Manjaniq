package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherJobPolling
import com.manjaniq.core.AetherRepository
import com.manjaniq.core.AetherScanOutcome
import com.manjaniq.core.models.AetherState
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the Aether screen is told after each scan outcome.
 *
 * `AetherService` cannot run on the host JVM - it is a foreground service over
 * the real FFI and real sockets - so these tests drive the repository exactly as
 * the service does. [begin], [succeed] and [fail] mirror `startAether()` step for
 * step: `requestStart` publishes busy+STARTING and clears the previous error, a
 * finished scan publishes the endpoint and RUNNING, and `failed()` reports a stop
 * the user asked for as STOPPED with no error and anything else as ERROR.
 *
 * What is pinned here is the contract the panel renders: no outcome may leave
 * Aether looking like it is running when no endpoint was chosen, a failure must
 * leave the state recoverable for a retry, and a cancellation must not be
 * reported as a failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherScanStateTest {

    @Before
    fun resetState() {
        AetherRepository.clearLogs()
        AetherRepository.clearError()
        AetherRepository.updateState(AetherState.STOPPED)
    }

    @After
    fun clearLogs() {
        // AetherRepository is a singleton: its log ring outlives one test method.
        AetherRepository.clearLogs()
    }

    /** `AetherRepository.requestStart`: busy, STARTING, previous error cleared. */
    private fun begin() {
        AetherRepository.clearError()
        AetherRepository.updateState(AetherState.STARTING)
    }

    /** `AetherService` after a successful scan: endpoint, log line, RUNNING. */
    private fun succeed(reply: JSONObject, socks: String = "127.0.0.1:1819"): String {
        val endpoint = AetherScanOutcome.endpointFrom(reply)
            ?: throw IllegalStateException("fixture must carry a usable endpoint")
        AetherRepository.log("OK", "Scan chose endpoint $endpoint")
        AetherRepository.setEndpoint(endpoint, socks)
        AetherRepository.onServiceActionFinished(null, AetherState.RUNNING)
        return endpoint
    }

    /** `AetherService.failed()`, including its cancellation branch. */
    private fun fail(message: String, cancelled: Boolean = false) {
        if (cancelled) {
            AetherRepository.log("INFO", "Aether stopped while starting: $message")
            AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
        } else {
            AetherRepository.onServiceActionFinished(message, AetherState.ERROR)
        }
    }

    private fun messages(level: String) =
        AetherRepository.logs.value.filter { it.level == level }.map { it.message }

    private fun scanReply(ip: String = "198.51.100.7", port: Int = 443) =
        JSONObject("""{"ok":true,"endpoint":{"ip":"$ip","port":$port,"rtt_ms":37}}""")

    @Test
    fun aSuccessfulScanPublishesTheChosenEndpointAndTheRunningState() {
        begin()
        assertTrue(AetherRepository.uiState.value.busy)
        assertEquals(AetherState.STARTING, AetherRepository.uiState.value.state)

        val endpoint = succeed(scanReply())

        val ui = AetherRepository.uiState.value
        assertEquals("198.51.100.7:443", endpoint)
        assertEquals(AetherState.RUNNING, ui.state)
        assertTrue(ui.state.isRunning())
        assertFalse("the start button must not stay in its loading state", ui.busy)
        assertNull(ui.lastError)
        assertEquals(endpoint, ui.endpoint)
        assertEquals("127.0.0.1:1819", ui.socksEndpoint)
        assertTrue(messages("OK").any { it.contains("Scan chose endpoint 198.51.100.7:443") })
        assertTrue("a success reports no error", messages("ERROR").isEmpty())
    }

    @Test
    fun noCleanEndpointLeavesAetherInErrorAndNeverRunning() {
        begin()
        fail(AetherScanOutcome.report("prober: no clean endpoint found", 140_000L))

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.ERROR, ui.state)
        assertFalse("Aether must not look like it is running", ui.state.isRunning())
        assertFalse("and it must not look like it is still starting", ui.busy)
        assertEquals("", "no endpoint may be published for a scan that chose none", ui.endpoint)
        val error = ui.lastError
        assertTrue("the failure is shown: $error", error != null && error.contains("no clean endpoint"))
        assertTrue(messages("ERROR").isNotEmpty())
    }

    @Test
    fun anExpiredPollDeadlineIsATimeoutAndNotARunningTunnel() {
        begin()
        val timeoutMs = AetherJobPolling.scanTimeoutFor("masque", "ironclad")
        fail(AetherScanOutcome.report(null, timeoutMs))

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.ERROR, ui.state)
        assertFalse(ui.busy)
        assertEquals("", ui.endpoint)
        assertTrue("says it timed out: ${ui.lastError}", ui.lastError!!.contains("timed out after ${timeoutMs / 1000}s"))
    }

    @Test
    fun aStopTheUserAskedForIsReportedAsCancellationNotAsAFailure() {
        begin()
        // Aether answers a cancelled job with an error, so the service's
        // cancellation branch is what turns it back into a cancellation.
        fail(AetherScanOutcome.report("cancelled", 60_000L), cancelled = true)

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.STOPPED, ui.state)
        assertFalse(ui.state.isRunning())
        assertFalse(ui.busy)
        assertNull("a cancellation is not an error", ui.lastError)
        assertEquals("", ui.endpoint)
        assertTrue("and it is not logged as one", messages("ERROR").isEmpty())
        assertTrue(messages("INFO").any { it.contains("Aether stopped while starting") })
    }

    @Test
    fun stoppingDuringStartupEndsInStoppedWithoutAnError() {
        begin()
        AetherRepository.updateState(AetherState.STOPPING)
        assertEquals(AetherState.STOPPING, AetherRepository.uiState.value.state)

        AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.STOPPED, ui.state)
        assertFalse("no transition state may be left behind", ui.busy)
        assertNull(ui.lastError)
    }

    @Test
    fun aRetryAfterAFailedScanClearsTheFailureAndCanSucceed() {
        begin()
        fail(AetherScanOutcome.report("prober: no clean endpoint found", 140_000L))
        assertEquals(AetherState.ERROR, AetherRepository.uiState.value.state)

        begin()
        val afterRetryStart = AetherRepository.uiState.value
        assertNull("the previous failure must not survive into the retry", afterRetryStart.lastError)
        assertEquals(AetherState.STARTING, afterRetryStart.state)
        assertTrue(afterRetryStart.busy)

        succeed(scanReply(ip = "203.0.113.9"))

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.RUNNING, ui.state)
        assertEquals("203.0.113.9:443", ui.endpoint)
        assertNull(ui.lastError)
    }

    @Test
    fun aRetryAfterACancelledStartAlsoWorks() {
        begin()
        fail(AetherScanOutcome.report("cancelled", 60_000L), cancelled = true)
        assertEquals(AetherState.STOPPED, AetherRepository.uiState.value.state)

        begin()
        assertEquals(AetherState.STARTING, AetherRepository.uiState.value.state)
        succeed(scanReply())
        assertEquals(AetherState.RUNNING, AetherRepository.uiState.value.state)
    }

    @Test
    fun everyFailureLeavesTheServiceIdleSoTheNextStartIsNotBlocked() {
        val rawMessages = listOf(
            "prober: no clean endpoint found",
            "ech: could not fetch the ECH configuration",
            "quic: Timeout",
            "masque: bad response",
            "identity refused: rate limited",
            "there is no job 3",
            null
        )
        rawMessages.forEach { raw ->
            resetState()
            begin()
            fail(AetherScanOutcome.report(raw, 140_000L))
            val ui = AetherRepository.uiState.value
            assertFalse("$raw must not leave Aether running", ui.state.isRunning())
            assertFalse("$raw must not leave a transition behind", ui.busy)
            assertEquals("$raw must not publish an endpoint", "", ui.endpoint)
            assertTrue("$raw must say what happened: ${ui.lastError}", !ui.lastError.isNullOrBlank())
        }
    }

    @Test
    fun theFailureTextReachesTheOperationalLogAsAnErrorEntry() {
        begin()
        val report = AetherScanOutcome.report("prober: no clean endpoint found", 140_000L)
        fail(report)

        val errors = messages("ERROR")
        assertEquals(1, errors.size)
        assertEquals(report, errors.single())
        // The panel renders these entries; the level drives its problem filter.
        assertTrue(AetherRepository.logs.value.single { it.message == report }.level == "ERROR")
    }
}
