package com.manjaniq

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.manjaniq.core.EngineLog
import com.manjaniq.ui.components.LogsScreen
import com.manjaniq.ui.theme.ManjaniqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Logs screen rendering against SUPPLIED test data.
 *
 * The sample lines below are fixtures — they are NOT
 * live engine output and the test does not claim they
 * are: it verifies that whatever the engine actually
 * logs is rendered (timestamps, levels, long messages).
 * The live-poll path itself is exercised by the engine
 * instrumentation test against the real native engine.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LogsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val sampleLogs = listOf(
        EngineLog(
            seq = 1,
            timeMs = 1_700_000_000_000L,
            level = "INFO",
            message = "Engine started: 0.0.0.0:40443"
        ),
        EngineLog(
            seq = 2,
            timeMs = 1_700_000_000_100L,
            level = "OK",
            message = "Connection open client=10.0.0.5:51444"
        ),
        EngineLog(
            seq = 3,
            timeMs = 1_700_000_000_200L,
            level = "WARN",
            message = "DPI blocked probe — server did not reply to the fake hello"
        ),
        EngineLog(
            seq = 4,
            timeMs = 1_700_000_000_300L,
            level = "ERROR",
            message = "TLS parse failure: record too short " +
                "this-is-a-deliberately-long-line-to-verify-that-" +
                "long-messages-remain-readable-in-the-log-list-" +
                "without-being-truncated-by-the-rendering-path"
        )
    )

    @Test
    fun emptyStateIsShownWithoutFabricatedMessages() {
        composeRule.setContent {
            ManjaniqTheme { LogsScreen(logs = emptyList(), onClearLogs = {}) }
        }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.logs_empty)
        ).assertIsDisplayed()
    }

    @Test
    fun suppliedLogLinesAreRendered() {
        composeRule.setContent {
            ManjaniqTheme {
                LogsScreen(logs = sampleLogs, onClearLogs = {})
            }
        }
        composeRule.onNodeWithText("Engine started: 0.0.0.0:40443")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Connection open client=10.0.0.5:51444"
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "DPI blocked probe — server did not reply to the fake hello"
        ).assertIsDisplayed()
        // Long messages remain present in full.
        composeRule.onNodeWithText(
            "TLS parse failure: record too short " +
                "this-is-a-deliberately-long-line-to-verify-that-" +
                "long-messages-remain-readable-in-the-log-list-" +
                "without-being-truncated-by-the-rendering-path"
        ).assertIsDisplayed()
    }

    @Test
    fun pauseFreezesTheShownList() {
        composeRule.setContent {
            ManjaniqTheme {
                LogsScreen(logs = sampleLogs, onClearLogs = {})
            }
        }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.pause)
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.logs_paused)
        ).assertIsDisplayed()
        // Frozen data stays visible while paused.
        composeRule.onNodeWithText("Engine started: 0.0.0.0:40443")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.resume)
        ).performClick()
    }
}
