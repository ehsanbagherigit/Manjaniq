package com.manjaniq.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String) {
    EN("en"),
    FA("fa");

    companion object {
        fun fromCode(code: String): AppLanguage = when (code) {
            "fa" -> FA
            else -> EN
        }
    }
}

enum class SelectedCore(val id: String) {
    RSTA("rsta"),
    AETHER("aether");

    companion object {
        fun fromId(id: String): SelectedCore = when (id) {
            "aether" -> AETHER
            else -> RSTA
        }
    }
}

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _languageFlow = MutableStateFlow(currentLanguage())
    val languageFlow: StateFlow<AppLanguage> = _languageFlow.asStateFlow()

    private val _coreFlow = MutableStateFlow(currentCore())
    val coreFlow: StateFlow<SelectedCore> = _coreFlow.asStateFlow()

    fun currentLanguage(): AppLanguage {
        val code = prefs.getString(KEY_LANGUAGE, AppLanguage.EN.code) ?: AppLanguage.EN.code
        return AppLanguage.fromCode(code)
    }

    fun currentCore(): SelectedCore {
        val id = prefs.getString(KEY_CORE, SelectedCore.RSTA.id) ?: SelectedCore.RSTA.id
        return SelectedCore.fromId(id)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.code) }
        _languageFlow.value = language
    }

    fun setCore(core: SelectedCore) {
        prefs.edit { putString(KEY_CORE, core.id) }
        _coreFlow.value = core
    }

    companion object {
        private const val PREFS_NAME = "manjaniq_app_settings"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_CORE = "selected_core"
    }
}

/**
 * Global holder for language to allow MainActivity recreation handling.
 * The AppPreferences is the source of truth persisted in SharedPreferences.
 */
object AppLocaleManager {
    var currentLanguage by mutableStateOf(AppLanguage.EN)
        private set

    fun update(language: AppLanguage) {
        currentLanguage = language
    }
}
