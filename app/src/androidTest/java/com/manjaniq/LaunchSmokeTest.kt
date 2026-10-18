package com.manjaniq

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal startup smoke test — uses ActivityScenario instead of Compose rule
 * to avoid Compose test flakiness. If attachBaseContext/onCreate crashes,
 * launch will throw. Previous LocaleHelper bug caused immediate exit before
 * any activity creation; green CI missed it because only host unit tests ran.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @Test
    fun mainActivityLaunchesWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("Activity should be created", activity)
                assertNotNull("Window should exist", activity.window)
                assertNotNull("DecorView should exist", activity.window.decorView)
            }
        }
    }

    @Test
    fun launcherIconIsPresent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = activity.packageManager.getApplicationInfo(activity.packageName, 0)
                assertTrue("android:icon missing", appInfo.icon != 0)
            }
        }
    }
}
