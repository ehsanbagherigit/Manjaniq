package com.manjaniq

import com.manjaniq.core.AetherJobPolling
import com.manjaniq.core.AetherRegistrationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registration (identity open) failure reporting.
 *
 * The reported run failed with
 * `api: registration: api: api.cloudflareclient.com did not answer within 20s`
 * 110 s after it started. That message is the engine's own: one attempt is bounded
 * by `API_TIMEOUT = 20 s` (account.rs:248) inside `https::send`, which words the
 * elapsed timeout exactly like that (https.rs:66-74), and `API_ATTEMPTS = 5` with
 * `backoff_delay` (account.rs:250-260) put an unanswered registration at
 * 106.75-111.25 s. These tests pin that reading and require the app to keep the
 * engine's wording, to name the layer that failed, and never to dress a timeout up
 * as a success.
 */
class AetherRegistrationOutcomeTest {

    /** The line from the reported failure, verbatim. */
    private val observed =
        "api: registration: api: api.cloudflareclient.com did not answer within 20s"

    private fun reportOf(raw: String?) =
        AetherRegistrationOutcome.report(raw, AetherJobPolling.identityTimeoutMs())

    // ---- classification ----

    @Test
    fun anUnansweredRegistrationIsRecognisedFromTheEnginesOwnText() {
        assertEquals(
            AetherRegistrationOutcome.Failure.NO_ANSWER,
            AetherRegistrationOutcome.classify(observed)
        )
    }

    @Test
    fun aRateLimitedRegistrationIsRecognised() {
        val raw = "api: registration: api: status 429 Too Many Requests: rate limited " +
            "(too many registrations from this address; wait a few minutes before trying again)"
        assertEquals(
            AetherRegistrationOutcome.Failure.REFUSED_BY_API,
            AetherRegistrationOutcome.classify(raw)
        )
    }

    @Test
    fun aFlaggedNetworkIsRecognised() {
        val raw = "api: registration: api: status 403 Forbidden: no details returned " +
            "(cloudflare refused this network; the address looks flagged, try again later, " +
            "switch network, or import an existing identity)"
        assertEquals(
            AetherRegistrationOutcome.Failure.REFUSED_BY_API,
            AetherRegistrationOutcome.classify(raw)
        )
    }

    @Test
    fun aRejectedIdentityIsRecognised() {
        assertEquals(
            AetherRegistrationOutcome.Failure.IDENTITY_REFUSED,
            AetherRegistrationOutcome.classify(
                "identity refused: registration: status 404 Not Found: no such device"
            )
        )
    }

    @Test
    fun unusableRegistrationDataIsRecognised() {
        assertEquals(
            AetherRegistrationOutcome.Failure.BAD_RESPONSE,
            AetherRegistrationOutcome.classify("api: registration: api: no peers in registration response")
        )
        assertEquals(
            AetherRegistrationOutcome.Failure.BAD_RESPONSE,
            AetherRegistrationOutcome.classify("api: registration: api: registration returned empty token")
        )
    }

    @Test
    fun handshakeAndKeyFailuresAreRecognised() {
        assertEquals(
            AetherRegistrationOutcome.Failure.TLS,
            AetherRegistrationOutcome.classify("tls: the handshake did not complete")
        )
        assertEquals(
            AetherRegistrationOutcome.Failure.ECH,
            AetherRegistrationOutcome.classify("ech: no key could be fetched")
        )
    }

    @Test
    fun anIdentityFileThatCannotBeWrittenIsRecognised() {
        // open_identity saves what it registered (api.rs:330, 336), so a full disk or
        // an unwritable path surfaces as an io error, not as an API answer.
        assertEquals(
            AetherRegistrationOutcome.Failure.STORAGE,
            AetherRegistrationOutcome.classify("io: No space left on device (os error 28)")
        )
        val report = reportOf("io: Permission denied (os error 13)")
        assertTrue(report.contains("Permission denied"))
        assertTrue(report.contains("identity file"))
        assertTrue(report.contains("no tunnel was started"))
    }

    @Test
    fun aFreedJobIsReportedAsCancellationNotAsRefusal() {
        assertEquals(
            AetherRegistrationOutcome.Failure.CANCELLED,
            AetherRegistrationOutcome.classify("api: registration: cancelled")
        )
        assertTrue(reportOf("api: registration: cancelled").contains("Registration cancelled"))
    }

    @Test
    fun aJobTheAppHasFreedIsReportedAsCancelledToo() {
        // `aether_job_poll` answers this once the job is out of its map, which only
        // happens when the app freed it - a Stop during registration (ffi.rs:291-330).
        assertEquals(
            AetherRegistrationOutcome.Failure.CANCELLED,
            AetherRegistrationOutcome.classify("there is no job 1")
        )
        val report = reportOf("there is no job 1")
        assertTrue(report.contains("Registration cancelled"))
        assertTrue(report.contains("no tunnel was started"))
    }

