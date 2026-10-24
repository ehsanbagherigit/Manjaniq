package com.manjaniq.core

/**
 * What an Aether registration (identity open) failure actually means, classified
 * from the message the engine itself returned.
 *
 * The path is app -> [com.manjaniq.AetherBridge.coreStart] (`--register`) ->
 * `aether_identity_open` (ffi.rs:331-368) -> `api::open_identity`
 * (api.rs:319-338), which **loads** the identity file when it exists and only
 * **provisions** one when it does not, so every message below comes from a
 * provisioning run. Provisioning goes through `api_call("registration", "POST", ...)`
 * (account.rs:597), where:
 *
 *  - one attempt is bounded by `API_TIMEOUT = 20 s` (account.rs:248) inside
 *    `https::send`, whose elapsed timeout is `"<authority> did not answer within
 *    20s"` (https.rs:66-74) - DNS, TCP dial, TLS handshake and the HTTP answer all
 *    sit inside that one budget;
 *  - `API_ATTEMPTS = 5` (account.rs:250) with `backoff_delay` before attempts 2-5,
 *    so an unanswered registration ends after 106.75-111.25 s (account.rs:255-260)
 *    with the last attempt's message, prefixed `api: registration: api: `
 *    (error.rs:28-33, account.rs:506);
 *  - a 401/404/410 answer is final (`refuses_identity`, account.rs:270-278, 525-538)
 *    while 429/408/5xx are retried (`worth_retrying`, account.rs:279-288) and may
 *    sleep a `Retry-After` of up to 30 s (account.rs:253, 293-304).
 *
 * This is pure and testable, and it never turns a failure
 * into a success - it only says, in addition to the engine's own words, which
 * layer failed and what the recoveries are. It does not relax any check: the
 * engine keeps its certificate validation, pinning and fingerprint.
 */
object AetherRegistrationOutcome {

    enum class Failure {
        /** No attempt reached the API: name resolution, TCP, TLS or HTTP stalled. */
        NO_ANSWER,

        /** The API answered and refused: 403 (flagged network), 429 (rate limit), 5xx. */
        REFUSED_BY_API,

        /** The API answered 401/404/410, which the engine treats as final. */
        IDENTITY_REFUSED,

        /** The API answered but the registration data was unusable. */
        BAD_RESPONSE,

        /** The TLS handshake did not complete. */
        TLS,

        /** The ECH key lookup failed. */
        ECH,

        /** The identity file could not be read or written. */
        STORAGE,

        /** The job was cancelled, which happens when the app frees a job. */
        CANCELLED,

        /**
         * The app's own poll deadline expired while the engine was still working:
         * the raw poll is `null`, so there is no engine message to show.
         */
        TIMEOUT,

        OTHER
    }

    /** Classifies the message of a failed registration job poll. */
    fun classify(raw: String?): Failure {
        val text = raw?.trim().orEmpty()
        if (raw == null || text.isEmpty()) return Failure.TIMEOUT
        val lower = text.lowercase()
        return when {
            "did not answer within" in lower -> Failure.NO_ANSWER
            "identity refused" in lower -> Failure.IDENTITY_REFUSED
            "status 403" in lower ||
                "status 429" in lower ||
                "too many registrations" in lower ||
                "refused this network" in lower -> Failure.REFUSED_BY_API
            "no peers in registration response" in lower ||
                "registration returned empty token" in lower ||
                "decode peer pubkey" in lower ||
                "invalid peer pubkey length" in lower ||
                "decode:" in lower -> Failure.BAD_RESPONSE
            lower.startsWith("io:") || " io:" in lower -> Failure.STORAGE
            lower.startsWith("tls:") || " tls:" in lower -> Failure.TLS
            lower.startsWith("ech:") || " ech:" in lower -> Failure.ECH
            // `aether_job_poll` answers this once the job is out of its map, which only
            // happens when the app freed it - a stop during registration, or the cleanup
            // after one (ffi.rs:291-330).
            "there is no job" in lower -> Failure.CANCELLED
            "cancelled" in lower || "canceled" in lower -> Failure.CANCELLED
            "status 4" in lower || "status 5" in lower -> Failure.REFUSED_BY_API
            else -> Failure.OTHER
        }
    }

