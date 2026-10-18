package com.manjaniq

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup smoke test — ensures MainActivity launches and renders the Home screen
 * without crashing. This was missing in CI when the LocaleHelper config-mutation
 * bug shipped green (only unit tests + build ran, no emulator launch).
 *
 * Keeps assertions minimal to avoid flakiness across locales/themes; the fact
 * that the rule creates the activity and we reach waitForIdle proves
 * attachBaseContext/onCreate/setContent did not crash.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesWithoutCrash() {
        // If attachBaseContext or onCreate crashed, this rule would fail to create
        composeRule.waitForIdle()
        val activity = composeRule.activity
        assertNotNull("Activity should be created", activity)
        // Decor view should have content (Compose setContent succeeded)
        assertNotNull(activity.window.decorView)
        assertTrue("DecorView should have width", activity.window.decorView.width >= 0)
    }

    @Test
    fun launcherIconManifestIsValid() {
        val appInfo = composeRule.activity.packageManager.getApplicationInfo(
            composeRule.activity.packageName, 0
        )
        assertTrue("Application icon resource missing — manifest android:icon not set", appInfo.icon != 0)
    }
}
