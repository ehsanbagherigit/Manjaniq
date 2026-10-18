package com.manjaniq.core

import android.content.Context
import com.manjaniq.core.models.RSTAConfig

/**
 * Persists the user configuration in app-private SharedPreferences
 * (stored as genuine-format JSON, so what is saved is exactly what
 * Export shows and what the engine receives).
 */
class ConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): RSTAConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return RSTAConfig.DEFAULT
        return RSTAConfig.fromJson(json) ?: RSTAConfig.DEFAULT
    }

    fun save(config: RSTAConfig) {
        prefs.edit().putString(KEY_CONFIG, config.toJson()).apply()
    }

    fun resetToDefaults(): RSTAConfig {
        save(RSTAConfig.DEFAULT)
        return RSTAConfig.DEFAULT
    }

    companion object {
        private const val PREFS_NAME = "manjaniq_config"
        private const val KEY_CONFIG = "rsta_config_json"
    }
}
