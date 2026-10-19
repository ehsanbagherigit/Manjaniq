package com.manjaniq

import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.AppLaunch.waitForHome
import com.manjaniq.AppLaunch.waitForTag
import com.manjaniq.AppLaunch.waitUntilDisplayed
import com.manjaniq.core.AppPreferences
import com.manjaniq.core.SelectedCore
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.navigation.AppDestination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drawer navigation and core selection against the real UI. The drawer is
 * opened through the real top-bar button, destinations are reached by
 * clicking the real drawer items, and the back button is the real system
 * back dispatched through the activity.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<Activity>? = null

    @Before
    fun setUp() {
        AppLaunch.resetPreferences()
        scenario = AppLaunch.launch()
        compose.waitForHome()
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        AppLaunch.resetPreferences()
    }

    private fun openDrawerAndGo(destination: AppDestination, screenTag: String) {
        compose.onNodeWithTag(TestTags.TOP_BAR_MENU).assertIsDisplayed().performClick()
        compose.waitForIdle() // drawer open animation
        compose.waitUntilDisplayed(TestTags.drawerItem(destination.route))
        compose.onNodeWithTag(TestTags.drawerItem(destination.route)).performClick()
        compose.waitUntilDisplayed(screenTag)
        compose.waitForIdle() // drawer close animation before the next step
    }

    @Test
    fun drawerReachesSettingsLogsAboutAndBackReturnsHome() {
        openDrawerAndGo(AppDestination.Settings, TestTags.SETTINGS_SCREEN)
        compose.onNodeWithTag(TestTags.LANGUAGE_EN).assertExists()
        compose.onNodeWithTag(TestTags.LANGUAGE_FA).assertExists()

        openDrawerAndGo(AppDestination.Logs, TestTags.LOGS_SCREEN)
        openDrawerAndGo(AppDestination.About, TestTags.ABOUT_SCREEN)

        // Home is the start destination; all other destinations are
        // launched single-top with popUpTo(start), so one back press from
        // any of them lands on Home without leaving the activity.
        Espresso.pressBack()
        compose.waitForHome()
        compose.onNodeWithTag(TestTags.HOME_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CORE_SELECTOR).assertIsDisplayed()
    }

    @Test
    fun drawerHomeItemReturnsToHomeFromSettings() {
        openDrawerAndGo(AppDestination.Settings, TestTags.SETTINGS_SCREEN)
        openDrawerAndGo(AppDestination.Home, TestTags.HOME_SCREEN)
        compose.onNodeWithTag(TestTags.ENGINE_TOGGLE).assertExists()
    }

    @Test
    fun aetherIsComingSoonAndCannotStartAnything() {
        compose.onNodeWithTag(TestTags.CORE_CARD_AETHER).assertIsDisplayed().performClick()
        compose.waitForTag(TestTags.AETHER_DISABLED_BUTTON)

        // The only "start" control for Aether is disabled; the real RSTA
        // start/stop button is not offered at all.
        compose.onNodeWithTag(TestTags.AETHER_DISABLED_BUTTON).assertExists().assertIsNotEnabled()
        compose.onAllNodesWithTag(TestTags.ENGINE_TOGGLE).assertCountEquals(0)
        // Clicking a disabled button must be a no-op (performClick on a
        // disabled node is rejected by Compose, which is itself the proof).
        assertEquals(SelectedCore.AETHER, AppPreferences(AppLaunch.context).currentCore())

        // Switching back restores the real RSTA controls.
        compose.onNodeWithTag(TestTags.CORE_CARD_RSTA).performClick()
        compose.waitForTag(TestTags.ENGINE_TOGGLE)
        compose.onNodeWithTag(TestTags.ENGINE_TOGGLE).assertExists()
        assertEquals(SelectedCore.RSTA, AppPreferences(AppLaunch.context).currentCore())
    }

    @Test
    fun coreSelectionPersistsAcrossRecreate() {
        compose.onNodeWithTag(TestTags.CORE_CARD_AETHER).performClick()
        compose.waitForTag(TestTags.AETHER_DISABLED_BUTTON)

        scenario!!.recreate()
        compose.waitForHome()
        compose.waitForTag(TestTags.AETHER_DISABLED_BUTTON)
        compose.onAllNodesWithTag(TestTags.ENGINE_TOGGLE).assertCountEquals(0)
    }
}
