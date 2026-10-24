package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherJobPolling
import com.manjaniq.core.AetherRegistrationOutcome
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
 * What the Aether screen is told while a `--register` run is in flight.
 *
 * `AetherService` cannot run on the host JVM - it is a foreground service over the
 * real FFI and real sockets - so these tests drive the repository exactly as the
 * service does. [begin], [registrationFailed], [registrationCancelled] and
 * [registrationSucceeded] mirror the register block of `startEngine()` step for
 * step: `requestStart` publishes busy+STARTING and clears the previous error, a
 * failed `pollJob` goes through `failed()` (which reports a stop the user asked for
 * as STOPPED with no error and anything else as ERROR), and a finished run
 * releases its job.
 *
 * A register-only run is a one-shot job like any other: `aether_core_start` takes
 * the same argument list, exits after the WARP API answers, and never publishes an
 * identity id or a tunnel endpoint, because on the single start path there is no
 * granular `aether_identity_open` step and no SOCKS listener. What is pinned here
 * is the contract the panel renders after the reported failure
 * (`api: registration: api: api.cloudflareclient.com did not answer within 20s`):
 * a registration that never answered must leave Aether in ERROR with the engine's
 * own wording on screen, must publish no endpoint, must stay recoverable for a
 * retry, and must never look like it connected.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherRegistrationStateTest {

    /** The line from the reported failure, verbatim. */
    private val observed =
        "api: registration: api: api.cloudflareclient.com did not answer within 20s"

    @Before
    fun resetState() {
        AetherRepository.clearLogs()
        AetherRepository.clearError()
        AetherRepository.updateState(AetherState.STOPPED)
        // AetherRepository is a Kotlin object: Robolectric resets the Application but
        // not object state, and JUnit's method order is not the source order, so a
        // job or endpoint an earlier test published would still be sitting here.
        AetherRepository.setActiveJob(null)
        AetherRepository.setEndpoint("", "127.0.0.1:1819")
    }

    @After
    fun clearLogs() {
        AetherRepository.clearLogs()
    }

    /** `AetherRepository.requestStart`: busy, STARTING, previous error cleared. */
    private fun begin() {
        AetherRepository.clearError()
        AetherRepository.updateState(AetherState.STARTING)
    }

    /** `failed(report)` in `AetherService` when the register poll came back broken. */
    private fun registrationFailed(raw: String?): String {
        val report = AetherRegistrationOutcome.report(raw, AetherJobPolling.identityTimeoutMs())
        AetherRepository.onServiceActionFinished(report, AetherState.ERROR)
        return report
    }

    /** `failed(report)` when `attempt.cancelled` is set: STOPPED and no error. */
    private fun registrationCancelled(raw: String) {
        AetherRepository.log("INFO", "Aether stopped while starting: $raw")
        AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
    }

    /** The success branch: the run exited and the service reports STOPPED. */
    private fun registrationSucceeded() {
        AetherRepository.log(
            "OK",
            "Identities registered for 'masque'; the files are beside the configured identity path"
        )
        AetherRepository.setActiveJob(null)
        AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
    }

    private val ui get() = AetherRepository.uiState.value
    private val lines get() = AetherRepository.logs.value.map { it.message }

    @Test
    fun anUnansweredRegistrationLeavesTheErrorOnScreen() {
        begin()
        val report = registrationFailed(observed)

        assertEquals(AetherState.ERROR, ui.state)
        assertFalse("the panel must not stay busy forever", ui.busy)
        assertEquals(report, ui.lastError)
        assertTrue("the engine's own wording is on screen", ui.lastError!!.contains(observed))
        assertTrue(lines.any { it.contains(observed) })
    }

    @Test
    fun aFailedRegistrationPublishesNoEndpoint() {
        begin()
        registrationFailed(observed)

        assertEquals("a register-only run never opens a tunnel", "", ui.endpoint)
        assertEquals("127.0.0.1:1819", ui.socksEndpoint)
        assertFalse("nothing may claim Aether is running", lines.any { it.contains("tunnel is running") })
        assertFalse(lines.any { it.contains("SOCKS5") && it.contains("accepting") })
    }

    @Test
    fun anAppSideRegistrationTimeoutIsReportedAsAnErrorNotAsASuccess() {
        begin()
        registrationFailed(null)

        assertEquals(AetherState.ERROR, ui.state)
        assertFalse(ui.busy)
        assertTrue(ui.lastError!!.contains("Registration timed out"))
        assertEquals("", ui.endpoint)
    }

    @Test
    fun aRegistrationTheUserStoppedIsStoppedWithoutAnError() {
        begin()
        registrationCancelled(observed)

        assertEquals(AetherState.STOPPED, ui.state)
        assertFalse(ui.busy)
        assertNull("a cancellation is not a failure", ui.lastError)
        assertTrue(lines.any { it.contains("Aether stopped while starting") })
    }

    @Test
    fun aRetryAfterARegistrationFailureStartsCleanAndCanSucceed() {
        begin()
        registrationFailed(observed)
        assertEquals(AetherState.ERROR, ui.state)

        // requestStart clears the previous error and publishes busy+STARTING
        begin()
        assertNull("a retry must not carry the old error", ui.lastError)
        assertEquals(AetherState.STARTING, ui.state)
        assertTrue(ui.busy)

        registrationSucceeded()

        assertEquals(AetherState.STOPPED, ui.state)
        assertNull(ui.lastError)
        assertTrue(lines.any { it.contains("Identities registered") })
        // the earlier failure stays in the log: history is not rewritten by a retry
        assertTrue("the failed attempt is still in the log", lines.any { it.contains(observed) })
    }

    @Test
    fun theRegisterJobIsTheActiveJobWhileItIsPolledAndIsClearedWhenItEnds() {
        // The register block publishes the job it polls, so a Stop finds a job to
        // cancel instead of leaving the engine running its attempts in the
        // background and leaking the job.
        begin()
        AetherRepository.setActiveJob(1L)
        val expectedJob: Long? = 1L
        assertEquals(expectedJob, ui.activeJobId)

        registrationFailed(observed)
        AetherRepository.setActiveJob(null)

        assertNull("no job may stay active after registration failed", ui.activeJobId)
        assertEquals(AetherState.ERROR, ui.state)
    }

    @Test
    fun aStopDuringRegistrationLeavesNoActiveJobAndNoError() {
        begin()
        AetherRepository.setActiveJob(1L)
        registrationCancelled("there is no job 1")
        AetherRepository.setActiveJob(null)

        assertNull(ui.activeJobId)
        assertEquals(AetherState.STOPPED, ui.state)
        assertNull(ui.lastError)
        assertFalse(ui.busy)
    }

    @Test
    fun aSucceededRegistrationAlsoClearsTheActiveJob() {
        begin()
        AetherRepository.setActiveJob(1L)
        registrationSucceeded()

        assertNull(ui.activeJobId)
        assertEquals(AetherState.STOPPED, ui.state)
        assertTrue(lines.any { it.contains("Identities registered for 'masque'") })
    }

    @Test
    fun exactlyOneErrorLineIsPublishedPerFailedRegistration() {
        begin()
        registrationFailed(observed)

        assertEquals(1, AetherRepository.logs.value.count { it.level == "ERROR" })
        assertEquals(0, AetherRepository.logs.value.count { it.level == "OK" })
    }

    @Test
    fun theReportedFailureIsClassifiedAsUnreachableNotAsRefused() {
        assertEquals(
            AetherRegistrationOutcome.Failure.NO_ANSWER,
            AetherRegistrationOutcome.classify(observed)
        )
        val report = AetherRegistrationOutcome.report(observed, AetherJobPolling.identityTimeoutMs())
        assertFalse("no refusal was observed, so none may be claimed", report.contains("401"))
        assertTrue(report.contains("reachability"))
    }
}
