package com.manjaniq

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal startup smoke test — proves MainActivity can be created without
 * crashing in attachBaseContext/onCreate/setContent. Previously LocaleHelper
 * mutated shared Configuration causing immediate exit before UI inflated,
 * and green CI missed it because only host unit tests ran.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesWithoutCrash() {
        composeRule.waitForIdle()
        val activity = composeRule.activity
        assertNotNull(activity)
        // If we reach here, attachBaseContext + onCreate + Compose succeeded
        // No need to assert view size — just that activity exists and has window
        assertNotNull(activity.window)
    }

    @Test
    fun launcherIconIsPresent() {
        val appInfo = composeRule.activity.packageManager.getApplicationInfo(
            composeRule.activity.packageName, 0
        )
        assertTrue("android:icon missing", appInfo.icon != 0)
    }
}
