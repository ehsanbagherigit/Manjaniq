package com.manjaniq

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.manjaniq.core.ConfigStore
import com.manjaniq.core.models.RSTAConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings contract against the REAL configuration layer
 * (SharedPreferences-backed ConfigStore):
 *  - saved values load into the form,
 *  - invalid input is rejected with a visible message,
 *  - valid changes persist and survive reopening,
 *  - reset restores the genuine defaults.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry
        .getInstrumentation().targetContext

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    private fun openSettings() =
        composeRule.onNodeWithText(string(R.string.tab_settings))
            .performClick()

    @Test
    fun defaultsLoadIntoTheForm() {
        openSettings()
        composeRule.onNodeWithTag("field_listen_port")
            .assertTextContains("40443")
        composeRule.onNodeWithTag("field_connect_port")
            .assertTextContains("443")
        composeRule.onNodeWithTag("field_fake_sni")
            .assertTextContains("cdnjs.cloudflare.com")
    }

    @Test
    fun invalidPortIsRejectedWithVisibleMessage() {
        openSettings()
        composeRule.onNodeWithTag("field_listen_port")
            .performTextReplacement("not-a-port")
        composeRule.onNodeWithTag("settings_save_button")
            .performClick()
        composeRule.onNodeWithText(
            string(R.string.error_listen_port_number)
        ).assertIsDisplayed()
        // The invalid value must NOT reach the stored config.
        assertEquals(
            RSTAConfig.DEFAULT.listenPort,
            ConfigStore(context).load().listenPort
        )
    }

    @Test
    fun invalidHostnameIsRejected() {
        openSettings()
        composeRule.onNodeWithTag("field_fake_sni")
            .performTextReplacement("bad host name!")
        composeRule.onNodeWithTag("settings_save_button")
            .performClick()
        composeRule.onNodeWithText("SNI hostname is not a valid hostname")
            .assertIsDisplayed()
    }

    @Test
    fun validChangePersistsAcrossReopen() {
        openSettings()
        composeRule.onNodeWithTag("field_fake_sni")
            .performTextReplacement("example.org")
        composeRule.onNodeWithTag("settings_save_button")
            .performClick()
        composeRule.onNodeWithText(string(R.string.saved_next_start))
            .assertIsDisplayed()

        // The real configuration layer received the value.
        val stored = ConfigStore(context).load()
        assertEquals("example.org", stored.sniHostname)
        val rawJson = context.getSharedPreferences(
            "manjaniq_config", Context.MODE_PRIVATE
        ).getString("rsta_config_json", null)
        assertTrue("stored config must be genuine-format JSON",
            rawJson != null && JSONObject(rawJson).getString("FAKE_SNI") == "example.org")
    }

    @Test
    fun resetRestoresGenuineDefaults() {
        openSettings()
        composeRule.onNodeWithTag("field_listen_port")
            .performTextReplacement("51820")
        composeRule.onNodeWithTag("settings_save_button")
            .performClick()
        // Reset via the dialog.
        composeRule.onNodeWithText(string(R.string.reset_defaults))
            .performClick()
        composeRule.onNodeWithText(string(R.string.reset))
            .performClick()
        composeRule.onNodeWithText(string(R.string.defaults_restored))
            .assertIsDisplayed()
        assertEquals(
            RSTAConfig.DEFAULT,
            ConfigStore(context).load()
        )
    }
}
