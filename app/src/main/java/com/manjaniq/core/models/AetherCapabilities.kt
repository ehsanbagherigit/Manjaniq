package com.manjaniq.core.models

import com.manjaniq.R

/**
 * How far an Aether setting actually reaches inside this build.
 *
 * Manjaniq drives Aether v2.3.0 through three FFI calls
 * (`third_party/Aether/aether/src/ffi.rs`):
 *
 *  - `aether_identity_open`  → path, transport, model, locale, team
 *  - `aether_scan_start`     → transport, mode, ip, profile, ports, excluded, ech
 *  - `aether_tunnel_start`   → peer, transport, socks, http, profile, keepalive, ech
 *
 * A setting that is not one of those payload fields only takes effect through
 * `aether_core_start` (the full CLI, which sets `AETHER_*` environment
 * variables). `AetherBridge.coreStart` exists for that path but nothing in the
 * app calls it, and Android offers no public API for setting the process
 * environment from Kotlin — so those settings are recorded and shown, but they
 * are not applied. The UI says so instead of pretending otherwise.
 */
enum class AetherReach {
    /** Sent in an FFI payload the service actually issues; Aether applies it. */
    Applied,

    /** Sent, but Aether only honours it on part of the path (see the reason text). */
    Partial,

    /** Captured and persisted, but no field for it exists on the FFI path in use. */
    NotForwarded
}

/** One row of the reachability matrix. */
data class AetherSettingInfo(
    val key: String,
    val reach: AetherReach,
    /** Why the setting is or is not applied, quoted from the pinned Aether source. */
    val reasonRes: Int
)

/**
 * Single source of truth for the Aether capability inventory.
 *
 * Every value below is traceable to the vendored Aether v2.3.0 source or to
 * `AetherService`/`AetherBridge`; nothing is inferred from a feature name.
 */
object AetherCapabilities {

    private val INFO = listOf(
        // ---- Applied: these are fields of the payloads AetherService sends ----
        AetherSettingInfo("transport", AetherReach.Applied, R.string.aether_reach_applied_transport),
        AetherSettingInfo("scanMode", AetherReach.Applied, R.string.aether_reach_applied_scan),
        AetherSettingInfo("ipVersion", AetherReach.Applied, R.string.aether_reach_applied_ip),
        AetherSettingInfo("socksBind", AetherReach.Applied, R.string.aether_reach_applied_socks),
        AetherSettingInfo("httpProxyBind", AetherReach.Applied, R.string.aether_reach_applied_http),
        AetherSettingInfo("peer", AetherReach.Applied, R.string.aether_reach_applied_peer),
        AetherSettingInfo("identityPath", AetherReach.Applied, R.string.aether_reach_applied_identity),

        // ---- Partial: forwarded, but only honoured on part of the path ----
        AetherSettingInfo("echEnabled", AetherReach.Partial, R.string.aether_reach_partial_ech),
        AetherSettingInfo("noizeProfile", AetherReach.Partial, R.string.aether_reach_partial_noize),
        AetherSettingInfo("teamName", AetherReach.Partial, R.string.aether_reach_partial_team),
        // Fragmenting reaches Aether through the bridge's environment write
        // (`AetherService.applyFragmenting` → `AetherBridge.fragmentingApply`), which
        // is where Aether reads it (`fragment.rs:32-61`) - not through a payload field.
        // Partial rather than Applied because `fragment.rs` has exactly one consumer,
        // `masque_h2.rs`: it applies on the MASQUE-over-HTTP/2 carrier only, and the
        // SNI split has no CLI flag upstream at all.
        AetherSettingInfo("fragmentEnabled", AetherReach.Partial, R.string.aether_reach_partial_fragment),
        AetherSettingInfo("fragmentSize", AetherReach.Partial, R.string.aether_reach_partial_fragment),
        AetherSettingInfo("fragmentDelay", AetherReach.Partial, R.string.aether_reach_partial_fragment),
        AetherSettingInfo("fragmentSniSplit", AetherReach.Partial, R.string.aether_reach_partial_fragment_sni),

        // ---- Not forwarded: no field on identity_open / scan_start / tunnel_start ----
        AetherSettingInfo("dns", AetherReach.NotForwarded, R.string.aether_reach_missing_env_only),
        AetherSettingInfo("upstream", AetherReach.NotForwarded, R.string.aether_reach_missing_env_only),
        AetherSettingInfo("quickReconnect", AetherReach.NotForwarded, R.string.aether_reach_missing_cli_flow),
        AetherSettingInfo("psiphonEnabled", AetherReach.NotForwarded, R.string.aether_reach_missing_external),
        AetherSettingInfo("psiphonEndpoint", AetherReach.NotForwarded, R.string.aether_reach_missing_external),
        AetherSettingInfo("psiphonRegion", AetherReach.NotForwarded, R.string.aether_reach_missing_external),
        AetherSettingInfo("torSocks", AetherReach.NotForwarded, R.string.aether_reach_missing_external),
        AetherSettingInfo("torBridges", AetherReach.NotForwarded, R.string.aether_reach_missing_external),
        AetherSettingInfo("torUseBridges", AetherReach.NotForwarded, R.string.aether_reach_missing_external)
    )

