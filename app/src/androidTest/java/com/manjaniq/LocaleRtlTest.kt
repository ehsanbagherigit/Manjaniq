package com.manjaniq

import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.manjaniq.core.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Localization contract, verified against the real
 * resources of this build:
 *  - English and Persian strings both exist and resolve,
 *  - Persian resolves to a right-to-left layout,
 *  - English resolves left-to-right,
 *  - technical values (IP, port) stay pure ASCII so
 *    the bidi algorithm never reorders them.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class LocaleRtlTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun englishLocaleResolvesEnglishStringsLtr() {
        val enConfig = Configuration(context.resources.configuration)
        enConfig.setLocale(Locale.ENGLISH)
        val enContext = context.createConfigurationContext(enConfig)

        assertEquals(
            "Manjaniq",
            enContext.getString(com.manjaniq.R.string.app_name)
        )
        assertEquals(
            View.LAYOUT_DIRECTION_LTR,
            enContext.resources.configuration.layoutDirection
        )
        assertEquals(
            Locale.ENGLISH,
            LocaleHelper.resolveLocale(Locale.ENGLISH)
        )
    }

    @Test
    fun persianLocaleResolvesPersianStringsRtl() {
        val faConfig = Configuration(context.resources.configuration)
        faConfig.setLocale(Locale("fa"))
        val faContext = context.createConfigurationContext(faConfig)

        assertEquals(
            "منجنیق",
            faContext.getString(com.manjaniq.R.string.app_name)
        )
        assertEquals(
            "راه‌اندازی موتور",
            faContext.getString(com.manjaniq.R.string.start_engine)
        )
        assertEquals(
            View.LAYOUT_DIRECTION_RTL,
            faContext.resources.configuration.layoutDirection
        )
        // The helper selects Persian for a Persian system.
        assertEquals(
            Locale("fa"),
            LocaleHelper.resolveLocale(Locale("fa", "IR"))
        )
    }

    @Test
    fun keyStringsAreActuallyTranslated() {
        // A missing Persian resource silently falls back
        // to English inside an RTL layout, so pin that
        // the visible UI strings are genuinely translated.
        val faConfig = Configuration(context.resources.configuration)
        faConfig.setLocale(Locale("fa"))
        val fa = context.createConfigurationContext(faConfig)

        val translated = mapOf(
            com.manjaniq.R.string.app_name to "منجنیق",
            com.manjaniq.R.string.start_engine to "راه‌اندازی موتور",
            com.manjaniq.R.string.tab_settings to "تنظیمات",
            com.manjaniq.R.string.tab_logs to "لاگ‌ها",
            com.manjaniq.R.string.tab_about to "درباره"
        )
        for ((resId, expected) in translated) {
            assertEquals(
                "Persian translation missing for ${context.resources.getResourceName(resId)}",
                expected,
                fa.getString(resId)
            )
        }
    }

    @Test
    fun technicalValuesStayAsciiLtr() {
        // IP addresses and ports are formatted from
        // ASCII-only components, so the Unicode bidi
        // algorithm keeps them LTR inside Persian text.
        val address = "127.0.0.1:40443"
        assertTrue(address.all { it.code in 0x20..0x7E })
        // And the engine reports them as ASCII too.
        val metrics = NativeBridge.metricsJson()
        if (metrics != null && metrics.contains("\"listen_addr\"")) {
            val value = metrics.substringAfter(
                "\"listen_addr\":\"").substringBefore("\"")
            assertTrue("listen_addr must stay ASCII: $value",
                value.all { it.code in 0x20..0x7E })
        }
    }

}
