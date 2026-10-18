package com.manjaniq.core

import android.content.Context
import android.os.Build
import java.util.Locale

object LocaleHelper {
    fun wrapContext(context: Context, language: AppLanguage): Context {
        val locale = if (language == AppLanguage.FA) Locale("fa") else Locale("en")
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun getCurrentLocale(language: AppLanguage): Locale =
        if (language == AppLanguage.FA) Locale("fa") else Locale.ENGLISH
}
