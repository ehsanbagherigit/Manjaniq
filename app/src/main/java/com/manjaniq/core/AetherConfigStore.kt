package com.manjaniq.core

import android.content.Context
import com.manjaniq.core.models.AetherConfig

class AetherConfigStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AetherConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return AetherConfig.DEFAULT
        return AetherConfig.fromJson(json) ?: AetherConfig.DEFAULT
    }

    fun save(config: AetherConfig) {
        prefs.edit().putString(KEY_CONFIG, config.toJson()).apply()
    }

    fun resetToDefaults(): AetherConfig {
        save(AetherConfig.DEFAULT)
        return AetherConfig.DEFAULT
    }

    companion object {
        private const val PREFS_NAME = "manjaniq_aether_config"
        private const val KEY_CONFIG = "aether_config_json"
    }
}
