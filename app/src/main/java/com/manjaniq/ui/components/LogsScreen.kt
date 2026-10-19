package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.EngineLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.manjaniq.R

/**
 * Live engine log: genuine monitor events with timestamps and
 * levels, with pause/resume, copy and clear.
 */
@Composable
fun LogsScreen(
    logs: List<EngineLog>,
    onClearLogs: () -> Unit
) {
    var paused by remember { mutableStateOf(false) }
    // While paused the displayed list is frozen even though the
    // engine ring keeps filling; resuming jumps to the live list.
    var frozen by remember { mutableStateOf<List<EngineLog>>(emptyList()) }
    val shown = if (paused) frozen else logs
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(shown.size, paused) {
        if (!paused && shown.isNotEmpty()) {
            listState.animateScrollToItem(shown.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (!paused) frozen = logs
                paused = !paused
            }) { Text(stringResource(if (paused) R.string.resume else R.string.pause)) }
            OutlinedButton(onClick = {
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                val text = shown.joinToString("\n") {
                    "${fmt.format(Date(it.timeMs))} [${it.level}] ${it.message}"
                }
                clipboard.setText(AnnotatedString(text))
            }) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = { showClearConfirm = true }) {
                Text(stringResource(R.string.clear))
            }
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
                    items(shown, key = { it.seq }) { LogLine(it) }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_logs_title)) },
            text = { Text(stringResource(R.string.clear_logs_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearLogs()
                    frozen = emptyList()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
