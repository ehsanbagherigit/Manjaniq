package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.ui.TestTags
import com.manjaniq.core.EngineMetrics
import com.manjaniq.core.models.EngineState
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.StatusError
import com.manjaniq.ui.theme.StatusRunning
import com.manjaniq.ui.theme.StatusStarting
import com.manjaniq.ui.theme.StatusStopped

fun statusColor(state: EngineState): Color = when (state) {
    EngineState.RUNNING -> StatusRunning
    EngineState.STARTING, EngineState.STOPPING -> StatusStarting
    EngineState.ERROR -> StatusError
    EngineState.STOPPED -> StatusStopped
}

fun statusLabelRes(state: EngineState): Int = when (state) {
    EngineState.RUNNING -> R.string.status_running
    EngineState.STARTING -> R.string.status_starting
    EngineState.STOPPING -> R.string.status_stopping
    EngineState.STOPPED -> R.string.status_stopped
    EngineState.ERROR -> R.string.status_error
}

@Composable
fun CompactConnectionStatus(
    engineState: EngineState,
    metrics: EngineMetrics,
    listenFallback: String,
    targetFallback: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.CONNECTION_STATUS),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ManjaniqBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.connection_status).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor(engineState), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(statusLabelRes(engineState)),
                        color = statusColor(engineState),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = ManjaniqBorder, thickness = 1.dp)

            // Grid of compact info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusItem(
                    label = stringResource(R.string.listen_address),
                    value = metrics.listenAddr.ifBlank { listenFallback }
                )
                StatusItem(
                    label = stringResource(R.string.target_address),
                    value = metrics.connectAddr.ifBlank { targetFallback }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusItem(
                    label = stringResource(R.string.status_method),
                    value = metrics.method.ifBlank { "—" }
                )
                StatusItem(
                    label = stringResource(R.string.status_active),
                    value = "${metrics.activeConns}"
                )
                StatusItem(
                    label = stringResource(R.string.status_uptime),
                    value = formatDuration(metrics.uptimeSeconds)
                )
            }

            if (metrics.version.isNotBlank()) {
                Text(
                    text = stringResource(R.string.engine_version_fmt, metrics.version),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp
        )
        // Technical values stay LTR even in RTL locale so IPs/ports remain readable
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LegacyStatusCard(engineState: EngineState, subtitle: String) {
    // Kept for compatibility if needed; Compact version is preferred.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.engine_status).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color = statusColor(engineState), shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(statusLabelRes(engineState)),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
}

fun formatBytes(n: Long): String = when {
    n < 1024 -> "${n}B"
    n < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1fKB", n / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2fMB", n / 1024.0 / 1024.0)
}
