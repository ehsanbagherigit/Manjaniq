package com.manjaniq

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup smoke test — the real launch verification.
 *
 * This is NOT an "am start returned ok" check. It:
 *  1. Starts the installed application through the actual
 *     launcher entry point (the MAIN/LAUNCHER intent,
 *     exactly what a tap on the home-screen icon fires).
 *  2. Waits for startup to complete (activity RESUMED).
 *  3. Confirms the target application process remains alive
 *     (same pid, still listed by the activity manager) —
 *     and stays alive across backgrounding and resuming.
 *  4. Confirms MainActivity reached the RESUMED state.
 *  5. Confirms the Home screen's expected UI elements are
 *     visible through the real view hierarchy (engine
 *     status card, start control, both engine chips) —
 *     a meaningful screen marker, not just the existence
 *     of an activity object.
 *  6. Fails with logcat diagnostics if the process exits,
 *     crashes, or the expected screen never appears.
 *
 * A runtime permission dialog is never accepted as a
 * successful launch: POST_NOTIFICATIONS is requested only
 * when the user starts the engine, never at cold start, so
 * the launcher activity itself comes to the foreground.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LaunchSmokeTest {

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val launcherIntent: Intent by lazy {
        val intent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
        assertNotNull(
            "no launcher intent for ${targetContext.packageName} — " +
                "manifest MAIN/LAUNCHER intent-filter missing",
            intent
        )
        intent!!
    }

    @get:Rule
    val scenarioRule = ActivityScenarioRule<MainActivity>(launcherIntent)

    private val appPid = Process.myPid()
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun clearLogcat() {
        // Clear stale logs so failure diagnostics are
        // attributable to this launch only.
        Runtime.getRuntime().exec("logcat -c")
    }

    @After
    fun ensureEngineStopped() {
        // Leave the device clean for the next test class.
        targetContext.stopService(
            Intent(targetContext, com.manjaniq.service.EngineService::class.java)
        )
    }

    @Test
    fun freshLaunchKeepsProcessAliveAndShowsHomeScreen() {
        // (2)+(4) Startup completed: the activity is RESUMED
        // and the package is in the foreground.
        scenarioRule.scenario.moveToState(
            androidx.lifecycle.Lifecycle.State.RESUMED
        )
        assertEquals(
            "MainActivity did not reach RESUMED",
            androidx.lifecycle.Lifecycle.State.RESUMED,
            scenarioRule.scenario.state
        )

        // (5) Home screen markers through the real view
        // hierarchy: engine status card, start control and
        // both engine chips (RSTA Available, Aether Coming
        // Soon) must be visible.
        assertTrue(
            "Home screen: START ENGINE control not visible",
            device.wait(
                Until.findObject(By.text(startEngineLabel())),
                10000
            ) != null
        )
        assertTrue(
            "Home screen: RSTA engine chip not visible",
            device.wait(
                Until.findObject(By.text("RSTA")),
                10000
            ) != null
        )
        assertTrue(
            "Home screen: Aether Coming Soon chip not visible",
            device.wait(
                Until.findObject(By.text("Coming Soon")),
                10000
            ) != null
        )
        // Honest initial state on a fresh install.
        assertTrue(
            "engine must honestly report STOPPED before any start",
            device.wait(
                Until.findObject(By.text("STOPPED")),
                10000
            ) != null
        )

        // (3) The target application process remains alive
        // with the same pid.
        assertTrue(
            "app process is not alive after launch" + diagnostics(),
            processAlive()
        )
        Thread.sleep(2000)
        assertTrue(
            "app process exited shortly after launch" + diagnostics(),
            processAlive()
        )

        // Cross-check from outside the process: the app
        // package must be the one in the foreground.
        assertEquals(
            "foreground package is not the app — a system " +
                "screen (permission dialog, etc.) may be on top",
            targetContext.packageName,
            device.currentPackageName
        )
    }

    @Test
    fun processSurvivesBackgroundingAndResuming() {
        scenarioRule.scenario.moveToState(
            androidx.lifecycle.Lifecycle.State.RESUMED
        )
        device.pressHome()
        Thread.sleep(500)
        assertTrue("process died while backgrounded", processAlive())

        targetContext.startActivity(
            launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        scenarioRule.scenario.moveToState(
            androidx.lifecycle.Lifecycle.State.RESUMED
        )
        assertTrue(
            "home screen not visible after resume",
            device.wait(
                Until.findObject(By.text(startEngineLabel())),
                10000
            ) != null
        )
        assertTrue("process died after resume", processAlive())
    }

    /** The app's own label for the start control (locale-proof). */
    private fun startEngineLabel(): String =
        targetContext.getString(com.manjaniq.R.string.start_engine)

    /** True while the target application process is alive. */
    private fun processAlive(): Boolean {
        // The instrumentation runs inside the target process,
        // so reaching this line already proves the pid was
        // alive a moment ago; the manager query proves it is
        // still listed now with the same pid.
        val am = targetContext.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
        val listed = am.runningAppProcesses?.any {
            it.processName == targetContext.packageName && it.pid == appPid
        } ?: false
        return listed && Process.myPid() == appPid
    }

    /** Logcat tail attached to assertion failures. */
    private fun diagnostics(): String = buildString {
        appendLine(" --- logcat tail ---")
        append(
            try {
                Runtime.getRuntime().exec("logcat -d -t 200")
                    .inputStream.bufferedReader().readText()
            } catch (t: Throwable) {
                "logcat unavailable: ${t.message}"
            }
        )
    }
}