    /**
     * The line the log shows for a failed registration: the engine's message
     * verbatim, then what it means and what to do. [timeoutMs] is the app's poll
     * deadline, used only for the case where the engine never answered it.
     */
    fun report(raw: String?, timeoutMs: Long): String {
        val failure = classify(raw)
        val attempts = AetherJobPolling.API_ATTEMPTS
        val perAttempt = AetherJobPolling.API_ATTEMPT_TIMEOUT_MS / 1000
        val window = AetherJobPolling.noAnswerWindowMs()
        val engine = raw?.trim().orEmpty()

        return when (failure) {
            Failure.NO_ANSWER ->
                "Registration failed: $engine - Aether made $attempts attempts of " +
                    "${perAttempt}s each (about ${window.first / 1000}-${window.last / 1000}s " +
                    "in all, including its own backoff) and no attempt reached the WARP API: " +
                    "name resolution, the TCP connection, the TLS handshake or the HTTP answer " +
                    "stalled before any of them completed. No identity was registered and no " +
                    "tunnel was started. This is a reachability result for the API from this " +
                    "device's network - check that the device is online, that DNS resolves, and " +
                    "that no captive portal, VPN or firewall blocks port 443, then try again; " +
                    "another network is the quickest test. Certificate validation and the " +
                    "engine's other checks were not relaxed to produce this."

            Failure.IDENTITY_REFUSED ->
                "Registration failed: $engine - the WARP API answered 401, 404 or 410, which " +
                    "Aether treats as final rather than retryable, so it stopped immediately. " +
                    "No identity was registered and no tunnel was started. Try again later or on " +
                    "another network, or import an identity you already have."

            Failure.REFUSED_BY_API ->
                "Registration failed: $engine - the WARP API answered and refused the request; " +
                    "Aether's own hint is in that message (403 means this network's address looks " +
                    "flagged, 429 means too many registrations from it). No identity was " +
                    "registered and no tunnel was started. The recoveries the engine itself names " +
                    "are waiting a few minutes, switching network, or importing an existing " +
                    "identity."

            Failure.BAD_RESPONSE ->
                "Registration failed: $engine - the API answered but the registration data was " +
                    "unusable, so Aether rejected it instead of saving a broken identity. No " +
                    "identity was registered and no tunnel was started. Try again; if it repeats, " +
                    "the answer this network gets from the API is being altered."

            Failure.TLS ->
                "Registration failed: $engine - the TLS handshake to the API did not complete. No " +
                    "identity was registered and no tunnel was started. A captive portal or a " +
                    "filtering middlebox is the usual cause; certificate validation stayed on."

            Failure.ECH ->
                "Registration failed: $engine - the ECH key could not be obtained. No identity was " +
                    "registered and no tunnel was started."

            Failure.STORAGE ->
                "Registration failed: $engine - the identity file could not be read or written " +
                    "at the path this profile is configured with, so Aether could not keep what " +
                    "it registered. No identity was registered and no tunnel was started. Check " +
                    "the app's free storage and that the identity path in the Aether settings " +
                    "points somewhere the app may write."

            Failure.CANCELLED ->
                "Registration cancelled: ${engine.ifEmpty { "the job was freed before an identity was ready" }} " +
                    "- this is a cancellation, not an API refusal, and no tunnel was started."

            Failure.TIMEOUT ->
                "Registration timed out: Aether had not finished registering after " +
                    "${timeoutMs / 1000}s, so the app ended the job (the engine allows " +
                    "$attempts attempts of ${perAttempt}s plus backoff and any Retry-After wait). " +
                    "No identity was registered and no tunnel was started. The engine's own answer " +
                    "was not available to report."

            Failure.OTHER ->
                "Registration failed: ${engine.ifEmpty { "unknown error" }} - no identity was " +
                    "registered and no tunnel was started."
        }
    }
}
