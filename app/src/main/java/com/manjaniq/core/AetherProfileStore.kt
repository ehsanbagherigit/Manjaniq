package com.manjaniq.core

import android.content.Context
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig

class AetherProfileStore(context: Context) {
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
        // Migration: try old single-config store
        if (profile == AetherProfile.WARP_DIRECT) {
            val oldJson = prefs.getString("aether_config_json", null) // from old AetherConfigStore (different prefs file, check)
            // Also check legacy prefs name
            val legacy = context.getSharedPreferences("manjaniq_aether_config", Context.MODE_PRIVATE)
            val legacyJson = legacy.getString("aether_config_json", null)
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

    fun loadAll(): Map<AetherProfile, AetherProfileConfig> {
        return AetherProfile.ALL.associateWith { loadFor(it) }
    }

    fun resetFor(profile: AetherProfile): AetherProfileConfig {
        val def = AetherProfileConfig(profileId = profile.id)
        saveFor(profile, def)
        return def
    }

    fun exportAllJson(): String {
        val o = org.json.JSONObject()
        o.put("selected", getSelectedProfile().id)
        val arr = org.json.JSONArray()
        for (p in AetherProfile.ALL) {
            arr.put(org.json.JSONObject(loadFor(p).toJson()))
        }
        o.put("profiles", arr)
        return o.toString(2)
    }

    fun importAllJson(json: String): Boolean {
        return try {
            val o = org.json.JSONObject(json)
            val sel = o.optString("selected", "")
            if (sel.isNotEmpty()) {
                prefs.edit().putString(KEY_SELECTED, sel).apply()
            }
            val arr = o.optJSONArray("profiles") ?: return false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cfg = AetherProfileConfig.fromJson(obj.toString()) ?: continue
                val prof = AetherProfile.byId(cfg.profileId)
                saveFor(prof, cfg)
            }
            true
        } catch (_: Exception) { false }
    }

    private fun keyFor(profile: AetherProfile) = "profile_${profile.id}_config"

    companion object {
        private const val PREFS_NAME = "manjaniq_aether_profiles"
        private const val KEY_SELECTED = "selected_profile"
    }
}
