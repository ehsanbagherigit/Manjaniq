package com.manjaniq.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.manjaniq.R
import com.manjaniq.core.EngineLog
import com.manjaniq.core.EngineMetrics
import com.manjaniq.core.models.EngineState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RstaConfigurationSection(
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.rsta_settings).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )
            Text(
                text = stringResource(R.string.rsta_config_intro),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            // Quick info about where to edit — we keep full settings embedded below
            Text(
                text = stringResource(R.string.rsta_config_intro_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun RstaLiveStatsCard(
    metrics: EngineMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.live_statistics).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )

            StatRow(stringResource(R.string.uptime), formatDuration(metrics.uptimeSeconds))
            StatRow(stringResource(R.string.active_connections), "${metrics.activeConns}")
            StatRow(stringResource(R.string.total_connections), "${metrics.totalConns}")
            StatRow(stringResource(R.string.bypass_confirmed), "${metrics.bypassOK}")
            StatRow(stringResource(R.string.blocked_no_reply), "${metrics.blocked}")
            StatRow(stringResource(R.string.traffic_up), formatBytes(metrics.bytesUp))
            StatRow(stringResource(R.string.traffic_down), formatBytes(metrics.bytesDown))
            Text(
                text = stringResource(R.string.stats_footnote),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun RstaRecentLogsCard(
    recentLogs: List<EngineLog>,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.recent_log).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )

            if (recentLogs.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_events),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                recentLogs.takeLast(5).forEach { LogLine(it) }
            }

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onOpenLogs) { Text(stringResource(R.string.open_full_logs)) }
        }
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

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
