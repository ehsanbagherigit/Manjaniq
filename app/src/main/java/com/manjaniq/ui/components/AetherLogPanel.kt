package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.AetherLog
import com.manjaniq.core.models.AetherState
import com.manjaniq.ui.AetherTestTags
import com.manjaniq.ui.theme.AetherCyan
import com.manjaniq.ui.theme.AetherCyanSoft
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.StatusError
import com.manjaniq.ui.theme.StatusStarting
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How many of the newest entries the panel composes; the repository ring keeps
 * [com.manjaniq.core.AetherRepository.MAX_LOG_ENTRIES]. Internal so the UI test
 * can pin the window instead of duplicating the number.
 */
internal const val MAX_RENDERED_LINES = 200

/**
 * The Aether operational log.
 *
 * It shows only what [com.manjaniq.core.AetherRepository] recorded from the
 * Aether workflow — a step starting, succeeding, failing — and nothing else: no
 * RSTA events, no synthesised progress, no simulated success. Entries are
 * redacted at the logging boundary by
 * [com.manjaniq.core.AetherLogSanitizer] before they reach this panel.
 *
 * The panel is collapsible and its body has a bounded height, so a long run
 * neither dominates the screen nor pushes the Aether controls off it.
 */
@Composable
fun AetherLogPanel(
    logs: List<AetherLog>,
    state: AetherState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    expandedInitially: Boolean = true
) {
    var expanded by remember { mutableStateOf(expandedInitially) }
    var problemsOnly by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val shown = remember(logs, problemsOnly) {
        val filtered = if (problemsOnly) {
            logs.filter { it.level == "ERROR" || it.level == "WARN" }
        } else {
            logs
        }
        if (filtered.size > MAX_RENDERED_LINES) filtered.takeLast(MAX_RENDERED_LINES) else filtered
    }

    // Follow the newest entry while the user has not paused. `maxValue` is part
    // of the key so the view still lands on the newest line after the extra
    // entries have been measured, not one entry behind.
    LaunchedEffect(shown.size, scrollState.maxValue, following) {
        if (following && shown.isNotEmpty()) scrollState.scrollTo(scrollState.maxValue)
    }

    // derivedStateOf keeps scrolling cheap: the panel only recomposes when the
    // view actually crosses in or out of the bottom, not on every scrolled pixel.
    val atBottom by remember {
        derivedStateOf { scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue - 24 }
    }
    val lastError = remember(logs) { logs.lastOrNull { it.level == "ERROR" } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AetherTestTags.LOG_PANEL),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManjaniqBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header stays visible when collapsed, so the log is always reachable.
            Card(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AetherTestTags.LOG_TOGGLE),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(stateColor(state), RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.aether_log_title).uppercase(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = headerSummary(logs.size, state),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.testTag(AetherTestTags.LOG_COUNT)
                        )
                    }
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = AetherCyan,
                        fontSize = 10.sp
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = ManjaniqBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !problemsOnly,
                        onClick = { problemsOnly = false },
                        label = { Text(stringResource(R.string.aether_log_filter_all), fontSize = 11.sp) },
                        modifier = Modifier.testTag(AetherTestTags.LOG_FILTER)
                    )
                    FilterChip(
                        selected = problemsOnly,
                        onClick = { problemsOnly = true },
                        label = { Text(stringResource(R.string.aether_log_filter_problems), fontSize = 11.sp) },
                        modifier = Modifier.testTag(AetherTestTags.LOG_FILTER_PROBLEMS)
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(copyText(logs))) },
                        modifier = Modifier.testTag(AetherTestTags.LOG_COPY)
                    ) { Text(stringResource(R.string.aether_log_copy), fontSize = 11.sp) }
                    TextButton(
                        onClick = onClear,
                        enabled = logs.isNotEmpty(),
                        modifier = Modifier.testTag(AetherTestTags.LOG_CLEAR)
                    ) { Text(stringResource(R.string.aether_log_clear), fontSize = 11.sp) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { following = !following }) {
                        Text(
                            text = stringResource(
                                if (following) R.string.aether_log_follow_toggle_on
                                else R.string.aether_log_follow_toggle_off
                            ),
                            color = if (following) AetherCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    if (!atBottom) {
                        TextButton(
                            onClick = {
                                following = true
                                scope.launch { scrollState.scrollTo(scrollState.maxValue) }
                            },
                            modifier = Modifier.testTag(AetherTestTags.LOG_FOLLOW)
                        ) { Text(stringResource(R.string.aether_log_follow), fontSize = 11.sp) }
                    }
                }

                if (lastError != null) {
                    Text(
                        text = stringResource(R.string.aether_log_last_error) + ": " + lastError.message,
                        color = StatusError,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                // Solid black log surface with a bounded height: long output
                // scrolls instead of taking over the screen.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .heightIn(min = 56.dp, max = 220.dp)
                        .background(Color.Black, RoundedCornerShape(10.dp))
                        .testTag(AetherTestTags.LOG_BODY)
                ) {
                    if (shown.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (logs.isEmpty()) R.string.aether_log_empty
                                else R.string.aether_log_no_problems
                            ),
                            color = AetherCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag(AetherTestTags.LOG_EMPTY)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            shown.forEach { entry -> AetherLogLine(entry) }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.aether_log_redacted_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** One log entry: timestamp, level and message on a black background, in Aether cyan. */
@Composable
private fun AetherLogLine(entry: AetherLog) {
    val time = remember(entry.timeMs) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timeMs))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AetherTestTags.logLine(entry.seq))
    ) {
        Text(
            text = time,
            color = AetherCyanSoft,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.level,
            color = levelColor(entry.level),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(42.dp)
        )
        Text(
            text = entry.message,
            color = AetherCyan,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun levelColor(level: String): Color = when (level) {
    "ERROR" -> StatusError
    "WARN" -> StatusStarting
    else -> AetherCyanSoft
}

private fun stateColor(state: AetherState): Color = when (state) {
    AetherState.RUNNING -> AetherCyan
    AetherState.STARTING, AetherState.STOPPING -> StatusStarting
    AetherState.ERROR -> StatusError
    AetherState.STOPPED -> Color.Gray
}

private fun copyText(logs: List<AetherLog>): String {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    return logs.joinToString("\n") { "${fmt.format(Date(it.timeMs))} [${it.level}] ${it.message}" }
}

@Composable
private fun headerSummary(count: Int, state: AetherState): String {
    val stateText = stringResource(
        when (state) {
            AetherState.RUNNING -> R.string.aether_log_state_running
            AetherState.STARTING -> R.string.aether_log_state_starting
            AetherState.STOPPING -> R.string.aether_log_state_stopping
            AetherState.ERROR -> R.string.aether_log_state_error
            AetherState.STOPPED -> R.string.aether_log_state_stopped
        }
    )
    val countText = stringResource(
        if (count == 1) R.string.aether_log_count_one else R.string.aether_log_count,
        count
    )
    return "$stateText · $countText"
}
