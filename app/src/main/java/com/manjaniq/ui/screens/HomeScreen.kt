package com.manjaniq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.components.CompactConnectionStatus
import com.manjaniq.ui.components.CoreSelector
import com.manjaniq.ui.components.RstaLiveStatsCard
import com.manjaniq.ui.components.RstaRecentLogsCard
import com.manjaniq.ui.server.AetherHomePanel
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.ManjaniqSurface
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
    modifier: Modifier = Modifier,
) {
    val running = ui.state == EngineState.RUNNING
    val transition = ui.state == EngineState.STARTING || ui.state == EngineState.STOPPING

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.HOME_SCREEN)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoreSelector(
            selected = selectedCore,
            onSelect = onSelectCore,
        )

        when (selectedCore) {
            SelectedCore.RSTA -> {
                if (ui.lastError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.engine_error),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ui.lastError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                TextButton(onClick = onOpenLogs) { Text(stringResource(R.string.view_logs)) }
                                TextButton(onClick = onDismissError) { Text(stringResource(R.string.dismiss)) }
                            }
                        }
                    }
                }

                Button(
                    onClick = { if (running) onStop() else onStart() },
                    enabled = !ui.busy && !transition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag(TestTags.ENGINE_TOGGLE),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFFB3261E) else RstaGold,
                        contentColor = if (running) Color.White else Color(0xFF1A1200),
                    ),
                ) {
                    if (ui.busy || transition) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = if (running) Color.White else Color(0xFF1A1200),
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
                        letterSpacing = 0.5.sp,
                    )
                }

                RstaSettingsForm(
                    initial = config,
                    engineState = ui.state,
                    onSave = onSaveConfig,
                    onResetDefaults = onResetDefaults,
                )

                RstaLiveStatsCard(metrics = ui.metrics)
                RstaRecentLogsCard(recentLogs = recentLogs, onOpenLogs = onOpenLogs)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ManjaniqSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManjaniqBorder),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.how_it_works).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = stringResource(R.string.how_it_works_body, config.listenPort.toString()),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                CompactConnectionStatus(
                    engineState = ui.state,
                    metrics = ui.metrics,
                    listenFallback = "${config.listenHost}:${config.listenPort}",
                    targetFallback = "${config.connectIp}:${config.connectPort}",
                )
            }

            SelectedCore.AETHER -> {
                AetherHomePanel()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
