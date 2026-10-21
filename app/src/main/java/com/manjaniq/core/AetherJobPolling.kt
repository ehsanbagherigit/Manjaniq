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
}
