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

    /**
     * Slack added on top of Aether's own scan budget before the app gives up.
     *
     * `overall_deadline` bounds how long the prober keeps *starting* probes; a
     * probe already in flight may still run for its `per_probe_timeout` (up to
     * 15 s, ironclad) and the result then has to cross the FFI. Waiting only as
     * long as the engine would cut off a scan that was about to answer.
     */
    const val SCAN_MARGIN_MS: Long = 20_000L

    /**
     * Aether's own scan budget per mode, in milliseconds: the `overall_deadline`
     * of `prober::ScanMode::strategy` (MASQUE) and `wg_prober::WgScanMode::strategy`
     * (WireGuard) in third_party/Aether/aether/src.
     *
     * | mode     | MASQUE | WireGuard |
     * |----------|-------:|----------:|
     * | turbo    |  45 s  |     30 s  |
     * | balanced | 120 s  |     80 s  |
     * | thorough | 300 s  |    250 s  |
     * | verified |  60 s  |     60 s  |
     * | ironclad | 180 s  |    180 s  |
     *
     * Three of these are longer than the flat [DEFAULT_TIMEOUT_MS] the scan poll
     * used to apply, so `thorough` and `ironclad` could never succeed through the
     * app: the poll expired first and `aether_job_free` then cancelled the scan
     * that was still working, which the user saw as a scan failure.
     */
    private val MASQUE_SCAN_BUDGET_MS = mapOf(
        "turbo" to 45_000L,
        "balanced" to 120_000L,
        "thorough" to 300_000L,
        "verified" to 60_000L,
        "ironclad" to 180_000L
    )

    private val WIREGUARD_SCAN_BUDGET_MS = mapOf(
        "turbo" to 30_000L,
        "balanced" to 80_000L,
        "thorough" to 250_000L,
        "verified" to 60_000L,
        "ironclad" to 180_000L
    )

    /**
     * How long Aether itself will spend scanning [scanMode] over [transport].
     *
     * The fallbacks mirror the engine's: `api::Transport::parse` treats every id
     * other than "wg" as MASQUE, and `ScanMode::parse` / `WgScanMode::parse` treat
     * every unrecognised mode as `balanced` (they also accept aliases such as
     * "fast", "deep", "pro", "stealth" and "guaranteed", which are not offered by
     * this app and therefore not budgeted here).
     */
    fun scanBudgetFor(transport: String, scanMode: String): Long {
        val budgets = if (transport == "wg") WIREGUARD_SCAN_BUDGET_MS else MASQUE_SCAN_BUDGET_MS
        return budgets[scanMode.trim().lowercase()] ?: budgets.getValue("balanced")
    }

    /** The scan poll deadline: Aether's budget plus [SCAN_MARGIN_MS]. */
    fun scanTimeoutFor(transport: String, scanMode: String): Long =
        scanBudgetFor(transport, scanMode) + SCAN_MARGIN_MS

    /**
     * Registration (identity open) budget, taken from the engine's own constants
     * in third_party/Aether/aether/src/account.rs, which drives every WARP API
     * call including registration:
     *
     *  - `API_TIMEOUT = 20 s` (account.rs:248) bounds **one attempt**, and
     *    `https::send` reports `"<host> did not answer within 20s"` when it
     *    elapses (https.rs:66-74);
     *  - `API_ATTEMPTS = 5` (account.rs:250), with `backoff_delay` slept before
     *    attempts 2-5 (account.rs:255-260): 0.45-0.75 s, 0.9-1.5 s, 1.8-3.0 s,
     *    3.6-6.0 s, i.e. 6.75-11.25 s in total;
     *  - a 429/408/5xx answer may carry `Retry-After`, capped at
     *    `API_RETRY_AFTER_CAP_SECS = 30` (account.rs:253) and slept **inside** the
     *    loop, so up to four 30 s sleeps.
     *
     * A registration nobody answers therefore takes 106.75-111.25 s, and a
     * rate-limited one up to about 231 s. The identity poll used the flat
     * [DEFAULT_TIMEOUT_MS] (120 s): 10 s of slack in the first case, and in the
     * second the app gave up first, called `aether_job_free` - which cancels the
     * job it removes (ffi.rs:318-330) - and reported its own timeout instead of
     * the engine's answer.
     */
    const val API_ATTEMPT_TIMEOUT_MS: Long = 20_000L
    const val API_ATTEMPTS: Int = 5
    const val API_BACKOFF_MIN_MS: Long = 6_750L
    const val API_BACKOFF_MAX_MS: Long = 11_250L
    const val API_RETRY_AFTER_MAX_MS: Long = 120_000L

    /** Slack on top of the engine's worst case, for FFI round trips. */
    const val IDENTITY_MARGIN_MS: Long = 15_000L

    /**
     * The window a registration occupies when nothing answers at all: five 20 s
     * attempts plus the four backoff sleeps. An observed failure inside this
     * window is the engine exhausting its own retries, not the app giving up.
     */
    fun noAnswerWindowMs(): LongRange {
        val attempts = API_ATTEMPTS * API_ATTEMPT_TIMEOUT_MS
        return (attempts + API_BACKOFF_MIN_MS)..(attempts + API_BACKOFF_MAX_MS)
    }

    /** The identity poll deadline: the engine's worst case plus [IDENTITY_MARGIN_MS]. */
    fun identityTimeoutMs(): Long =
        API_ATTEMPTS * API_ATTEMPT_TIMEOUT_MS + API_BACKOFF_MAX_MS +
            API_RETRY_AFTER_MAX_MS + IDENTITY_MARGIN_MS
}