    private val BY_KEY: Map<String, AetherSettingInfo> = INFO.associateBy { it.key }

    val ALL: List<AetherSettingInfo> = INFO

    fun of(key: String): AetherSettingInfo? = BY_KEY[key]

    /** What the **granular** path does with [key]; kept for callers that predate modes. */
    fun reachOf(key: String): AetherReach = BY_KEY[key]?.reach ?: AetherReach.NotForwarded

    fun reasonResFor(key: String): Int =
        BY_KEY[key]?.reasonRes ?: R.string.aether_reach_missing_env_only

    /** True when [key] is applied (fully or partly) by the granular path. */
    fun isForwarded(key: String): Boolean = reachOf(key) != AetherReach.NotForwarded

    /**
     * What the start mode [mode] does with [key].
     *
     * The table in [INFO] describes the granular FFI path, because that is the
     * payload surface it was written against. `aether_core_start` runs the whole
     * CLI, so every setting it accepts is applied - the only exceptions are the
     * ones that need a child process this build may not ship (Psiphon, a Tor
     * pluggable transport) and the ClientHello SNI split, which upstream has no
     * flag for and which the app writes through
     * `AetherBridge.fragmentingApply` either way.
     */
    fun reachOf(key: String, mode: AetherStartMode): AetherReach {
        if (!mode.fullEngine) return reachOf(key)
        return when (key) {
            // Needs an external executable; present only when the build shipped it.
            "psiphonEnabled", "psiphonEndpoint", "psiphonRegion",
            "torSocks", "torBridges", "torUseBridges" -> AetherReach.Partial
            // Written into the environment by the JNI bridge, not by a CLI flag.
            "fragmentSniSplit" -> AetherReach.Partial
            else -> AetherReach.Applied
        }
    }

    /** The reason text for [key] under [mode]. */
    fun reasonResFor(key: String, mode: AetherStartMode): Int {
        if (!mode.fullEngine) return reasonResFor(key)
        return when (key) {
            "psiphonEnabled", "psiphonEndpoint", "psiphonRegion" ->
                R.string.aether_reach_full_helper_psiphon
            "torSocks", "torBridges", "torUseBridges" -> R.string.aether_reach_full_helper_tor
            "fragmentSniSplit" -> R.string.aether_reach_partial_fragment_sni
            else -> R.string.aether_reach_full_cli
        }
    }

    /** True when [key] reaches Aether at all under [mode]. */
    fun isForwarded(key: String, mode: AetherStartMode): Boolean =
        reachOf(key, mode) != AetherReach.NotForwarded
}

/**
 * The four protocol names `AetherConfig.transport` accepts.
 *
 * `api::Transport::parse` (third_party/Aether/aether/src/api.rs) maps only
 * `wg|wireguard|warp` to WireGuard and *everything else* to Masque, so `gool`
 * and `mim` are accepted by the app's validation yet silently run as plain
 * MASQUE on the `identity_open` / `scan_start` / `tunnel_start` path. They are
 * real Aether protocols — reachable through `aether_core_start` (`--gool`,
 * `--mim`) — so they stay listed, marked as not selectable here, instead of
 * being removed or being allowed to degrade without saying so.
 */
