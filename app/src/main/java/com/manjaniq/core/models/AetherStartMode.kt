package com.manjaniq.core.models

import com.manjaniq.R

/**
 * How AetherService starts the core: through the granular FFI calls or through
 * the core's own command line.
 *
 * Aether exposes both entry points (`third_party/Aether/aether/src/ffi.rs`):
 *
 *  * `aether_identity_open` / `aether_scan_start` / `aether_tunnel_start` take
 *    small, typed payloads. They can express exactly the fields those payload
 *    structs carry (`ffi.rs:215-260`), which is why the app used them first: a
 *    scan reports its chosen endpoint, registration failures come back with the
 *    engine's own words, and the service can publish each step.
 *  * `aether_core_start` (`ffi.rs:492`) runs `cli::parse_args` over an argument
 *    list and then `run_with`. Because the CLI is the engine's full surface -
 *    every protocol (`--gool`, `--mim`), the Tor and Psiphon layering
 *    (`--tor*`, `--psiphon*`), Zero Trust, routing, `--upstream`, `--dns`,
 *    `--quick-reconnect`, `--stats`, `--mark`, `--register` - it is the only
 *    path that can honour a complete configuration.
 *
 * Neither is a superset of the other in convenience: the granular path tells the
 * app *where* it is, the CLI path carries *everything*. The user picks which one
 * a route uses, and both are always available - a route is never silently moved
 * from one to the other.
 */
enum class AetherStartMode(
    val id: String,
    /** True when this mode runs the core's command line. */
    val fullEngine: Boolean,
    val labelRes: Int,
    val detailRes: Int
) {
    /** identity_open -> scan_start -> tunnel_start, with per-step reporting. */
    GRANULAR(
        id = "granular",
        fullEngine = false,
        labelRes = R.string.aether_start_mode_granular,
        detailRes = R.string.aether_start_mode_granular_detail
    ),

    /** One `aether_core_start` job over the full CLI: every setting applies. */
    FULL_ENGINE(
        id = "full",
        fullEngine = true,
        labelRes = R.string.aether_start_mode_full,
        detailRes = R.string.aether_start_mode_full_detail
    );

    companion object {
        val ALL: List<AetherStartMode> = values().toList()
        val IDS: List<String> = ALL.map { it.id }

        fun byId(id: String): AetherStartMode? = ALL.firstOrNull { it.id == id }

        /** True only for [FULL_ENGINE]; unknown ids are not the full engine. */
        fun isFullEngine(id: String): Boolean = byId(id)?.fullEngine == true
    }
}
