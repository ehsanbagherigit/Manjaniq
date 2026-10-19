package com.manjaniq.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    fun wrapContext(context: Context, language: AppLanguage): Context {
        val locale = if (language == AppLanguage.FA) Locale("fa") else Locale("en")
        Locale.setDefault(locale)

        // Copy configuration to avoid mutating the shared base config — previous
        // code used `context.resources.configuration` directly which mutates the
        // global Configuration and can crash on first launch before super.attachBaseContext.
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }

        return context.createConfigurationContext(config)
    }

    fun getCurrentLocale(language: AppLanguage): Locale =
        if (language == AppLanguage.FA) Locale("fa") else Locale.ENGLISH
}
