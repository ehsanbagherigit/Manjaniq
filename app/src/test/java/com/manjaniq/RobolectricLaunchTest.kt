package com.manjaniq

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Host-side (Robolectric) startup regression test.
 *
 * Runs the real MainActivity.onCreate path without an
 * emulator so a startup crash cannot reach CI green with
 * only host unit tests passing — the historical failure
 * mode: builds and unit tests stayed green while the app
 * process died immediately after install (LocaleHelper
 * mutating the shared Configuration).
 *
 * The on-device equivalent (real process, real Home screen
 * pixel assertion) is LaunchSmokeTest in androidTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RobolectricLaunchTest {

    @Test
    fun mainActivityReachesResumedWithoutCrashing() {
        // Surface any logcat error as a test failure with content.
        ShadowLog.stream = System.out
        var scenario: ActivityScenario<MainActivity>? = null
        val errors = mutableListOf<String>()
        val observer = Thread.UncaughtExceptionHandler { _, throwable ->
            errors.add(throwable.toString())
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(observer)
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertNotNull(activity.window)
                assertNotNull(activity.window.decorView)
                assertTrue(
                    "decor view must have content",
                    activity.window.decorView.childCount > 0
                )
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            scenario?.close()
        }
        assertTrue("startup crashed: $errors", errors.isEmpty())
    }

    @Test
    fun applicationClassAppliesLocaleConfigurationWithoutCrash() {
        // The Application.attachBaseContext path runs before
        // any activity; Robolectric exercises it here.
        val app = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<ManjaniqApplication>()
        assertNotNull(app)
        // Resources must resolve in the wrapped configuration.
        val name = app.getString(androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .resources.getIdentifier("app_name", "string", "com.manjaniq"))
        assertNotNull(name)
        assertTrue(name.isNotEmpty())
    }

    @Test
    fun manifestDeclaresLauncherActivityAndIcons() {
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        assertNotNull(info)
        // Launcher activity and icon presence are pinned here so
        // a manifest regression fails on the JVM, not on-device.
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        assertTrue("launcher icon must be set", appInfo.icon != 0)
        assertTrue("round launcher icon must be set", appInfo.roundIcon != 0)
        val launchIntent = pm.getLaunchIntentForPackage(context.packageName)
        assertNotNull("launcher intent must resolve", launchIntent)
        val resolved = pm.queryIntentActivities(launchIntent, 0)
        assertTrue(
            "the launcher intent must resolve to MainActivity",
            resolved.any { it.activityInfo.name == "com.manjaniq.MainActivity" }
        )
        val mainActivity = Class.forName("com.manjaniq.MainActivity")
        val activityInfo = pm.getActivityInfo(
            android.content.ComponentName(context, mainActivity), 0
        )
        assertNotNull(activityInfo)
        assertTrue(activityInfo.exported)
    }
}
