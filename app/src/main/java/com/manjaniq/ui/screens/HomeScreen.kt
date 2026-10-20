package com.manjaniq.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.ui.TestTags
import com.manjaniq.core.AetherLog
import com.manjaniq.core.AetherUiState
import com.manjaniq.core.EngineLog
import com.manjaniq.core.EngineUiState
import com.manjaniq.core.SelectedCore
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherState
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.ui.components.*
import com.manjaniq.ui.theme.AetherCyan
import com.manjaniq.ui.theme.RstaGold

@Composable
fun HomeScreen(
    selectedCore: SelectedCore,
    onSelectCore: (SelectedCore) -> Unit,
    config: RSTAConfig,
    ui: EngineUiState,
    recentLogs: List<EngineLog>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onOpenLogs: () -> Unit,
    onSaveConfig: (RSTAConfig, Boolean) -> Unit,
    onResetDefaults: () -> RSTAConfig,
    // Aether bindings (preserves architecture: same Home, separate core state)
    aetherConfig: AetherConfig,
    aetherUi: AetherUiState,
    aetherLogs: List<AetherLog>,
    onAetherStart: () -> Unit,
    onAetherStop: () -> Unit,
    onAetherDismissError: () -> Unit,
    onAetherSaveConfig: (AetherConfig, Boolean) -> Unit,
    onAetherResetDefaults: () -> AetherConfig,
    modifier: Modifier = Modifier
) {
    val running = ui.state == EngineState.RUNNING
    val transition = ui.state == EngineState.STARTING || ui.state == EngineState.STOPPING

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.HOME_SCREEN)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // B. Core Selector Near Top — prominent
        CoreSelector(
            selected = selectedCore,
            onSelect = onSelectCore
        )

        // C. Core-specific content below selector
        when (selectedCore) {
            SelectedCore.RSTA -> {
                // RSTA live stats — visible when engine has data, but compact
                // Engine error banner
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
                                text = stringResource(R.string.engine_error),
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
                                TextButton(onClick = onOpenLogs) { Text(stringResource(R.string.view_logs)) }
                                TextButton(onClick = onDismissError) { Text(stringResource(R.string.dismiss)) }
                            }
                        }
                    }
                }

                // Start/Stop — clear, accessible, reflects real state
                // Placed near top of core section but below selector per spec: status lower.
                // Here we keep control visible before long config.
                Button(
                    onClick = { if (running) onStop() else onStart() },
                    enabled = !ui.busy && !transition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag(TestTags.ENGINE_TOGGLE),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFFB3261E)
                        else RstaGold,
                        contentColor = if (running) Color.White else Color(0xFF1A1200)
                    )
                ) {
                    if (ui.busy || transition) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = if (running) Color.White else Color(0xFF1A1200)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = when {
                            ui.state == EngineState.STARTING -> stringResource(R.string.starting)
                            ui.state == EngineState.STOPPING -> stringResource(R.string.stopping)
                            running -> stringResource(R.string.stop_engine)
                            else -> stringResource(R.string.start_engine)
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // RSTA settings — full functional form embedded on Home
                RstaSettingsForm(
                    initial = config,
                    engineState = ui.state,
                    onSave = onSaveConfig,
                    onResetDefaults = onResetDefaults
                )

                // Live statistics — compact, real data
                RstaLiveStatsCard(metrics = ui.metrics)

                // Recent logs preview
                RstaRecentLogsCard(recentLogs = recentLogs, onOpenLogs = onOpenLogs)

                // How it works
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.how_it_works).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = stringResource(R.string.how_it_works_body, config.listenPort.toString()),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // D. Compact Connection Status — lower, less prominent
                CompactConnectionStatus(
                    engineState = ui.state,
                    metrics = ui.metrics,
                    listenFallback = "${config.listenHost}:${config.listenPort}",
                    targetFallback = "${config.connectIp}:${config.connectPort}"
                )
            }

            SelectedCore.AETHER -> {
                // Aether functional screen (SOCKS5-only phase, no VpnService)
                val aRunning = aetherUi.state == AetherState.RUNNING
                val aTransition = aetherUi.state == AetherState.STARTING || aetherUi.state == AetherState.STOPPING

                // Header card (keeps branding/logo but now functional)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AetherCyan.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = stringResource(R.string.aether_coming_soon_title), fontWeight = FontWeight.SemiBold, fontSize=15.sp)
                        Text(text = stringResource(R.string.aether_coming_soon_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=12.sp, lineHeight = 16.sp)
                        Text(text = "SOCKS5 ${aetherUi.socksEndpoint} • Port conflict checks vs RSTA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }

                if (aetherUi.lastError != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = stringResource(R.string.aether_error_title), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold, fontSize=14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(text = aetherUi.lastError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize=13.sp)
                            Row { TextButton(onClick = onAetherDismissError) { Text(stringResource(R.string.dismiss)) } }
                        }
                    }
                }

                if (!aetherUi.isNativeLoaded) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha=0.9f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(text = stringResource(R.string.aether_not_native), color = MaterialTheme.colorScheme.onErrorContainer, fontSize=12.sp)
                            if (aetherUi.loadError != null) Text(text = aetherUi.loadError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize=11.sp)
                        }
                    }
                }

                Button(
                    onClick = { if (aRunning) onAetherStop() else onAetherStart() },
                    enabled = aetherUi.isNativeLoaded && !aetherUi.busy && !aTransition,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TestTags.AETHER_TOGGLE),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (aRunning) Color(0xFFB3261E) else AetherCyan,
                        contentColor = if (aRunning) Color.White else Color(0xFF00212A)
                    )
                ) {
                    if (aetherUi.busy || aTransition) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = if (aRunning) Color.White else Color(0xFF00212A))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(text = when {
                        aetherUi.state == AetherState.STARTING -> stringResource(R.string.starting)
                        aetherUi.state == AetherState.STOPPING -> stringResource(R.string.stopping)
                        aRunning -> stringResource(R.string.stop_engine)
                        else -> stringResource(R.string.start_engine)
                    }, fontSize=15.sp, fontWeight=FontWeight.SemiBold, letterSpacing=0.5.sp)
                }

                if (!aetherUi.isNativeLoaded) {
                    Text(text = "Build requires libaether.so (aether-wrapper cdylib). CI builds it; this APK was built without it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp)
                }

                AetherSettingsForm(
                    initial = aetherConfig,
                    aetherState = aetherUi.state,
                    onSave = onAetherSaveConfig,
                    onResetDefaults = onAetherResetDefaults
                )

                AetherStatusCard(
                    state = aetherUi.state,
                    socksEndpoint = aetherUi.socksEndpoint,
                    endpoint = aetherUi.endpoint,
                    version = aetherUi.version,
                    lastError = null, // already shown in banner
                    isNativeLoaded = aetherUi.isNativeLoaded
                )

                // Recent Aether logs (max 8 preview)
                if (aetherLogs.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "Recent Aether logs".uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp, letterSpacing=1.2.sp, fontWeight=FontWeight.Medium)
                            aetherLogs.takeLast(8).forEach { log ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(text = "[${log.level}] ", color = when(log.level){"OK"->MaterialTheme.colorScheme.primary;"ERROR"->MaterialTheme.colorScheme.error; else->AetherCyan}, fontSize=11.sp, fontWeight=FontWeight.Bold, modifier=Modifier.width(48.dp))
                                    Text(text = log.message, color = MaterialTheme.colorScheme.onSurface, fontSize=11.sp, modifier=Modifier.weight(1f))
                                }
                            }
                            TextButton(onClick = { com.manjaniq.core.AetherRepository.clearLogs() }) { Text("Clear logs", fontSize=12.sp) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
