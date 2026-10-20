package com.manjaniq.core

/**
 * Deadline arithmetic for polling an Aether FFI job (`aether_job_poll`).
 *
 * Aether jobs are polled until they report `state == "done"`. A tunnel job only
 * reaches `done` when the tunnel *exits*, so the tunnel poll must be unbounded.
 *
 * Callers used to express "unbounded" as `timeoutMs = Long.MAX_VALUE` and the
 * deadline was computed as `System.currentTimeMillis() + timeoutMs`. That sum
 * overflows `Long` and wraps to a large *negative* number, so the poll loop
 * condition (`now < deadline`) was false on the very first check: the poll
 * returned "timed out" immediately and the caller freed the job it had just
 * started — and `aether_job_free` cancels the job it removes
 * (third_party/Aether/aether/src/ffi.rs, `aether_job_free`).
 *
 * This object makes the unbounded case explicit and overflow-proof so the
 * arithmetic can be unit-tested on the host JVM without an Android Service.
 */
object AetherJobPolling {

    /** Default bound for short jobs (identity open, scan). */
    const val DEFAULT_TIMEOUT_MS: Long = 120_000L

    /** Delay between two `aether_job_poll` calls. */
    const val POLL_INTERVAL_MS: Long = 250L

    /**
     * The wall-clock instant at which polling must give up, or `null` when
     * polling is unbounded.
     *
     * `null` is returned for a `null` timeout (explicitly unbounded) and for a
     * timeout so large that `nowMs + timeoutMs` would overflow — the case that
     * used to produce an already-expired, negative deadline.
     *
     * A timeout of `0` keeps its original meaning: the deadline is `nowMs`, so
     * the loop body never runs and the poll reports a timeout at once.
     */
    fun deadlineFor(nowMs: Long, timeoutMs: Long?): Long? {
        if (timeoutMs == null) return null
        return try {
            Math.addExact(nowMs, timeoutMs)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Whether another poll attempt is allowed at [nowMs]. */
    fun shouldKeepPolling(nowMs: Long, deadlineMs: Long?): Boolean =
        deadlineMs == null || nowMs < deadlineMs
}
