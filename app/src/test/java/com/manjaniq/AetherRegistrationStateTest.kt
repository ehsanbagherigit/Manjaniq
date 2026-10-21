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
 * What the Aether screen is told while an identity is being registered.
 *
 * `AetherService` cannot run on the host JVM - it is a foreground service over the
 * real FFI and real sockets - so these tests drive the repository exactly as the
 * service does. [begin], [registrationFailed], [registrationCancelled] and
 * [registrationSucceeded] mirror the identity block of `startAether()` step for
 * step: `requestStart` publishes busy+STARTING and clears the previous error, a
 * failed `pollJob` goes through `failed()` (which reports a stop the user asked for
 * as STOPPED with no error and anything else as ERROR), and a finished registration
 * publishes the identity id the engine returned.
 *
 * What is pinned here is the contract the panel renders after the reported failure
 * (`api: registration: api: api.cloudflareclient.com did not answer within 20s`):
 * a registration that never answered must leave Aether in ERROR with the engine's
 * own wording on screen, must publish neither an identity nor an endpoint, must
 * stay recoverable for a retry, and must never look like it connected.
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
        // not object state, and JUnit's method order is not the source order, so an
        // identity or endpoint an earlier test published would still be sitting here.
        AetherRepository.setIdentity(null)
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

    /** `failed(report)` in `AetherService` when the identity poll came back broken. */
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

    /** The identity block of `startAether()` after a registration succeeded. */
    private fun registrationSucceeded(id: Long) {
        AetherRepository.log(
            "OK",
            "Identity ready id=$id at /data/user/0/com.manjaniq/files/aether_identity-masque " +
                "(lastconn /data/user/0/com.manjaniq/files/aether_identity-masque-lastconn, " +
                "model Pixel 6)"
        )
        AetherRepository.setIdentity(id)
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
    fun aFailedRegistrationPublishesNeitherAnIdentityNorAnEndpoint() {
        begin()
        registrationFailed(observed)

        assertNull("no identity may be published when registration failed", ui.identityId)
        assertEquals("", ui.endpoint)
        assertFalse("nothing may claim Aether is running", lines.any { it.contains("Identity ready") })
        assertFalse(lines.any { it.contains("tunnel is running") })
    }

    @Test
    fun anAppSideRegistrationTimeoutIsReportedAsAnErrorNotAsASuccess() {
        begin()
        registrationFailed(null)

        assertEquals(AetherState.ERROR, ui.state)
        assertFalse(ui.busy)
        assertTrue(ui.lastError!!.contains("Registration timed out"))
        assertNull(ui.identityId)
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
        assertNull(ui.identityId)
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

        registrationSucceeded(42L)
        AetherRepository.onServiceActionFinished(null, AetherState.STARTING)

        val expectedIdentity: Long? = 42L
        assertEquals(expectedIdentity, ui.identityId)
        assertNull(ui.lastError)
        assertTrue(lines.any { it.contains("Identity ready id=42") })
        // the earlier failure stays in the log: history is not rewritten by a retry
        assertTrue("the failed attempt is still in the log", lines.any { it.contains(observed) })
    }

    @Test
    fun theIdentityJobIsTheActiveJobWhileItIsPolledAndIsClearedWhenItEnds() {
        // The identity block publishes the job it polls, exactly as the scan and tunnel
        // blocks do, so a Stop finds a job to cancel instead of leaving the engine
        // running its attempts in the background and leaking the job.
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
    fun aSucceededRegistrationAlsoClearsTheActiveJobBeforeTheScanStarts() {
        begin()
        AetherRepository.setActiveJob(1L)
        registrationSucceeded(7L)
        AetherRepository.setActiveJob(null)

        assertNull(ui.activeJobId)
        val expectedIdentity: Long? = 7L
        assertEquals(expectedIdentity, ui.identityId)
        assertTrue(lines.any { it.contains("aether_identity-masque") })
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