enum class AetherTransportChoice(
    val id: String,
    val labelRes: Int,
    val detailRes: Int,
    /** False when the embedded FFI path cannot request this protocol. */
    val selectable: Boolean
) {
    MASQUE("masque", R.string.aether_transport_masque, R.string.aether_transport_masque_detail, true),
    WIREGUARD("wg", R.string.aether_transport_wg, R.string.aether_transport_wg_detail, true),
    GOOL("gool", R.string.aether_transport_gool, R.string.aether_transport_gool_detail, false),
    MIM("mim", R.string.aether_transport_mim, R.string.aether_transport_mim_detail, false);

    companion object {
        val ALL: List<AetherTransportChoice> = values().toList()
        val IDS: List<String> = ALL.map { it.id }
        val SELECTABLE_IDS: List<String> = ALL.filter { it.selectable }.map { it.id }

        fun byId(id: String): AetherTransportChoice? = ALL.firstOrNull { it.id == id }

        /**
         * Whether the granular path can dispatch [id]: only `masque` and `wg`,
         * because `api::Transport::parse` (api.rs:20-26) maps every other string
         * to Masque.
         */
        fun isSelectable(id: String): Boolean = byId(id)?.selectable == true

        /**
         * Whether [mode] can start [id]. The full-engine path runs `cli.rs`, where
         * `--gool` (`AETHER_PROTOCOL=gool`) and `--mim` are real protocols, so all
         * four are selectable there; on the granular path the table's own
         * [AetherTransportChoice.selectable] decides.
         */
        fun isSelectable(id: String, fullEngine: Boolean): Boolean {
            val choice = byId(id) ?: return false
            return choice.selectable || fullEngine
        }
    }
}

/**
 * Scan modes, exactly the set `prober::ScanMode::parse` accepts
 * (turbo | balanced | thorough | verified | ironclad).
 */
enum class AetherScanChoice(val id: String, val labelRes: Int, val detailRes: Int) {
    TURBO("turbo", R.string.aether_scan_turbo, R.string.aether_scan_turbo_detail),
    BALANCED("balanced", R.string.aether_scan_balanced, R.string.aether_scan_balanced_detail),
    THOROUGH("thorough", R.string.aether_scan_thorough, R.string.aether_scan_thorough_detail),
    VERIFIED("verified", R.string.aether_scan_verified, R.string.aether_scan_verified_detail),
    IRONCLAD("ironclad", R.string.aether_scan_ironclad, R.string.aether_scan_ironclad_detail);

    companion object {
        val ALL: List<AetherScanChoice> = values().toList()
        val IDS: List<String> = ALL.map { it.id }
    }
}

/**
 * IP versions, the values `prober::IpScan::parse` accepts (v4 | v6 | both).
 */
enum class AetherIpChoice(val id: String, val labelRes: Int) {
    V4("v4", R.string.aether_ip_v4),
    V6("v6", R.string.aether_ip_v6),
    BOTH("both", R.string.aether_ip_both);

    companion object {
        val ALL: List<AetherIpChoice> = values().toList()
        val IDS: List<String> = ALL.map { it.id }
    }
}

/**
 * Obfuscation profiles, the set both `noize::from_profile` and
 * `aethernoize::from_profile` accept (off | light | firewall | balanced | gfw |
 * aggressive). `light` is supported upstream and by the CLI help text but was
 * missing from the app's chip list.
 */
enum class AetherNoizeChoice(val id: String, val labelRes: Int, val detailRes: Int) {
    OFF("off", R.string.aether_noize_off, R.string.aether_noize_off_detail),
    LIGHT("light", R.string.aether_noize_light, R.string.aether_noize_light_detail),
    FIREWALL("firewall", R.string.aether_noize_firewall, R.string.aether_noize_firewall_detail),
    BALANCED("balanced", R.string.aether_noize_balanced, R.string.aether_noize_balanced_detail),
    GFW("gfw", R.string.aether_noize_gfw, R.string.aether_noize_gfw_detail),
    AGGRESSIVE("aggressive", R.string.aether_noize_aggressive, R.string.aether_noize_aggressive_detail);

    companion object {
        val ALL: List<AetherNoizeChoice> = values().toList()
        val IDS: List<String> = ALL.map { it.id }
    }
}

