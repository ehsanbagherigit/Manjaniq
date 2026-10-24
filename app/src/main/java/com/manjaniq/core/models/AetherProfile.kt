package com.manjaniq.core.models

import com.manjaniq.R
import com.manjaniq.core.AetherHelpers

/**
 * Selectable connection profiles - maintainable single-source definition.
 * Each profile has a truthful capability status, not just a label.
 */
enum class AetherProfile(
    val id: String,
    val titleRes: Int,
    val routeRes: Int,
    val iconRes: Int,
    val descriptionRes: Int,
    val capability: ProfileCapability
) {
    WARP_DIRECT(
        id = "warp_direct",
        titleRes = R.string.profile_warp_title,
        routeRes = R.string.profile_warp_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_warp_desc,
        capability = ProfileCapability.Available
    ),
    AETHER_PSIPHON(
        id = "aether_psiphon",
        titleRes = R.string.profile_aether_psiphon_title,
        routeRes = R.string.profile_aether_psiphon_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_aether_psiphon_desc,
        capability = ProfileCapability.RequiresExternalComponent
    ),
    TOR_DIRECT(
        id = "tor_direct",
        titleRes = R.string.profile_tor_direct_title,
        routeRes = R.string.profile_tor_direct_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_tor_direct_desc,
        // tor is compiled into libaether.so (`features = ["tor"]`), so this route
        // needs no component the build lacks - only, for bridges, a pluggable
        // transport.
        capability = ProfileCapability.NeedsConfiguration
    ),
    AETHER_TOR(
        id = "aether_tor",
        titleRes = R.string.profile_aether_tor_title,
        routeRes = R.string.profile_aether_tor_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_aether_tor_desc,
        capability = ProfileCapability.NeedsConfiguration
    ),
    TOR_PSIPHON(
        id = "tor_psiphon",
        titleRes = R.string.profile_tor_psiphon_title,
        routeRes = R.string.profile_tor_psiphon_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_tor_psiphon_desc,
        capability = ProfileCapability.RequiresExternalComponent
    ),
    TOR_AETHER(
        id = "tor_aether",
        titleRes = R.string.profile_tor_aether_title,
        routeRes = R.string.profile_tor_aether_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_tor_aether_desc,
        capability = ProfileCapability.NeedsConfiguration
    );

    /**
     * What this installation can actually do with this route.
     *
     * The static [capability] above is the shipping table: WARP over Aether is
     * always available, the Tor routes need configuration (tor is compiled in,
     * but bridges need a pluggable transport), and the Psiphon routes need the
     * psiphon-tunnel-core child process.
     *
     * That last part is not a constant: CI builds the helper and packages it, so
     * a build can ship it or not. [AetherHelpers] answers from the installation
     * itself - strictly from the native library directory, never from the
     * writable data directory - so the badge never claims a route will work when
     * the executable it needs is absent, and never hides one that will.
     */
    fun capabilityInThisBuild(): ProfileCapability = when (this) {
        WARP_DIRECT -> ProfileCapability.Available
        TOR_DIRECT, AETHER_TOR, TOR_AETHER -> ProfileCapability.NeedsConfiguration
        AETHER_PSIPHON, TOR_PSIPHON ->
            if (AetherHelpers.hasPsiphon()) ProfileCapability.NeedsConfiguration
            else ProfileCapability.RequiresExternalComponent
    }

    companion object {
        fun byId(id: String): AetherProfile = values().find { it.id == id } ?: WARP_DIRECT
        val ALL = values().toList()
    }
}

enum class ProfileCapability {
    Available,
    NeedsConfiguration,
    RequiresExternalComponent,
    Unsupported,
    NotVerified
}

/**
 * Per-profile extra settings that extend base AetherConfig.
 * Kept minimal - base config already covers most Aether CLI flags.
 * Extra fields are for Psiphon/Tor chaining, each stored as string to avoid over-engineering.
 */
