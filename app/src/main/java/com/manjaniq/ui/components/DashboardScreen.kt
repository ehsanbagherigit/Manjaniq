// Package com.manjaniq.ui.components provides the main UI composables for the application.
package com.manjaniq.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.EngineLog
import com.manjaniq.core.EngineUiState
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.manjaniq.R

/** Brand: Manjaniq purple. */
val ManjaniqPurple = Color(0xFF8250D9)

/** Brand: RSTA engine gold accent. */
val RstaGold = Color(0xFFC9A227)

/** Brand: Aether engine blue accent. */
val AetherBlue = Color(0xFF4FA3E3)

/** Test tags used by the Compose instrumentation tests. */
object DashboardTags {
    const val ENGINE_STATUS = "engine_status_card"
    const val ENGINE_STATE_TEXT = "engine_state_text"
    const val START_BUTTON = "start_engine_button"
    const val SELECTOR_RSTA = "engine_selector_rsta"
    const val SELECTOR_AETHER = "engine_selector_aether"
    const val LISTEN_ADDRESS = "listen_address"
    const val TARGET_ADDRESS = "target_address"
    const val UPTIME = "uptime_value"
    const val ACTIVE_CONNS = "active_conns_value"
    const val TRAFFIC_UP = "traffic_up_value"
    const val TRAFFIC_DOWN = "traffic_down_value"
    const val ERROR_TEXT = "engine_error_text"
}

/**
 * The only engine implemented in this build. The selector is
 * shown so the Aether "Coming Soon" state is visible and honest;
 * Aether is disabled and can never start RSTA.
 */
enum class EngineCore(val id: String) {
    RSTA("rsta"),
    AETHER("aether");

    val available: Boolean get() = this == RSTA
}

/**
 * Main dashboard: engine selector, real engine state, the actual
 * listening/target addresses, live genuine-monitor statistics,
 * start/stop, and the most recent engine log lines.
 */
