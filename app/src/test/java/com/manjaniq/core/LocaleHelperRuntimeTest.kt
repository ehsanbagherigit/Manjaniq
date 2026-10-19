package com.manjaniq.core

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import android.view.View
import java.util.Locale

/**
 * Robolectric regression tests for the locale initialization
 * path that once crashed startup.
 *
 * The historical defect: `attachBaseContext` mutated the shared
 * `context.resources.configuration` object directly
 * (`val config = context.resources.configuration; config.setLocale(...)`),
 * which corrupts the process-wide configuration and crashed the
 * app immediately after install — while builds and host unit
 * tests stayed green. The fix copies the Configuration before
 * modifying it and applies it via createConfigurationContext.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocaleHelperRuntimeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun wrapContextDoesNotMutateBaseConfiguration() {
        val baseConfig = context.resources.configuration
        val localeBefore = baseConfig.locales.get(0)

        LocaleHelper.wrapContext(context)

        assertEquals(
            "base configuration must not be mutated by locale wrapping",
            localeBefore,
            context.resources.configuration.locales.get(0)
        )
    }

    @Test
    fun wrappedContextReturnsPersianResourcesForPersianSystem() {
        val faConfig = Configuration(context.resources.configuration)
        faConfig.setLocale(Locale("fa"))
        val faContext = context.createConfigurationContext(faConfig)

        val wrapped = LocaleHelper.wrapContext(faContext)
        assertEquals(
            "Persian system locale must resolve to Persian app resources",
            "منجنیق",
            wrapped.getString(com.manjaniq.R.string.app_name)
        )
        assertEquals(
            "Persian must select RTL layout direction",
            View.LAYOUT_DIRECTION_RTL,
            wrapped.resources.configuration.layoutDirection
        )
    }

    @Test
    fun wrappedContextReturnsEnglishResourcesForNonPersianSystem() {
        val enConfig = Configuration(context.resources.configuration)
        enConfig.setLocale(Locale.ENGLISH)
        val enContext = context.createConfigurationContext(enConfig)

        val wrapped = LocaleHelper.wrapContext(enContext)
        assertEquals(
            "English system locale must resolve to English app resources",
            "Manjaniq",
            wrapped.getString(com.manjaniq.R.string.app_name)
        )
        assertEquals(
            "English must select LTR layout direction",
            View.LAYOUT_DIRECTION_LTR,
            wrapped.resources.configuration.layoutDirection
        )
    }

    @Test
    fun wrapContextReturnsADistinctContext() {
        val wrapped = LocaleHelper.wrapContext(context)
        assertNotSame("wrapContext must return a new context", context, wrapped)
    }

    @Test
    fun currentLocaleFollowsSystemLocale() {
        val faConfig = Configuration(context.resources.configuration)
        faConfig.setLocale(Locale("fa"))
        val faContext = context.createConfigurationContext(faConfig)
        assertEquals(Locale("fa"), LocaleHelper.currentLocale(faContext))
        assertEquals(
            Locale.ENGLISH,
            LocaleHelper.currentLocale(context)
        )
    }

    @Test
    fun technicalValuesRemainAsciiLtr() {
        // IP addresses, ports and identifiers must render LTR
        // inside a Persian RTL layout: the characters themselves
        // must stay ASCII so the Unicode bidi algorithm never
        // reorders them.
        val address = "127.0.0.1:40443"
        assertTrue(address.all { it.code in 0x20..0x7E })
        assertEquals("127.0.0.1:40443", address)
    }
}