data class AetherProfileConfig(
    val profileId: String,
    val base: AetherConfig = AetherConfig.DEFAULT,
    // Psiphon-specific (used only for profiles involving Psiphon)
    val psiphonEnabled: Boolean = false,
    val psiphonEndpoint: String = "", // e.g. 127.0.0.1:1821 if Psiphon installed separately
    val psiphonRegion: String = "",
    // Tor-specific (for profiles involving Tor)
    val torSocks: String = "", // e.g. 127.0.0.1:9050
    val torBridges: String = "",
    val torUseBridges: Boolean = false
) {
    /**
     * The engine's command line for this route: [base]'s own arguments, plus the
     * quick Tor/Psiphon values this data class stores on top of it.
     *
     * Those values are the compact form of flags the engine options group edits in
     * full (`--tor-bind`, `--tor-bridge`/`--tor-bridges`, `--psiphon`,
     * `--psiphon-region`, `--psiphon-bind`). They fill in only where the detailed
     * editor left the same flag at the engine's default, so the two editors cannot
     * emit one flag twice and the detailed one always wins. Nothing here is stored
     * and then dropped: a value on this screen is a value on the command line.
     */
    fun toAetherArgs(
        contextFilesDir: String,
        helpers: AetherHelperPaths = AetherHelperPaths.none
    ): List<String> {
        val args = base.toAetherArgs(contextFilesDir, helpers).toMutableList()
        val engine = base.engine
        if (psiphonEnabled && engine.psiphon.mode == AetherPsiphonMode.OFF) {
            args += "--psiphon"
        }
        if (psiphonRegion.isNotBlank() && engine.psiphon.region.isBlank()) {
            args += "--psiphon-region"; args += psiphonRegion.trim()
        }
        // `psiphonEndpoint` is the local address the child process binds, which is
        // `--psiphon-bind` (cli.rs:519); an empty value leaves Aether's own choice.
        if (psiphonEndpoint.isNotBlank() && engine.psiphon.bind.isBlank()) {
            args += "--psiphon-bind"; args += psiphonEndpoint.trim()
        }
        if (torSocks.isNotBlank() && engine.tor.bind.isBlank()) {
            args += "--tor-bind"; args += torSocks.trim()
        }
        // One value owns AETHER_TOR_BRIDGES (`cli.rs:501-506`), so either the named
        // lines are appended or the automatic fetch is asked for - never both.
        if (engine.tor.bridges == AetherTorBridges.DEFAULT) {
            val lines = torBridges.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                lines.forEach { line -> args += "--tor-bridge"; args += line }
            } else if (torUseBridges) {
                args += "--tor-bridges"
            }
        }
        return args
    }

    fun validateForProfile(profile: AetherProfile): String? {
        // First validate base
        val baseErr = base.validate()
        if (baseErr != null) return baseErr
        // Profile-specific validation
        when (profile) {
            AetherProfile.AETHER_PSIPHON, AetherProfile.TOR_PSIPHON -> {
                if (psiphonEnabled && psiphonEndpoint.isNotEmpty()) {
                    val port = psiphonEndpoint.substringAfterLast(":").toIntOrNull()
                    if (port == null || port !in 1024..65535) return "Invalid Psiphon endpoint port"
                }
            }
            AetherProfile.TOR_DIRECT, AetherProfile.AETHER_TOR, AetherProfile.TOR_PSIPHON, AetherProfile.TOR_AETHER -> {
                if (torSocks.isNotEmpty()) {
                    val port = torSocks.substringAfterLast(":").toIntOrNull()
                    if (port == null || port !in 1024..65535) return "Invalid Tor SOCKS port"
                }
            }
            else -> {}
        }
        return null
    }

    fun toJson(): String {
        val o = org.json.JSONObject()
        o.put("profileId", profileId)
        o.put("base", org.json.JSONObject(base.toJson()))
        o.put("psiphonEnabled", psiphonEnabled)
        o.put("psiphonEndpoint", psiphonEndpoint)
        o.put("psiphonRegion", psiphonRegion)
        o.put("torSocks", torSocks)
        o.put("torBridges", torBridges)
        o.put("torUseBridges", torUseBridges)
        return o.toString()
    }

    companion object {
        fun fromJson(json: String): AetherProfileConfig? {
            return try {
                val o = org.json.JSONObject(json)
                val baseObj = o.optJSONObject("base")
                val baseJson = baseObj?.toString() ?: "{}"
                AetherProfileConfig(
                    profileId = o.optString("profileId", AetherProfile.WARP_DIRECT.id),
                    base = AetherConfig.fromJson(baseJson) ?: AetherConfig.DEFAULT,
                    psiphonEnabled = o.optBoolean("psiphonEnabled", false),
                    psiphonEndpoint = o.optString("psiphonEndpoint", ""),
                    psiphonRegion = o.optString("psiphonRegion", ""),
                    torSocks = o.optString("torSocks", ""),
                    torBridges = o.optString("torBridges", ""),
                    torUseBridges = o.optBoolean("torUseBridges", false)
                )
            } catch (_: Exception) { null }
        }
    }
}

/** Defines which settings sections are shown for each profile */
data class SettingSection(
    val titleRes: Int,
    val keys: List<String> // keys correspond to AetherConfig fields or extra
)