@Composable
fun DashboardScreen(
    ui: EngineUiState,
    config: RSTAConfig,
    recentLogs: List<EngineLog>,
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onOpenLogs: () -> Unit
) {
    var selectedCore by remember { mutableStateOf(EngineCore.RSTA) }
    val running = ui.state == EngineState.RUNNING
    val transition = ui.state == EngineState.STARTING || ui.state == EngineState.STOPPING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EngineSelector(selectedCore = selectedCore, onSelect = { core ->
            // Aether is unavailable in this build: the control is
            // disabled, so it cannot be selected and can never
            // trigger an RSTA start.
            if (core.available) selectedCore = core
        })

        StatusCard(
            engineState = ui.state,
            subtitle = when {
                running -> stringResource(
                    R.string.listening_on,
                    ui.metrics.listenAddr.ifBlank {
                        "${config.listenHost}:${config.listenPort}"
                    }
                )
                ui.state == EngineState.ERROR ->
                    stringResource(R.string.status_last_start_failed)
                else -> stringResource(R.string.status_not_running)
            }
        )

        if (notice != null) {
            // Non-blocking notice (for example the
            // notification-permission rationale); it is
            // not an engine error and is dismissible.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notice_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = notice,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onDismissNotice) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }

        if (ui.lastError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("engine_error_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.engine_error),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ui.lastError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag(DashboardTags.ERROR_TEXT)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = onOpenLogs) {
                            Text(stringResource(R.string.view_logs))
                        }
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }
        }

        Button(
            onClick = { if (running) onStop() else onStart() },
            enabled = !ui.busy && !transition,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(DashboardTags.START_BUTTON),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) Color(0xFFB3261E)
                else ManjaniqPurple
            )
        ) {
            if (ui.busy || transition) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = when {
                    ui.state == EngineState.STARTING ->
                        stringResource(R.string.starting)
                    ui.state == EngineState.STOPPING ->
                        stringResource(R.string.stopping)
                    running -> stringResource(R.string.stop_engine)
                    else -> stringResource(R.string.start_engine)
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        InfoCard(title = stringResource(R.string.section_connection)) {
            InfoRow(
                stringResource(R.string.label_listen),
                ui.metrics.listenAddr.ifBlank {
                    "${config.listenHost}:${config.listenPort}"
                },
                tag = DashboardTags.LISTEN_ADDRESS
            )
            InfoRow(
                stringResource(R.string.label_target),
                ui.metrics.connectAddr.ifBlank {
                    "${config.connectIp}:${config.connectPort}"
                },
                tag = DashboardTags.TARGET_ADDRESS
            )
            InfoRow(stringResource(R.string.label_method), config.bypassMethod)
            InfoRow(
                stringResource(R.string.label_fragmentation),
                stringResource(
                    R.string.fragmentation_summary,
                    config.fragmentStrategy,
                    config.fragmentDelay
                )
            )
            InfoRow(stringResource(R.string.label_fake_sni), config.sniHostname)
            InfoRow(
                stringResource(R.string.label_ttl_trick),
                stringResource(if (config.useTtlTrick) R.string.on else R.string.off)
            )
            if (ui.metrics.version.isNotBlank()) {
                InfoRow(
                    stringResource(R.string.label_engine),
                    stringResource(R.string.engine_version, ui.metrics.version)
                )
            }
        }

        InfoCard(title = stringResource(R.string.section_statistics)) {
            InfoRow(
                stringResource(R.string.label_uptime),
                formatDuration(ui.metrics.uptimeSeconds),
                tag = DashboardTags.UPTIME
            )
            InfoRow(
                stringResource(R.string.label_active_connections),
                "${ui.metrics.activeConns}",
                tag = DashboardTags.ACTIVE_CONNS
            )
            InfoRow(
                stringResource(R.string.label_total_connections),
                "${ui.metrics.totalConns}"
            )
            InfoRow(
                stringResource(R.string.label_bypass_confirmed),
                "${ui.metrics.bypassOK}"
            )
            InfoRow(
                stringResource(R.string.label_blocked),
                "${ui.metrics.blocked}"
            )
            InfoRow(
                stringResource(R.string.label_traffic_up),
                formatBytes(ui.metrics.bytesUp),
                tag = DashboardTags.TRAFFIC_UP
            )
            InfoRow(
                stringResource(R.string.label_traffic_down),
                formatBytes(ui.metrics.bytesDown),
                tag = DashboardTags.TRAFFIC_DOWN
            )
            Text(
                text = stringResource(R.string.stats_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        InfoCard(title = stringResource(R.string.section_recent_log)) {
            if (recentLogs.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_engine_events),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                recentLogs.takeLast(8).forEach { LogLine(it) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onOpenLogs) {
                Text(stringResource(R.string.open_full_logs))
            }
        }

        InfoCard(title = stringResource(R.string.section_how_it_works)) {
            Text(
                text = stringResource(R.string.how_it_works, config.listenPort),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Core selector: RSTA is implemented and selectable; Aether is
 * honestly marked "Coming Soon" and disabled. Selecting Aether
 * is impossible, so it can never start or affect RSTA.
 */
@Composable
fun EngineSelector(
    selectedCore: EngineCore,
    onSelect: (EngineCore) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.engine_selector_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        CoreChip(
            name = stringResource(R.string.engine_rsta),
            status = stringResource(R.string.engine_rsta_status_available),
            accent = RstaGold,
            logo = painterResource(R.drawable.ic_rsta_logo),
            selected = selectedCore == EngineCore.RSTA,
            enabled = true,
            tag = DashboardTags.SELECTOR_RSTA,
            onClick = { onSelect(EngineCore.RSTA) }
        )
        CoreChip(
            name = stringResource(R.string.engine_aether),
            status = stringResource(R.string.engine_aether_status_coming_soon),
            accent = AetherBlue,
            logo = painterResource(R.drawable.ic_aether_logo),
            selected = false,
            enabled = false,
            tag = DashboardTags.SELECTOR_AETHER,
            onClick = {}
        )
        }
    }
}

@Composable
private fun CoreChip(
    name: String,
    status: String,
    accent: Color,
    logo: Painter,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val border = if (selected) accent
    else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier
            .weight(1f)
            // A disabled core (Aether, Coming Soon) is not
            // clickable and can never trigger an engine start.
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = logo,
                    contentDescription = name,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = name,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        content()
    }
}

@Composable
fun InfoRow(label: String, value: String, tag: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = tag?.let { Modifier.testTag(it) } ?: Modifier
        )
    }
}

@Composable
fun LogLine(log: EngineLog) {
    val time = remember(log.timeMs) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timeMs))
    }
    val levelColor = when (log.level) {
        "OK" -> Color(0xFF4CAF50)
        "WARN" -> Color(0xFFFFB300)
        "ERROR" -> Color(0xFFE53935)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = "$time [${log.level}] ${log.message}",
        color = levelColor,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
}

fun formatBytes(n: Long): String = when {
    n < 1024 -> "${n}B"
    n < 1024 * 1024 -> String.format(Locale.US, "%.1fKB", n / 1024.0)
    else -> String.format(Locale.US, "%.2fMB", n / 1024.0 / 1024.0)
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
}
