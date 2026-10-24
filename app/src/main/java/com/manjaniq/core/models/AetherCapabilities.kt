package com.manjaniq.core.models

import com.manjaniq.R

/**
 * The inventory of Aether settings this screen can render.
 *
 * The app has exactly one way to start the engine - `aether_core_start`, the
 * core's own command line (`third_party/Aether/aether/src/cli.rs`) - so every key
 * listed here is applied, not merely recorded. [SETTING_KEYS] exists so a route
 * cannot name a setting that no composable draws: a typo would otherwise make a
 * control disappear without a word.
 */
object AetherCapabilities {

    /** Every setting key [com.manjaniq.ui.screens.AetherPanel] can render. */
    val SETTING_KEYS: Set<String> = linkedSetOf(
        "transport", "scanMode", "ipVersion", "peer",
        "socksBind", "httpProxyBind", "dns", "upstream",
        "noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath",
        "psiphonEnabled", "psiphonEndpoint", "psiphonRegion",
        "torSocks", "torBridges", "torUseBridges"
    )

    /** True when [key] names a setting the Aether screen can draw. */
    fun knows(key: String): Boolean = key in SETTING_KEYS
}

/**
 * The four protocol names `AetherConfig.transport` accepts.
 *
 * All four are real Aether protocols on the one start path this app uses:
 * `cli.rs:551-563` maps `--masque`, `--wg`, `--gool` and `--mim` to
 * `AETHER_PROTOCOL`, and `run_with` reads that variable before anything dials
 * (`lib.rs:77-108`). None of them is conditional on a mode any more, so none of
 * them is refused.
 */
enum class AetherTransportChoice(
    val id: String,
    val labelRes: Int,
    val detailRes: Int
) {
    MASQUE("masque", R.string.aether_transport_masque, R.string.aether_transport_masque_detail),
    WIREGUARD("wg", R.string.aether_transport_wg, R.string.aether_transport_wg_detail),
    GOOL("gool", R.string.aether_transport_gool, R.string.aether_transport_gool_detail),
    MIM("mim", R.string.aether_transport_mim, R.string.aether_transport_mim_detail);

    companion object {
        val ALL: List<AetherTransportChoice> = values().toList()
        val IDS: List<String> = ALL.map { it.id }

        fun byId(id: String): AetherTransportChoice? = ALL.firstOrNull { it.id == id }
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
 * fragmenting necessarily selects that carrier for every protocol that has one,
 * which is why `AetherConfig.usesHttp2Carrier()` pairs the two. Nothing refuses a
 * WireGuard route for carrying the setting: it is stored, exported and put on the
 * command line, and the screen states that only the MASQUE carrier uses it.
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