/**
 * TLS ClientHello fragmenting.
 *
 * Implemented upstream in `third_party/Aether/aether/src/fragment.rs`
 * (`FragmentConfig`, `FragmentingStream`, `sni_host_range`, with unit tests) and
 * applied in `masque_h2.rs`, where `connect_tls` wraps the carrier's TCP stream
 * in a `FragmentingStream`. It therefore only affects the MASQUE-over-HTTP/2
 * carrier, never HTTP/3 (QUIC) or WireGuard.
 *
 * Configuration is environment-only upstream: `AETHER_MASQUE_H2_FRAGMENT`,
 * `AETHER_MASQUE_H2_FRAGMENT_SIZE`, `AETHER_MASQUE_H2_FRAGMENT_DELAY`,
 * `AETHER_MASQUE_H2_FRAGMENT_SNI`, which the CLI flags `--fragment`,
 * `--no-fragment`, `--fragment-size` and `--fragment-delay` write
 * (`cli.rs:602-605`). The SNI split has no CLI flag at all.
 *
 * This app writes the same variables through `AetherBridge.fragmentingApply`, whose
 * JNI side (`aether-wrapper/src/lib.rs`) accepts a fixed payload and touches those
 * five variables and the carrier's `AETHER_MASQUE_HTTP2` (`cli.rs:579`) and nothing
 * else - an Android process cannot set the environment of a library it loaded from
 * Kotlin. Because `fragment.rs` is consumed only by `masque_h2.rs`, enabling
 * fragmenting necessarily selects that carrier, which is why
 * `AetherConfig.usesHttp2Carrier()` pairs the two and `validate()` refuses
 * fragmenting on any other transport.
 *
 * Defaults recorded here are the documented CLI defaults (size `16-32`, delay
 * `2-10`, off unless asked for — `Docs/GUIDE.en.md` §"AETHER_MASQUE_H2_FRAGMENT …
 * Off by default"). Note that upstream disagrees with itself: `fragment.rs:37-44`
 * falls back to `8-16` for a blank size and to off for a blank
 * `AETHER_MASQUE_H2_FRAGMENT`, while `cli.rs:133` and the guide say `16-32` and
 * "default on". The app sends its values explicitly, so the engine's fallback is
 * only reached for a field left blank.
 */
object AetherFragmenting {

    const val DEFAULT_SIZE = "16-32"
    const val DEFAULT_DELAY = "2-10"
    const val DEFAULT_ENABLED = false
    const val DEFAULT_SNI_SPLIT = false

    /** Largest chunk size the UI accepts; upstream clamps `size_min` to >= 1. */
    const val MAX_SIZE_BYTES = 1500

    /** Longest inter-fragment delay the UI accepts, in milliseconds. */
    const val MAX_DELAY_MS = 5_000

    /**
     * Environment variables Aether reads for fragmenting, written on this app's behalf
     * by `AetherBridge.fragmentingApply` plus the carrier variable they need.
     */
    val ENV_VARS: List<String> = listOf(
        "AETHER_MASQUE_HTTP2",
        "AETHER_MASQUE_H2_FRAGMENT",
        "AETHER_MASQUE_H2_FRAGMENT_SIZE",
        "AETHER_MASQUE_H2_FRAGMENT_DELAY",
        "AETHER_MASQUE_H2_FRAGMENT_SNI"
    )

    /**
     * Parses an Aether range spec: `n` or `a-b`.
     *
     * Mirrors `fragment::parse_range`: a single number means the same value on
     * both ends, and a reversed range is swapped rather than rejected. Returns
     * null when the spec is not a usable range, so the caller can show a
     * validation error instead of letting Aether fall back silently.
     */
    fun parseRange(spec: String): Pair<Long, Long>? {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split("-")
        return when (parts.size) {
            1 -> {
                val v = parts[0].trim().toLongOrNull() ?: return null
                if (v < 0) return null
                v to v
            }
            2 -> {
                val a = parts[0].trim().toLongOrNull() ?: return null
                val b = parts[1].trim().toLongOrNull() ?: return null
                if (a < 0 || b < 0) return null
                if (b < a) b to a else a to b
            }
            else -> null
        }
    }

    /**
     * Validates a fragmenting range for the UI. Returns an error message, or
     * null when [spec] is acceptable. [max] is the largest value allowed.
     *
     * A blank spec is acceptable: it means "leave Aether's own default in
     * place", and `toAetherArgs` then emits no `--fragment-size`/`--fragment-delay`.
     */
    fun validateRange(spec: String, max: Long): String? {
        if (spec.isBlank()) return null
        val range = parseRange(spec) ?: return "Use n or a-b (for example 16-32)"
        if (range.first > max || range.second > max) return "Must be at most $max"
        return null
    }
}