/**
 * One row of the settings screen: a localised title and the keys it draws.
 *
 * Every key is checked against [AetherCapabilities.knows], so a route can never
 * name a setting the screen has no composable for: a typo fails loudly here
 * instead of making a control disappear without a word. The check runs in the
 * app too, not only in tests - a settings screen missing a control is exactly
 * the class of bug this build removes.
 */
private fun section(titleRes: Int, vararg keys: String): SettingSection {
    val unknown = keys.filterNot { AetherCapabilities.knows(it) }
    require(unknown.isEmpty()) { "unknown Aether setting key(s) $unknown" }
    return SettingSection(titleRes, keys.toList())
}

/**
 * The settings a profile owns, in the order the Aether CLI asks for them
 * (protocol -> scan -> IP version -> carrier -> obfuscation -> local proxy).
 *
 * Fragmenting is listed only for profiles that can carry MASQUE: Aether applies
 * it on the MASQUE-over-HTTP/2 carrier (`masque_h2.rs`), so it has no meaning
 * for a Tor-only route.
 */
fun getSectionsForProfile(profile: AetherProfile): List<SettingSection> {
    // Keys shared by every route that dials an Aether gateway.
    val gateway = section(R.string.section_gateway, "transport", "scanMode", "ipVersion", "peer")
    // Fragmenting is deliberately NOT a per-route section: it belongs to one carrier
    // (MASQUE over HTTP/2) rather than to a route, so it is edited once, in its own
    // area at the bottom of the Aether screen, for whichever route is selected.
    // On the one start path it is a flag of the same `aether_core_start` job -
    // `--fragment`/`--no-fragment`, `--fragment-size`, `--fragment-delay`, plus the
    // `--h2`/`--h3` carrier choice - so nothing has to be refused or applied out of
    // band any more. See AETHER_CAPABILITIES.md §3.
    return when (profile) {
        AetherProfile.WARP_DIRECT -> listOf(
            gateway,
            section(R.string.section_tunnel, "socksBind", "httpProxyBind", "dns", "upstream"),
            section(
                R.string.section_advanced,
                "noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath"
            )
        )
        AetherProfile.AETHER_PSIPHON -> listOf(
            gateway,
            section(R.string.section_psiphon, "psiphonEnabled", "psiphonEndpoint", "psiphonRegion"),
            section(R.string.section_tunnel, "socksBind", "httpProxyBind", "dns", "upstream"),
            section(
                R.string.section_advanced,
                "noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath"
            )
        )
        AetherProfile.TOR_DIRECT -> listOf(
            section(R.string.section_tor, "torSocks", "torBridges", "torUseBridges"),
            section(R.string.section_tunnel, "socksBind", "dns"),
            section(R.string.section_advanced, "ipVersion")
        )
        AetherProfile.AETHER_TOR -> listOf(
            gateway,
            section(R.string.section_tor, "torSocks", "torBridges", "torUseBridges"),
            section(R.string.section_tunnel, "socksBind", "httpProxyBind", "dns", "upstream"),
            section(
                R.string.section_advanced,
                "noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath"
            )
        )
        AetherProfile.TOR_PSIPHON -> listOf(
            section(R.string.section_tor, "torSocks", "torBridges", "torUseBridges"),
            section(R.string.section_psiphon, "psiphonEnabled", "psiphonEndpoint", "psiphonRegion"),
            section(R.string.section_tunnel, "socksBind", "upstream"),
            section(R.string.section_advanced, "dns")
        )
        AetherProfile.TOR_AETHER -> listOf(
            section(R.string.section_tor, "torSocks", "torBridges", "torUseBridges"),
            gateway,
            section(R.string.section_tunnel, "socksBind", "httpProxyBind", "dns", "upstream"),
            section(
                R.string.section_advanced,
                "noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath"
            )
        )
    }
}

/**
 * True when [profile] dials an Aether gateway at all (i.e. it owns the gateway
 * section). Tor-only routes do not, so their Aether settings stay hidden.
 */
fun profileUsesAetherGateway(profile: AetherProfile): Boolean =
    getSectionsForProfile(profile).any { it.titleRes == R.string.section_gateway }

/**
 * True when [profile] can carry MASQUE at all, which is the only thing
 * fragmenting can ever apply to: `masque_h2.rs` is its single consumer, so a
 * Tor-only route can never reach it. The fragmenting values are still stored per
 * route; this only decides whether reporting them for that route is meaningful.
 */
fun profileSupportsFragmenting(profile: AetherProfile): Boolean = profileUsesAetherGateway(profile)

object AetherProfileManager {
    var selectedProfile: AetherProfile = AetherProfile.WARP_DIRECT
}
