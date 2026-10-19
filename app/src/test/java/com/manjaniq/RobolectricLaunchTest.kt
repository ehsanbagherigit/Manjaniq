package com.manjaniq

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AppLanguage
import com.manjaniq.core.LocaleHelper
import com.manjaniq.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Host-JVM (Robolectric) launch of the real MainActivity. This is a fast
 * early signal that the real UI composes without the native library (the
 * repository degrades to STOPPED instead of crashing); the authoritative
 * gate remains the on-device LaunchSmokeTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RobolectricLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun realHomeScreenComposesOnHost() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(TestTags.HOME_SCREEN))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(TestTags.HOME_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CORE_SELECTOR).assertExists()
        compose.onNodeWithTag(TestTags.ENGINE_TOGGLE).assertExists()
        assertEquals(Lifecycle.State.RESUMED, compose.activityRule.scenario.state)
        compose.activityRule.scenario.onActivity { assertFalse(it.isFinishing) }
    }

    @Test
    fun localeHelperResolvesLocales() {
        assertEquals(Locale("fa"), LocaleHelper.getCurrentLocale(AppLanguage.FA))
        assertEquals(Locale.ENGLISH, LocaleHelper.getCurrentLocale(AppLanguage.EN))
    }

    @Test
    fun localeHelperWrapsWithoutMutatingBaseConfiguration() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val before = Configuration(ctx.resources.configuration)

        val fa = LocaleHelper.wrapContext(ctx, AppLanguage.FA)
        assertEquals("fa", fa.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, fa.resources.configuration.layoutDirection)
        assertEquals(
            "wrapContext must copy, not mutate, the shared Configuration",
            before.locales, ctx.resources.configuration.locales
        )

        val en = LocaleHelper.wrapContext(ctx, AppLanguage.EN)
        assertEquals("en", en.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_LTR, en.resources.configuration.layoutDirection)
        assertTrue(fa.resources.getString(R.string.nav_settings) != en.resources.getString(R.string.nav_settings))
    }
}
