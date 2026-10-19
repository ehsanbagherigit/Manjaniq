package com.manjaniq

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.manjaniq.core.AppLanguage
import com.manjaniq.core.AppPreferences
import com.manjaniq.core.SelectedCore
import com.manjaniq.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import java.util.Locale

/**
 * Shared helpers for on-device tests. The app is always started through the
 * *real launcher intent* resolved from PackageManager, exactly as a tap on
 * the home-screen icon would, and the real MainActivity composes the real
 * ManjaniqApp (there is no test-mode UI).
 */
object AppLaunch {

    const val STARTUP_TIMEOUT_MS = 30_000L
    const val UI_TIMEOUT_MS = 10_000L

    val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /** Deterministic starting point: English UI, RSTA core selected. */
    fun resetPreferences(language: AppLanguage = AppLanguage.EN, core: SelectedCore = SelectedCore.RSTA) {
        val prefs = AppPreferences(context)
        prefs.setLanguage(language)
        prefs.setCore(core)
    }

    /** The intent the launcher would fire; asserts it targets MainActivity. */
    fun launcherIntent(): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("PackageManager has no launcher intent for ${context.packageName}", intent)
        assertEquals(
            "Launcher intent must resolve to MainActivity",
            MainActivity::class.java.name,
            intent!!.component?.className
        )
        return intent
    }

    fun launch(): ActivityScenario<Activity> = ActivityScenario.launch(launcherIntent())

    /** Blocks (bounded) until at least one node with [tag] exists in the real UI. */
    fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        waitUntil(timeoutMs) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Blocks (bounded) until no node with [tag] exists. */
    fun ComposeTestRule.waitForTagGone(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        waitUntil(timeoutMs) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Blocks (bounded) until the node with [tag] is actually displayed on
     * screen (e.g. after the drawer's open animation has finished).
     */
    fun ComposeTestRule.waitUntilDisplayed(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        waitUntil(timeoutMs) {
            runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
        }
        onNodeWithTag(tag).assertIsDisplayed()
    }

    /** Waits for the real Home screen, which is only composed by ManjaniqApp. */
    fun ComposeTestRule.waitForHome() = waitForTag(TestTags.HOME_SCREEN, STARTUP_TIMEOUT_MS)

    /** Resources resolved for a specific locale, independent of the current UI language. */
    fun resourcesFor(locale: Locale): Resources {
        val cfg = Configuration(context.resources.configuration)
        cfg.setLocale(locale)
        return context.createConfigurationContext(cfg).resources
    }
}
