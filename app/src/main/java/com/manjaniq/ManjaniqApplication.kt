// Package com.manjaniq provides the Android Application subclass for
// Manjaniq.
package com.manjaniq

import android.app.Application
import android.content.Context
import com.manjaniq.core.LocaleHelper

/**
 * Application entry point. Resolves the app locale (English or
 * Persian, following the system locale) before any resource access
 * so the whole process sees a consistent configuration.
 *
 * The locale is applied by wrapping the base context with a copied
 * configuration — never by mutating the shared Configuration object
 * (see [LocaleHelper] for the regression this guards against).
 */
class ManjaniqApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }
}
