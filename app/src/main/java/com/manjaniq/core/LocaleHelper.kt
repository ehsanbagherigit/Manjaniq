// Package com.manjaniq.core provides locale selection and context
// wrapping for the application's supported languages (English and
// Persian).
package com.manjaniq.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Locale handling for Manjaniq.
 *
 * The app follows the system locale by default: Persian when the
 * system locale is Persian (right-to-left layout), English otherwise.
 * [wrapContext] is applied in [com.manjaniq.ManjaniqApplication]
 * via `attachBaseContext`, which is the only correct place to
 * override an app's locale.
 *
 * The configuration is ALWAYS copied before modification. An earlier
 * implementation mutated `context.resources.configuration` directly —
 * that object is shared process-wide, and mutating it from
 * `attachBaseContext` crashed the app immediately after install
 * (the process disappeared after launch while builds and unit tests
 * stayed green). The regression test `wrapContextDoesNotMutateBaseConfiguration`
 * pins the copy semantics.
 */
object LocaleHelper {

    /** Locales the app is translated into. */
    val SUPPORTED_LOCALES: List<Locale> = listOf(Locale.ENGLISH, Locale("fa"))

    /**
     * Returns the app locale for a given system locale: Persian when
     * the system language is Persian, English otherwise. Pure function
     * (no Android dependencies) so it is unit-testable on the JVM.
     */
    fun resolveLocale(systemLocale: Locale): Locale =
        if (systemLocale.language == "fa") Locale("fa") else Locale.ENGLISH

    /** The locale the app is currently running with. */
    fun currentLocale(context: Context): Locale =
        resolveLocale(context.resources.configuration.locales.get(0))

    /**
     * Returns a context whose resources use the resolved app locale.
     * The base context's configuration is never modified.
     */
    fun wrapContext(context: Context): Context {
        val locale = currentLocale(context)
        // Copy, never mutate: context.resources.configuration is the
        // shared process configuration object.
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
