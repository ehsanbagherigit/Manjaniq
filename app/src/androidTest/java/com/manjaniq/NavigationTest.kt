package com.manjaniq

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.manjaniq.ui.components.DashboardTags
import com.manjaniq.ui.components.DashboardScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation contract of the app shell: every tab opens
 * its own screen and the dashboard markers stay stable
 * when returning.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun dashboardIsTheFirstScreen() {
        composeRule.onNodeWithTag(DashboardTags.ENGINE_STATUS)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.START_BUTTON)
            .assertIsDisplayed()
    }

    @Test
    fun settingsTabOpensSettingsScreen() {
        composeRule.onNodeWithText(string(R.string.tab_settings))
            .performClick()
        composeRule.onNodeWithText(string(R.string.settings_listen_host))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_fake_sni))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_save_button")
            .assertIsDisplayed()
    }

    @Test
    fun logsTabOpensLogsScreen() {
        composeRule.onNodeWithText(string(R.string.tab_logs))
            .performClick()
        // Empty-state copy is the logs screen marker.
        composeRule.onNodeWithText(string(R.string.logs_empty))
            .assertIsDisplayed()
    }

    @Test
    fun aboutTabOpensAboutScreen() {
        composeRule.onNodeWithText(string(R.string.tab_about))
            .performClick()
        composeRule.onNodeWithText(string(R.string.about_description))
            .assertIsDisplayed()
    }

    @Test
    fun tabsSwitchBothWaysWithoutDeadScreens() {
        composeRule.onNodeWithText(string(R.string.tab_settings))
            .performClick()
        composeRule.onNodeWithText(string(R.string.tab_logs))
            .performClick()
        composeRule.onNodeWithText(string(R.string.tab_dashboard))
            .performClick()
        composeRule.onNodeWithTag(DashboardTags.ENGINE_STATUS)
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.tab_logs))
            .performClick()
        composeRule.onNodeWithText(string(R.string.tab_about))
            .performClick()
        composeRule.onNodeWithText(string(R.string.about_description))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.tab_dashboard))
            .performClick()
        composeRule.onNodeWithTag(DashboardTags.START_BUTTON)
            .assertIsDisplayed()
    }
}
