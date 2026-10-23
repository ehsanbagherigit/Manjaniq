package com.manjaniq.core

import android.content.Context
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig

class AetherProfileStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedProfile(): AetherProfile {
        val id = prefs.getString(KEY_SELECTED, null) ?: AetherProfile.WARP_DIRECT.id
        return AetherProfile.byId(id)
    }

    fun setSelectedProfile(profile: AetherProfile) {
        prefs.edit().putString(KEY_SELECTED, profile.id).apply()
    }

    fun loadFor(profile: AetherProfile): AetherProfileConfig {
        val key = keyFor(profile)
        val json = prefs.getString(key, null)
        if (json != null) {
            AetherProfileConfig.fromJson(json)?.let { return it }
        }
        // Migration from the single-config store that existed before profiles.
        // There is no "aether_config_json" key in *this* preferences file, so it
        // is not read here any more: AetherConfigStore owns that key in
        // "manjaniq_aether_config".
        if (profile == AetherProfile.WARP_DIRECT) {
            val legacy = context.getSharedPreferences("manjaniq_aether_config", Context.MODE_PRIVATE)
            val legacyJson = legacy.getString("aether_config_json", null)
            // `json` is retried as a bare AetherConfig: an older build could have
            // written a base config under the profile key, which parses as
            // AetherConfig but not as AetherProfileConfig.
            val toMigrate = legacyJson ?: json
            if (toMigrate != null) {
                val cfg = AetherConfig.fromJson(toMigrate)
                if (cfg != null) {
                    val migrated = AetherProfileConfig(profileId = profile.id, base = cfg)
                    saveFor(profile, migrated)
                    return migrated
                }
            }
        }
        return AetherProfileConfig(profileId = profile.id)
    }

    fun saveFor(profile: AetherProfile, config: AetherProfileConfig) {
        prefs.edit().putString(keyFor(profile), config.toJson()).apply()
    }

    fun resetFor(profile: AetherProfile): AetherProfileConfig {
        val def = AetherProfileConfig(profileId = profile.id)
        saveFor(profile, def)
        return def
    }

    private fun keyFor(profile: AetherProfile) = "profile_${profile.id}_config"

    companion object {
        private const val PREFS_NAME = "manjaniq_aether_profiles"
        private const val KEY_SELECTED = "selected_profile"
    }
}
