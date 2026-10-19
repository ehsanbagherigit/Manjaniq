package com.manjaniq

import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.AppLaunch.waitForHome
import com.manjaniq.AppLaunch.waitUntilDisplayed
import com.manjaniq.core.AppLanguage
import com.manjaniq.core.AppPreferences
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.navigation.AppDestination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * English and Persian are verified independently on the real UI:
 * translated strings must come from resources, and Persian must flip the
 * layout direction (the top-bar menu button moves to the right edge).
 */
@RunWith(AndroidJUnit4::class)
class LocaleRtlTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<Activity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        AppLaunch.resetPreferences()
    }

    private fun menuIsOnRightHalf(): Boolean {
        val menu = compose.onNodeWithTag(TestTags.TOP_BAR_MENU).fetchSemanticsNode()
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        return menu.boundsInRoot.left > rootWidth / 2f
    }

    @Test
    fun englishHomeIsLtrWithEnglishStrings() {
        AppLaunch.resetPreferences(language = AppLanguage.EN)
        scenario = AppLaunch.launch()
        compose.waitForHome()

        val en = AppLaunch.resourcesFor(Locale.ENGLISH)
        compose.onNodeWithText(en.getString(R.string.select_core)).assertIsDisplayed()
        assertTrue("Menu button should be on the left in LTR", !menuIsOnRightHalf())
    }

    @Test
    fun persianHomeIsRtlWithPersianStrings() {
        AppLaunch.resetPreferences(language = AppLanguage.FA)
        scenario = AppLaunch.launch()
        compose.waitForHome()

        val fa = AppLaunch.resourcesFor(Locale("fa"))
        val en = AppLaunch.resourcesFor(Locale.ENGLISH)
        assertNotEquals("Persian translation must differ from English", en.getString(R.string.select_core), fa.getString(R.string.select_core))

        compose.onNodeWithText(fa.getString(R.string.select_core)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(en.getString(R.string.select_core)).fetchSemanticsNodes().size)
        assertTrue("Menu button should be on the right in RTL", menuIsOnRightHalf())

        // Technical values stay LTR-readable: the connection card shows the
        // listen address as plain ASCII regardless of UI language.
        compose.onNodeWithTag(TestTags.CONNECTION_STATUS).assertExists()
    }

    @Test
    fun switchingLanguageInSettingsPersistsAndRecreatesInPersian() {
        AppLaunch.resetPreferences(language = AppLanguage.EN)
        scenario = AppLaunch.launch()
        compose.waitForHome()

        compose.onNodeWithTag(TestTags.TOP_BAR_MENU).performClick()
        compose.waitForIdle()
        compose.waitUntilDisplayed(TestTags.drawerItem(AppDestination.Settings.route))
        compose.onNodeWithTag(TestTags.drawerItem(AppDestination.Settings.route)).performClick()
        compose.waitUntilDisplayed(TestTags.SETTINGS_SCREEN)

        compose.onNodeWithTag(TestTags.LANGUAGE_FA).performClick()

        // The preference is the source of truth and must be persisted...
        compose.waitUntil(AppLaunch.UI_TIMEOUT_MS) {
            AppPreferences(AppLaunch.context).currentLanguage() == AppLanguage.FA
        }
        // ...and the activity recreates itself in Persian (NavHost state is
        // restored, so we are still on Settings, now with the Persian title).
        val fa = AppLaunch.resourcesFor(Locale("fa"))
        val persianSettingsTitle = hasText(fa.getString(R.string.settings_title)) and
            hasAnyAncestor(hasTestTag(TestTags.SETTINGS_SCREEN))
        compose.waitUntil(AppLaunch.STARTUP_TIMEOUT_MS) {
            compose.onAllNodes(persianSettingsTitle).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(persianSettingsTitle).assertIsDisplayed()
        assertTrue("Menu button should be on the right after switching to Persian", menuIsOnRightHalf())
    }
}