    @Test
    fun aMissingEngineAnswerIsTheAppsOwnDeadline() {
        assertEquals(AetherRegistrationOutcome.Failure.TIMEOUT, AetherRegistrationOutcome.classify(null))
        assertEquals(AetherRegistrationOutcome.Failure.TIMEOUT, AetherRegistrationOutcome.classify("   "))
    }

    // ---- error reporting ----

    @Test
    fun theEnginesWordingSurvivesVerbatimInTheReport() {
        assertTrue(reportOf(observed).contains(observed))
    }

    @Test
    fun anUnansweredRegistrationSaysWhatWasTriedAndThatNothingStarted() {
        val report = reportOf(observed)
        assertTrue("names the attempt count", report.contains("${AetherJobPolling.API_ATTEMPTS} attempts"))
        assertTrue(
            "names the per-attempt budget",
            report.contains("${AetherJobPolling.API_ATTEMPT_TIMEOUT_MS / 1000}s")
        )
        assertTrue(report.contains("No identity was registered"))
        assertTrue(report.contains("no tunnel was started"))
    }

    @Test
    fun aRegistrationTimeoutIsNeverReportedAsASuccess() {
        val report = reportOf(null)
        assertTrue(report.contains("Registration timed out"))
        assertTrue("names the deadline that expired", report.contains(
            "${AetherJobPolling.identityTimeoutMs() / 1000}s"
        ))
        assertFalse(report.contains("Identity ready"))
        assertFalse(report.lowercase().contains("tunnel is running"))
        assertTrue(report.contains("was not available to report"))
    }

    @Test
    fun noFailureReportClaimsTheTunnelCameUp() {
        val failures = listOf(
            observed,
            null,
            "api: registration: api: status 429 Too Many Requests: slow down",
            "identity refused: registration: status 401 Unauthorized: bad token",
            "api: registration: api: no peers in registration response",
            "tls: handshake failed",
            "api: registration: cancelled",
            "there is no job 1",
            "io: No space left on device (os error 28)",
            "api: registration: api: an error this app has not seen before"
        )
        for (raw in failures) {
            val report = reportOf(raw)
            assertTrue("every report says no tunnel started: $report", report.contains("no tunnel was started"))
            assertFalse("no report claims success: $report", report.contains("Identity ready"))
            assertFalse("no report hides the failure: $report", report.isBlank())
        }
    }

    @Test
    fun recoveryHintsComeFromWhatTheEngineItselfSays() {
        assertTrue(
            reportOf(observed).contains("port 443")
        )
        assertTrue(
            "429 keeps the engine's own wait hint",
            reportOf("api: registration: api: status 429 Too Many Requests: slow down")
                .contains("waiting a few minutes")
        )
        assertTrue(
            "401/404/410 names importing an identity",
            reportOf("identity refused: registration: status 410 Gone: deleted")
                .contains("import an identity")
        )
    }

    @Test
    fun certificateValidationIsNeverSuggestedAsAWayOut() {
        for (raw in listOf(observed, null, "tls: handshake failed")) {
            val report = reportOf(raw).lowercase()
            assertFalse(report.contains("disable certificate"))
            assertFalse(report.contains("ignore certificate"))
            assertFalse(report.contains("insecure"))
        }
    }

    // ---- the app's deadline against the engine's schedule ----

    @Test
    fun theObservedFailureLiesInsideTheEnginesOwnRetryWindow() {
        // 02:59:33 start -> 03:01:23 failure = 110 s, which is five 20 s attempts plus
        // the engine's backoff, so the engine ran out of attempts rather than the app.
        val observedElapsedMs = 110_000L
        assertTrue(
            "110 s is inside ${AetherJobPolling.noAnswerWindowMs()}",
            AetherJobPolling.noAnswerWindowMs().contains(observedElapsedMs)
        )
        assertTrue(
            "the app's old 120 s deadline had not expired yet",
            observedElapsedMs < AetherJobPolling.DEFAULT_TIMEOUT_MS
        )
    }

    @Test
    fun theIdentityDeadlineOutlastsEveryScheduleTheEngineCanRun() {
        val attempts = AetherJobPolling.API_ATTEMPTS * AetherJobPolling.API_ATTEMPT_TIMEOUT_MS
        val unansweredWorst = attempts + AetherJobPolling.API_BACKOFF_MAX_MS
        val rateLimitedWorst = attempts + AetherJobPolling.API_RETRY_AFTER_MAX_MS

        assertTrue(AetherJobPolling.identityTimeoutMs() > unansweredWorst)
        assertTrue(AetherJobPolling.identityTimeoutMs() > rateLimitedWorst)
        assertTrue(
            "the flat default was too short for a rate-limited registration",
            rateLimitedWorst > AetherJobPolling.DEFAULT_TIMEOUT_MS
        )
    }
}
