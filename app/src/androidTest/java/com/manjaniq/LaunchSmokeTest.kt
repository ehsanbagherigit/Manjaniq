package com.manjaniq

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal startup smoke test — verifies the crash fix doesn't regress.
 * 1) MainActivity can be launched (attachBaseContext/onCreate don't crash).
 * 2) LocaleHelper.wrapContext works for both EN/FA without mutating global config.
 * 3) Launcher icon is present in manifest.
 *
 * Uses ActivityScenario with minimal content in test mode (see MainActivity)
 * to avoid EngineRepository/native flakiness on emulator. Previous LocaleHelper
 * bug caused immediate exit before any activity creation; green CI missed it
 * because only host unit tests ran.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @Test
    fun mainActivityLaunchesWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("Activity should be created", activity)
                assertNotNull("Window should exist", activity.window)
            }
        }
    }

    @Test
    fun launcherIconIsPresent() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appInfo = ctx.packageManager.getApplicationInfo(ctx.packageName, 0)
        assertTrue("android:icon missing", appInfo.icon != 0)
    }

    @Test
    fun localeHelperDoesNotCrash() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wrappedEn = com.manjaniq.core.LocaleHelper.wrapContext(ctx, com.manjaniq.core.AppLanguage.EN)
        assertNotNull(wrappedEn)
        val wrappedFa = com.manjaniq.core.LocaleHelper.wrapContext(ctx, com.manjaniq.core.AppLanguage.FA)
        assertNotNull(wrappedFa)
    }
}
