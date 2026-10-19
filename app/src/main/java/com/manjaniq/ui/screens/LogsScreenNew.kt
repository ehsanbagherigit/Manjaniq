package com.manjaniq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.EngineLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreenNew(
    logs: List<EngineLog>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<EngineLog>>(emptyList()) }
    var filter by remember { mutableStateOf("ALL") } // ALL, INFO, WARN, ERROR
    val shownRaw = if (paused) frozen else logs
    val shown = when (filter) {
        "INFO" -> shownRaw.filter { it.level == "INFO" }
        "WARN" -> shownRaw.filter { it.level == "WARN" }
        "ERROR" -> shownRaw.filter { it.level == "ERROR" }
        "OK" -> shownRaw.filter { it.level == "OK" }
        else -> shownRaw
    }
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(shown.size, paused) {
        if (!paused && shown.isNotEmpty()) {
            listState.animateScrollToItem(shown.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.logs_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.logs_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        // Controls
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (!paused) frozen = logs
                paused = !paused
            }) { Text(if (paused) stringResource(R.string.resume) else stringResource(R.string.pause)) }
            OutlinedButton(onClick = {
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                val text = shown.joinToString("\n") {
                    "${fmt.format(Date(it.timeMs))} [${it.level}] ${it.message}"
                }
                clipboard.setText(AnnotatedString(text))
            }) { Text(stringResource(R.string.copy_logs)) }
            OutlinedButton(onClick = { showClearConfirm = true }) { Text(stringResource(R.string.clear_logs)) }
        }

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == "ALL",
                onClick = { filter = "ALL" },
                label = { Text(stringResource(R.string.filter_all), fontSize = 12.sp) }
            )
            FilterChip(
                selected = filter == "INFO",
                onClick = { filter = "INFO" },
                label = { Text(stringResource(R.string.filter_info), fontSize = 12.sp) }
            )
            FilterChip(
                selected = filter == "WARN",
                onClick = { filter = "WARN" },
                label = { Text("WARN", fontSize = 12.sp) }
            )
            FilterChip(
                selected = filter == "ERROR",
                onClick = { filter = "ERROR" },
                label = { Text(stringResource(R.string.filter_error), fontSize = 12.sp) }
            )
        }

        if (paused) {
            Text(
                stringResource(R.string.logs_paused),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.seq }) { LogLineNew(it) }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_logs_title)) },
            text = { Text(stringResource(R.string.clear_logs_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearLogs()
                    frozen = emptyList()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun LogLineNew(log: EngineLog) {
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
