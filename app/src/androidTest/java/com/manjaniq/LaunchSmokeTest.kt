package com.manjaniq

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup smoke test — ensures MainActivity launches and renders the Home screen
 * without crashing. This was missing in CI when the LocaleHelper config-mutation
 * bug shipped green (only unit tests + build ran, no emulator launch).
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesAndShowsHome() {
        // Wait for Compose to settle; any crash in attachBaseContext/onCreate/setContent will fail here
        composeRule.waitForIdle()
        // Home screen should show core selector or title — check for app name or RSTA text
        // Use waitForIdle + basic node check to prove UI rendered
        try {
            composeRule.onNodeWithText("Manjaniq", useUnmergedTree = true).assertIsDisplayed()
        } catch (_: AssertionError) {
            // Fallback: check for RSTA core selector text if app_name not directly visible
            composeRule.onNodeWithText("RSTA", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun launcherIconManifestIsValid() {
        // Verify manifest declares launcher icon — prevents regression of missing icon bug
        val appInfo = composeRule.activity.packageManager.getApplicationInfo(
            composeRule.activity.packageName, 0
        )
        assert(appInfo.icon != 0) { "Application icon resource missing — manifest android:icon not set" }
    }
}
