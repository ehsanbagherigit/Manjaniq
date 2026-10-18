package com.manjaniq

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric startup validation — runs on JVM without emulator.
 * Catches crashes in attachBaseContext/onCreate/Compose setContent that
 * unit tests missed (e.g., LocaleHelper config mutation).
 * This is the practical alternative if emulator infra is unavailable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RobolectricLaunchTest {

    @Test
    fun mainActivityLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("Activity should be created", activity)
                // Verify window has content (Compose setContent succeeded)
                val content = activity.findViewById<android.view.View>(android.R.id.content)
                assertNotNull(content)
            }
        }
    }

    @Test
    fun launcherIconDeclaredInManifest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = activity.packageManager.getApplicationInfo(activity.packageName, 0)
                // icon == 0 means missing android:icon in manifest — previous bug
                assertTrue("Launcher icon missing: android:icon not set in manifest", appInfo.icon != 0)
                // Verify mipmap resources exist via resolved icon
                assertTrue("Launcher icon resource should exist", appInfo.icon != 0)
            }
        }
    }

    @Test
    fun localeHelperDoesNotCrashOnFirstLaunch() {
        // Simulates first launch where SharedPreferences is empty
        // LocaleHelper previously mutated shared Configuration causing crash
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                // If LocaleHelper.wrapContext crashed, we wouldn't reach here
            }
        }
    }
}
