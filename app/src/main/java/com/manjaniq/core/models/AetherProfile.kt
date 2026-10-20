package com.manjaniq.core.models

import com.manjaniq.R

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
        capability = ProfileCapability.RequiresExternalComponent
    ),
    AETHER_TOR(
        id = "aether_tor",
        titleRes = R.string.profile_aether_tor_title,
        routeRes = R.string.profile_aether_tor_route,
        iconRes = R.drawable.ic_aether_logo,
        descriptionRes = R.string.profile_aether_tor_desc,
        capability = ProfileCapability.RequiresExternalComponent
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
        capability = ProfileCapability.RequiresExternalComponent
    );

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
 * The settings a profile owns, in the order the Aether CLI asks for them
 * (protocol -> scan -> IP version -> carrier -> obfuscation -> local proxy).
 *
 * Fragmenting is listed only for profiles that can carry MASQUE: Aether applies
 * it on the MASQUE-over-HTTP/2 carrier (`masque_h2.rs`), so it has no meaning
 * for a Tor-only route.
 */
fun getSectionsForProfile(profile: AetherProfile): List<SettingSection> {
    // Keys shared by every route that dials an Aether gateway.
    val gateway = SettingSection(
        R.string.section_gateway,
        listOf("transport", "scanMode", "ipVersion", "peer")
    )
    val fragmenting = SettingSection(
        R.string.section_fragmenting,
        listOf("fragmentEnabled", "fragmentSize", "fragmentDelay", "fragmentSniSplit")
    )
    return when (profile) {
        AetherProfile.WARP_DIRECT -> listOf(
            gateway,
            fragmenting,
            SettingSection(R.string.section_tunnel, listOf("socksBind", "httpProxyBind", "dns", "upstream")),
            SettingSection(
                R.string.section_advanced,
                listOf("noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath")
            )
        )
        AetherProfile.AETHER_PSIPHON -> listOf(
            gateway,
            fragmenting,
            SettingSection(R.string.section_psiphon, listOf("psiphonEnabled", "psiphonEndpoint", "psiphonRegion")),
            SettingSection(R.string.section_tunnel, listOf("socksBind", "httpProxyBind", "dns", "upstream")),
            SettingSection(
                R.string.section_advanced,
                listOf("noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath")
            )
        )
        AetherProfile.TOR_DIRECT -> listOf(
            SettingSection(R.string.section_tor, listOf("torSocks", "torBridges", "torUseBridges")),
            SettingSection(R.string.section_tunnel, listOf("socksBind", "dns")),
            SettingSection(R.string.section_advanced, listOf("ipVersion"))
        )
        AetherProfile.AETHER_TOR -> listOf(
            gateway,
            fragmenting,
            SettingSection(R.string.section_tor, listOf("torSocks", "torBridges", "torUseBridges")),
            SettingSection(R.string.section_tunnel, listOf("socksBind", "httpProxyBind", "dns", "upstream")),
            SettingSection(
                R.string.section_advanced,
                listOf("noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath")
            )
        )
        AetherProfile.TOR_PSIPHON -> listOf(
            SettingSection(R.string.section_tor, listOf("torSocks", "torBridges", "torUseBridges")),
            SettingSection(R.string.section_psiphon, listOf("psiphonEnabled", "psiphonEndpoint", "psiphonRegion")),
            SettingSection(R.string.section_tunnel, listOf("socksBind", "upstream")),
            SettingSection(R.string.section_advanced, listOf("dns"))
        )
        AetherProfile.TOR_AETHER -> listOf(
            SettingSection(R.string.section_tor, listOf("torSocks", "torBridges", "torUseBridges")),
            gateway,
            fragmenting,
            SettingSection(R.string.section_tunnel, listOf("socksBind", "httpProxyBind", "dns", "upstream")),
            SettingSection(
                R.string.section_advanced,
                listOf("noizeProfile", "echEnabled", "quickReconnect", "teamName", "identityPath")
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

/** True when [profile] can carry MASQUE and therefore honour fragmenting. */
fun profileSupportsFragmenting(profile: AetherProfile): Boolean =
    getSectionsForProfile(profile).any { it.titleRes == R.string.section_fragmenting }

object AetherProfileManager {
    var selectedProfile: AetherProfile = AetherProfile.WARP_DIRECT
}
