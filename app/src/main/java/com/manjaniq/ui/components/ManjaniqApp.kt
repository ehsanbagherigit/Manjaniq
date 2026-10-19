// Package com.manjaniq.ui.components provides the main UI composables for the application.
package com.manjaniq.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.manjaniq.core.ConfigStore
import com.manjaniq.core.EngineRepository
import com.manjaniq.ui.theme.ManjaniqTheme
import kotlinx.coroutines.delay
import com.manjaniq.R

/**
 * App shell: Dashboard / Settings / Logs / About tabs over the real
 * engine state. Engine values are polled once a second from the native
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

    // POST_NOTIFICATIONS is requested when the user actually starts
    // the engine (the only moment a notification can appear), never
    // during cold start: a permission dialog at launch puts the
    // system permission-controller activity on top of MainActivity
    // and hides the Home screen from both the user and automated
    // launch checks.
    var startAfterPermission by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val notificationPermission =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            // The engine runs with or without the permission (the
            // foreground notification is suppressed if denied); the
            // pending start proceeds either way.
            if (!granted) {
                notice = appContext.getString(
                    R.string.notification_permission_denied
                )
            }
            if (startAfterPermission) {
                startAfterPermission = false
                EngineRepository.requestStart(appContext)
            }
        }

    fun startEngine() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val activity = LocalContext.current as? android.app.Activity
            if (activity?.shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == true
            ) {
                notice = appContext.getString(
                    R.string.notification_permission_needed
                )
            }
            startAfterPermission = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            EngineRepository.requestStart(appContext)
        }
    }

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
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.app_tagline),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.tab_dashboard)) }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.tab_settings)) }
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { Text(stringResource(R.string.tab_logs)) }
            )
            Tab(
                selected = tab == 3,
                onClick = { tab = 3 },
                text = { Text(stringResource(R.string.tab_about)) }
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> DashboardScreen(
                    ui = ui.copy(lastError = localError ?: ui.lastError),
                    config = config,
                    recentLogs = logs,
                    notice = notice,
                    onDismissNotice = { notice = null },
                    onStart = {
                        val problem = config.validate()
                        if (problem != null) {
                            localError = problem
                        } else {
                            localError = null
                            startEngine()
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
                2 -> LogsScreen(
                    logs = logs,
                    onClearLogs = { EngineRepository.clearLogs() }
                )
                else -> AboutScreen()
            }
        }
    }
}
