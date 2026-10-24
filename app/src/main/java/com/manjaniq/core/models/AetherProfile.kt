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
    );

    /**
     * What this installation can actually do with this route.
     *
     * The static [capability] above is the shipping table: WARP over Aether is
     * always available, and the Psiphon route needs the psiphon-tunnel-core child
     * process.
     *
     * That last part is not a constant: CI builds the helper and packages it, so
     * a build can ship it or not. [AetherHelpers] answers from the installation
     * itself - strictly from the native library directory, never from the
     * writable data directory - so the badge never claims a route will work when
     * the executable it needs is absent, and never hides one that will.
     */
    fun capabilityInThisBuild(): ProfileCapability = when (this) {
        WARP_DIRECT -> ProfileCapability.Available
        AETHER_PSIPHON ->
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
 * The extra fields are Psiphon's, stored as strings to avoid over-engineering.
 */
data class AetherProfileConfig(
    val profileId: String,
    val base: AetherConfig = AetherConfig.DEFAULT,
    // Psiphon-specific (used only for the AETHER_PSIPHON route)
    val psiphonEnabled: Boolean = false,
    val psiphonEndpoint: String = "", // e.g. 127.0.0.1:1821 if Psiphon installed separately
    val psiphonRegion: String = ""
) {
    /**
     * The engine's command line for this route: [base]'s own arguments, plus the
     * Psiphon values this data class stores on top of it.
     *
     * Those values are the compact form of `--psiphon`, `--psiphon-region` and
     * `--psiphon-bind` (`cli.rs:512-521`). Nothing here is stored and then dropped:
     * a value on this screen is a value on the command line.
     */
    fun toAetherArgs(
        contextFilesDir: String,
        helpers: AetherHelperPaths = AetherHelperPaths.none
    ): List<String> {
        val args = base.toAetherArgs(contextFilesDir).toMutableList()
        if (psiphonEnabled) {
            args += "--psiphon"
            // The child process is spawned by the engine (`psiphon.rs:749`) and is
            // not on PATH under Android, so the packaged file is named explicitly.
            if (helpers.psiphonBinary.isNotEmpty()) {
                args += "--psiphon-bin"; args += helpers.psiphonBinary
            }
            if (helpers.psiphonDirectory.isNotEmpty()) {
                args += "--psiphon-dir"; args += helpers.psiphonDirectory
            }
        }
        if (psiphonRegion.isNotBlank()) {
            args += "--psiphon-region"; args += psiphonRegion.trim()
        }
        // `psiphonEndpoint` is the local address the child process binds, which is
        // `--psiphon-bind` (cli.rs:519); an empty value leaves Aether's own choice.
        if (psiphonEndpoint.isNotBlank()) {
            args += "--psiphon-bind"; args += psiphonEndpoint.trim()
        }
        return args
    }

    fun validateForProfile(profile: AetherProfile): String? {
        // First validate base
        val baseErr = base.validate()
        if (baseErr != null) return baseErr
        if (psiphonEnabled) {
            if (psiphonEndpoint.isNotEmpty()) {
                val port = psiphonEndpoint.substringAfterLast(":").toIntOrNull()
                if (port == null || port !in 1024..65535) return "Invalid Psiphon endpoint port"
            }
            // The helper is a packaged child process. A build that does not ship it
            // is refused here, with the reason, rather than failing deep inside the
            // engine once the tunnel is already being dialled.
            if (!AetherHelpers.hasPsiphon()) {
                return "Psiphon needs the ${AetherHelpers.PSIPHON_FILE} executable and this " +
                    "build does not ship it: ship it as a native library " +
                    "(see AETHER_CAPABILITIES.md §6)"
            }
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
                    psiphonRegion = o.optString("psiphonRegion", "")
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
 * Fragmenting is not listed here at all: it is edited once, in its own area of
 * the Aether screen, because it belongs to a carrier rather than to a route.
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
    }
}

/** True when [profile] dials an Aether gateway (i.e. it owns the gateway section). */
fun profileUsesAetherGateway(profile: AetherProfile): Boolean =
    getSectionsForProfile(profile).any { it.titleRes == R.string.section_gateway }

/**
 * True when [profile] can carry MASQUE at all, which is the only thing
 * fragmenting can ever apply to: `masque_h2.rs` is its single consumer. The
 * fragmenting values are still stored per route; this only decides whether
 * reporting them for that route is meaningful.
 */
fun profileSupportsFragmenting(profile: AetherProfile): Boolean = profileUsesAetherGateway(profile)

object AetherProfileManager {
    var selectedProfile: AetherProfile = AetherProfile.WARP_DIRECT
}
