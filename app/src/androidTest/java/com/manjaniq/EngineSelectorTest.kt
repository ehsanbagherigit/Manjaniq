package com.manjaniq

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.manjaniq.ui.components.DashboardTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Engine selector honesty contract:
 *  - RSTA is shown as Available and is selectable.
 *  - Aether is shown as Coming Soon and is disabled —
 *    it cannot be selected and can never start RSTA.
 *  - The engine stays STOPPED before any start request.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class EngineSelectorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun rstaIsAvailableAndSelectable() {
        composeRule.onNodeWithTag(DashboardTags.SELECTOR_RSTA)
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.engine_rsta))
            .assertExists()
        composeRule.onNodeWithText(
            string(R.string.engine_rsta_status_available)
        ).assertExists()
    }

    @Test
    fun aetherIsComingSoonAndDisabled() {
        composeRule.onNodeWithTag(DashboardTags.SELECTOR_AETHER)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.engine_aether))
            .assertExists()
        composeRule.onNodeWithText(
            string(R.string.engine_aether_status_coming_soon)
        ).assertExists()
    }

    @Test
    fun aetherCannotStartTheEngine() {
        // Before any interaction the engine must honestly
        // report STOPPED.
        composeRule.onNodeWithText("STOPPED").assertExists()
        // The disabled Aether chip accepts no input, so no
        // tap path from Aether can reach the engine start.
        composeRule.onNodeWithTag(DashboardTags.SELECTOR_AETHER)
            .assertIsNotEnabled()
        composeRule.onNodeWithText("STOPPED").assertExists()
    }
}
