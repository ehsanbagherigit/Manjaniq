package com.manjaniq

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.AppLaunch.waitForHome
import com.manjaniq.AppLaunch.waitForTag
import com.manjaniq.core.EngineRepository
import com.manjaniq.ui.TestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release gate: the *real* launcher activity must reach RESUMED, compose the
 * *real* redesigned Home screen, and the process must stay alive while the
 * UI keeps polling the engine.
 *
 * Nothing here is satisfied by a non-null window, an installed APK, or an
 * icon resource alone: the assertions target nodes that only exist when
 * ManjaniqApp() is composed.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<Activity>? = null

    @Before
    fun setUp() {
        AppLaunch.resetPreferences()
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun launcherActivityReachesResumedAndRendersRealHome() {
        val s = AppLaunch.launch().also { scenario = it }

        compose.waitUntil(AppLaunch.STARTUP_TIMEOUT_MS) { s.state == Lifecycle.State.RESUMED }
        assertEquals(Lifecycle.State.RESUMED, s.state)

        compose.waitForHome()
        compose.onNodeWithTag(TestTags.HOME_SCREEN).assertIsDisplayed()

        // Meaningful Home elements of the redesign, not placeholders.
        compose.onNodeWithTag(TestTags.TOP_BAR_MENU).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CORE_SELECTOR).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CORE_CARD_RSTA).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CORE_CARD_AETHER).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.ENGINE_TOGGLE).assertExists().assertIsEnabled()
        compose.onNodeWithTag(TestTags.CONNECTION_STATUS).assertExists()

        // The UI polls the engine every second. Wait for a poll result that
        // reflects the real native engine state (STOPPED at launch) instead of
        // sleeping a fixed time, then confirm the activity is still alive.
        compose.waitUntil(AppLaunch.UI_TIMEOUT_MS) {
            EngineRepository.uiState.value.state == com.manjaniq.core.models.EngineState.STOPPED && !EngineRepository.uiState.value.busy
        }
        assertEquals(Lifecycle.State.RESUMED, s.state)
        s.onActivity { activity ->
            assertFalse("Activity must not be finishing after initial render", activity.isFinishing)
            assertTrue("Launched activity must be MainActivity", activity is MainActivity)
            assertTrue(
                "Activity must be RESUMED",
                (activity as MainActivity).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            )
        }
        assertProcessIsForeground()
        compose.onNodeWithTag(TestTags.HOME_SCREEN).assertIsDisplayed()
    }

    @Test
    fun launcherMetadataPointsAtRealResources() {
        val ctx = AppLaunch.context
        val appInfo = ctx.packageManager.getApplicationInfo(ctx.packageName, 0)
        assertNotEquals("android:icon must be set", 0, appInfo.icon)
        // The icon resource must resolve to the adaptive icon drawable.
        val name = ctx.resources.getResourceEntryName(appInfo.icon)
        assertEquals("ic_launcher", name)
        assertNotNull(ctx.resources.getDrawable(appInfo.icon, ctx.theme))
        // In-app brand resources must decode.
        assertNotNull(ctx.resources.getDrawable(R.drawable.ic_manjaniq_logo, ctx.theme))
        assertNotNull(ctx.resources.getDrawable(R.drawable.ic_rsta_logo, ctx.theme))
        assertNotNull(ctx.resources.getDrawable(R.drawable.ic_aether_logo, ctx.theme))
        assertNotNull(ctx.resources.getDrawable(R.drawable.ic_stat_engine, ctx.theme))
    }

    @Test
    fun backgroundAndForegroundKeepsHomeAlive() {
        val s = AppLaunch.launch().also { scenario = it }
        compose.waitForHome()

        s.moveToState(Lifecycle.State.CREATED)   // like pressing Home
        s.moveToState(Lifecycle.State.RESUMED)   // and coming back
        compose.waitForTag(TestTags.HOME_SCREEN)
        compose.onNodeWithTag(TestTags.HOME_SCREEN).assertIsDisplayed()

        s.recreate()                              // configuration change / process-light recreate
        compose.waitForHome()
        compose.onNodeWithTag(TestTags.CORE_SELECTOR).assertIsDisplayed()
        assertEquals(Lifecycle.State.RESUMED, s.state)
    }

    private fun assertProcessIsForeground() {
        val am = AppLaunch.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val me = am.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
        assertNotNull("App process not listed as running", me)
        assertTrue(
            "App process should be in the foreground, importance=${me!!.importance}",
            me.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        )
    }
}
