// Package com.manjaniq.ui.components provides the main UI composables for the application.
package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.ConfigStore
import com.manjaniq.core.EngineRepository
import kotlinx.coroutines.delay

/**
 * App shell: Dashboard / Settings / Logs tabs over the real engine
 * state. Engine values are polled once a second from the native
 * side while the UI is alive; the engine itself is owned by
 * EngineService and keeps running when this UI is gone.
 */
@Composable
fun ManjaniqApp() {
    val appContext = LocalContext.current.applicationContext
    val store = remember { ConfigStore(appContext) }
    var config by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableStateOf(0) }
    var localError by remember { mutableStateOf<String?>(null) }

    val ui by EngineRepository.uiState.collectAsState()
    val logs by EngineRepository.logs.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            EngineRepository.refresh()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(
                text = "Manjaniq",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "RSTA SNI Spoof — local proxy control",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        TabRow(selectedTabIndex = tab) {
            listOf("Dashboard", "Settings", "Logs").forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> DashboardScreen(
                    ui = ui.copy(lastError = localError ?: ui.lastError),
                    config = config,
                    recentLogs = logs,
                    onStart = {
                        val problem = config.validate()
                        if (problem != null) {
                            localError = problem
                        } else {
                            localError = null
                            EngineRepository.requestStart(appContext)
                        }
                    },
                    onStop = { EngineRepository.requestStop(appContext) },
                    onDismissError = {
                        localError = null
                        EngineRepository.clearError()
                    },
                    onOpenLogs = { tab = 2 }
                )
                1 -> SettingsScreen(
                    initial = config,
                    engineState = ui.state,
                    onSave = { saved, restart ->
                        store.save(saved)
                        config = saved
                        if (restart) EngineRepository.requestRestart(appContext)
                    },
                    onResetDefaults = {
                        val defaults = store.resetToDefaults()
                        config = defaults
                        defaults
                    }
                )
                else -> LogsScreen(
                    logs = logs,
                    onClearLogs = { EngineRepository.clearLogs() }
                )
            }
        }
    }
}
