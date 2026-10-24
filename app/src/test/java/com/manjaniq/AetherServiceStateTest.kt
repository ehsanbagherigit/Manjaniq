package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherRepository
import com.manjaniq.core.models.AetherState
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
 * What the Aether screen is told after each start outcome.
 *
 * `AetherService` cannot run on the host JVM - it is a foreground service over
 * the real FFI and real sockets - so these tests drive the repository exactly as
 * the service does. [begin], [succeed] and [fail] mirror `startAether()` step for
 * step: `requestStart` publishes busy+STARTING and clears the previous error, a
 * started engine publishes the endpoint and RUNNING, and `failed()` reports a stop
 * the user asked for as STOPPED with no error and anything else as ERROR.
 *
 * What is pinned here is the contract the panel renders: no outcome may leave
 * Aether looking like it is running when no endpoint was chosen, a failure must
 * leave the state recoverable for a retry, and a cancellation must not be
 * reported as a failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherServiceStateTest {

    @Before
    fun resetState() {
        AetherRepository.clearLogs()
        AetherRepository.clearError()
        AetherRepository.updateState(AetherState.STOPPED)
        // AetherRepository is a Kotlin object: Robolectric resets the Application
        // but not object state, and JUnit's method order is not the source order,
        // so the endpoint an earlier test published would still be sitting here.
        // Restoring the values AetherUiState ships with keeps these tests
        // order-independent - a failed scan must publish no endpoint at all.
        AetherRepository.setEndpoint("", "127.0.0.1:1819")
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

    /**
     * `AetherService.startEngine` once the loopback probe is answered: it publishes
     * the configured peer (or `scan` when the engine is doing the choosing), the
     * listener it probes, and RUNNING.
     */
    private fun succeed(peer: String = "", socks: String = "127.0.0.1:1819"): String {
        val published = peer.trim().ifEmpty { "scan" }
        AetherRepository.log("OK", "SOCKS5 $socks is accepting connections")
        AetherRepository.setEndpoint(published, socks)
        AetherRepository.onServiceActionFinished(null, AetherState.RUNNING)
        return published
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

    @Test
    fun aStartThatReachesAServingTunnelPublishesTheEndpointAndRunning() {
        begin()
        assertTrue(AetherRepository.uiState.value.busy)
        assertEquals(AetherState.STARTING, AetherRepository.uiState.value.state)

        val endpoint = succeed()

        val ui = AetherRepository.uiState.value
        assertEquals("scan", endpoint)
        assertEquals(AetherState.RUNNING, ui.state)
        assertTrue(ui.state.isRunning())
        assertFalse("the start button must not stay in its loading state", ui.busy)
        assertNull(ui.lastError)
        assertEquals(endpoint, ui.endpoint)
        assertEquals("127.0.0.1:1819", ui.socksEndpoint)
        assertTrue(messages("OK").any { it.contains("SOCKS5 127.0.0.1:1819 is accepting connections") })
        assertTrue("a success reports no error", messages("ERROR").isEmpty())
    }

    @Test
    fun aForcedPeerIsWhatThePanelIsTold() {
        begin()
        val endpoint = succeed(peer = "203.0.113.9:443")

        val ui = AetherRepository.uiState.value
        assertEquals("203.0.113.9:443", endpoint)
        assertEquals("203.0.113.9:443", ui.endpoint)
        assertEquals(AetherState.RUNNING, ui.state)
    }

    @Test
    fun aFailedStartLeavesAetherInErrorAndNeverRunning() {
        begin()
        fail("Aether failed: prober: no clean endpoint found")

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.ERROR, ui.state)
        assertFalse("Aether must not look like it is running", ui.state.isRunning())
        assertFalse("and it must not look like it is still starting", ui.busy)
        assertEquals("no endpoint may be published for a start that failed", "", ui.endpoint)
        val error = ui.lastError
        assertTrue("the failure is shown: $error", error != null && error.contains("no clean endpoint"))
        assertTrue(messages("ERROR").isNotEmpty())
    }

    @Test
    fun anEngineThatNeverCameUpIsAnErrorAndNotARunningTunnel() {
        begin()
        fail("Aether failed: the job ended before SOCKS5 127.0.0.1:1819 came up (state=closed)")

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.ERROR, ui.state)
        assertFalse(ui.busy)
        assertEquals("", ui.endpoint)
        assertTrue(
            "says what happened: ${ui.lastError}",
            ui.lastError!!.contains("before SOCKS5 127.0.0.1:1819 came up")
        )
    }

    @Test
    fun aStopTheUserAskedForIsReportedAsCancellationNotAsAFailure() {
        begin()
        // Aether answers a cancelled job with an error, so the service's
        // cancellation branch is what turns it back into a cancellation.
        fail("Aether stopped", cancelled = true)

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
    fun aRetryAfterAFailedStartClearsTheFailureAndCanSucceed() {
        begin()
        fail("Aether failed: prober: no clean endpoint found")
        assertEquals(AetherState.ERROR, AetherRepository.uiState.value.state)

        begin()
        val afterRetryStart = AetherRepository.uiState.value
        assertNull("the previous failure must not survive into the retry", afterRetryStart.lastError)
        assertEquals(AetherState.STARTING, afterRetryStart.state)
        assertTrue(afterRetryStart.busy)

        succeed(peer = "203.0.113.9:443")

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.RUNNING, ui.state)
        assertEquals("203.0.113.9:443", ui.endpoint)
        assertNull(ui.lastError)
    }

    @Test
    fun aRetryAfterACancelledStartAlsoWorks() {
        begin()
        fail("Aether stopped", cancelled = true)
        assertEquals(AetherState.STOPPED, AetherRepository.uiState.value.state)

        begin()
        assertEquals(AetherState.STARTING, AetherRepository.uiState.value.state)
        succeed()
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
            fail(raw?.let { "Aether failed: $it" } ?: "Aether failed: unknown error")
            val ui = AetherRepository.uiState.value
            assertFalse("$raw must not leave Aether running", ui.state.isRunning())
            assertFalse("$raw must not leave a transition behind", ui.busy)
            assertEquals("$raw must not publish an endpoint", "", ui.endpoint)
            assertTrue("$raw must say what happened: ${ui.lastError}", !ui.lastError.isNullOrBlank())
        }
    }

    @Test
    fun theRepositoryIsRestoredToItsShippedStateBetweenOutcomes() {
        // AetherRepository is a Kotlin object, so everything one test publishes
        // survives into the next unless it is put back. This is the contract
        // resetState() implements, and the reason three assertions about "no
        // endpoint published" used to fail depending on JUnit's method order.
        begin()
        succeed()
        val dirty = AetherRepository.uiState.value
        assertEquals(AetherState.RUNNING, dirty.state)
        assertEquals("scan", dirty.endpoint)
        assertTrue(AetherRepository.logs.value.isNotEmpty())

        resetState()

        val ui = AetherRepository.uiState.value
        assertEquals(AetherState.STOPPED, ui.state)
        assertFalse("no transition may survive", ui.busy)
        assertNull("no failure may survive", ui.lastError)
        assertEquals("no endpoint may survive", "", ui.endpoint)
        assertEquals("127.0.0.1:1819", ui.socksEndpoint)
        assertTrue("no log entry may survive", AetherRepository.logs.value.isEmpty())
    }

    @Test
    fun theFailureTextReachesTheOperationalLogAsAnErrorEntry() {
        begin()
        val report = "Aether failed: prober: no clean endpoint found"
        fail(report)

        val errors = messages("ERROR")
        assertEquals(1, errors.size)
        assertEquals(report, errors.single())
        // The panel renders these entries; the level drives its problem filter.
        assertTrue(AetherRepository.logs.value.single { it.message == report }.level == "ERROR")
    }
}
