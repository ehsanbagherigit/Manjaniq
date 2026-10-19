package com.manjaniq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.util.Locale

/**
 * Pure-JVM part of the locale contract: the app supports
 * exactly English and Persian, and anything else falls back
 * to English.
 */
class LocaleHelperTest {

    @Test
    fun persianSystemLocaleSelectsPersian() {
        assertEquals(Locale("fa"), LocaleHelper.resolveLocale(Locale("fa")))
        assertEquals(Locale("fa"), LocaleHelper.resolveLocale(Locale("fa", "IR")))
    }

    @Test
    fun englishAndOtherLocalesSelectEnglish() {
        assertEquals(Locale.ENGLISH, LocaleHelper.resolveLocale(Locale.ENGLISH))
        assertEquals(Locale.ENGLISH, LocaleHelper.resolveLocale(Locale.US))
        assertEquals(Locale.ENGLISH, LocaleHelper.resolveLocale(Locale.GERMAN))
        assertEquals(Locale.ENGLISH, LocaleHelper.resolveLocale(Locale("xx")))
    }

    @Test
    fun supportedLocalesAreExactlyEnglishAndPersian() {
        assertEquals(
            listOf(Locale.ENGLISH, Locale("fa")),
            LocaleHelper.SUPPORTED_LOCALES
        )
    }
}
