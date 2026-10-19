package com.manjaniq.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.EngineLog
import com.manjaniq.core.EngineUiState
import com.manjaniq.core.SelectedCore
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.ui.components.*
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
    modifier: Modifier = Modifier
) {
    val running = ui.state == EngineState.RUNNING
    val transition = ui.state == EngineState.STARTING || ui.state == EngineState.STOPPING

    Column(
        modifier = modifier
            .fillMaxSize()
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
                        .height(52.dp),
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
                // Genuine Coming Soon — no fake controls
                AetherComingSoonContent()

                // Still show compact status but honest: engine not running for Aether
                // Show disabled status card indicating not available
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.connection_status).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = stringResource(R.string.aether_not_available),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Text(
                            text = "Selecting Aether does not start or affect the RSTA engine. Switch back to RSTA to control the available proxy.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // Disabled start button — clearly not functional for Aether
                Button(
                    onClick = { /* No-op: Aether not integrated */ },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.badge_coming_soon).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
