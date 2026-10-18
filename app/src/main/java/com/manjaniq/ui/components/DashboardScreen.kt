package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Main dashboard: real engine state, the actual listening/target
 * addresses, live genuine-monitor statistics, start/stop, and the
 * most recent engine log lines.
 */
@Composable
fun DashboardScreen(
    ui: EngineUiState,
    config: RSTAConfig,
    recentLogs: List<EngineLog>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val running = ui.state == EngineState.RUNNING
    val transition = ui.state == EngineState.STARTING || ui.state == EngineState.STOPPING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatusCard(
            engineState = ui.state,
            subtitle = when {
                running -> "Listening on ${ui.metrics.listenAddr.ifBlank { "${config.listenHost}:${config.listenPort}" }}"
                ui.state == EngineState.ERROR -> "Last start failed — see the error below and Logs"
                else -> "Local RSTA proxy is not running"
            }
        )

        if (ui.lastError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Engine error",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ui.lastError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = onOpenLogs) { Text("View logs") }
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                }
            }
        }

        Button(
            onClick = { if (running) onStop() else onStart() },
            enabled = !ui.busy && !transition,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) Color(0xFFB3261E)
                else MaterialTheme.colorScheme.primary
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
                    ui.state == EngineState.STARTING -> "STARTING…"
                    ui.state == EngineState.STOPPING -> "STOPPING…"
                    running -> "STOP ENGINE"
                    else -> "START ENGINE"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        InfoCard(title = "CONNECTION") {
            InfoRow("Listen", ui.metrics.listenAddr.ifBlank { "${config.listenHost}:${config.listenPort}" })
            InfoRow("Target", ui.metrics.connectAddr.ifBlank { "${config.connectIp}:${config.connectPort}" })
            InfoRow("Method", config.bypassMethod)
            InfoRow("Fragmentation", "${config.fragmentStrategy} · delay ${config.fragmentDelay}s")
            InfoRow("Fake SNI", config.sniHostname)
            InfoRow("TTL trick", if (config.useTtlTrick) "on" else "off")
            if (ui.metrics.version.isNotBlank()) {
                InfoRow("Engine", "RSTA SNI Spoof v${ui.metrics.version}")
            }
        }

        InfoCard(title = "LIVE STATISTICS") {
            InfoRow("Uptime", formatDuration(ui.metrics.uptimeSeconds))
            InfoRow("Active connections", "${ui.metrics.activeConns}")
            InfoRow("Total connections", "${ui.metrics.totalConns}")
            InfoRow("Bypass confirmed", "${ui.metrics.bypassOK}")
            InfoRow("Blocked / no reply", "${ui.metrics.blocked}")
            InfoRow("Traffic up (client → target)", formatBytes(ui.metrics.bytesUp))
            InfoRow("Traffic down (target → client)", formatBytes(ui.metrics.bytesDown))
            Text(
                text = "Counters come from the engine's own monitor; traffic totals " +
                    "advance in 256 KB steps, as in the original program.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        InfoCard(title = "RECENT LOG") {
            if (recentLogs.isEmpty()) {
                Text(
                    text = "No engine events yet. Start the engine (with monitoring on) " +
                        "and connection events will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                recentLogs.takeLast(8).forEach { LogLine(it) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onOpenLogs) { Text("Open full logs") }
        }

        InfoCard(title = "HOW IT WORKS") {
            Text(
                text = "Manjaniq runs the original RSTA engine as a local proxy. " +
                    "Point the app or browser you want to protect at the listen " +
                    "address above (for this device: 127.0.0.1:${config.listenPort}). " +
                    "It does not redirect all device traffic and needs no root.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
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
fun InfoRow(label: String, value: String) {
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
            fontWeight = FontWeight.Medium
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
