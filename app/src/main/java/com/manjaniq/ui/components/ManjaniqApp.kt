// Package com.manjaniq.ui.components provides the main UI composables for the application.
package com.manjaniq.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.NativeBridge
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.core.models.EngineState

// ManjaniqApp is the main application composable that displays the engine UI.
@Composable
fun ManjaniqApp() {
    var engineState by remember { mutableStateOf(EngineState.STOPPED) }
    var isStarting by remember { mutableStateOf(false) }
    var bridgeResult by remember { mutableStateOf("Not run yet") }
    var isError by remember { mutableStateOf(false) }
    var currentConfig by remember { mutableStateOf<RSTAConfig?>(RSTAConfig.DEFAULT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Manjaniq",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Control the RSTA SNI Spoof engine",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        StatusCard(engineState = engineState)

        CurrentConfigCard(config = currentConfig)

        EngineControlSection(
            engineState = engineState,
            isStarting = isStarting,
            onStart = {
                isStarting.value = true
                val result = NativeBridge.startEngine(currentConfig ?: RSTAConfig.DEFAULT)
                if (result.isSuccess) {
                    // Confirm the engine's real state from the native side
                    // instead of assuming RUNNING after StartEngine returns.
                    engineState = NativeBridge.getEngineState()
                } else {
                    // Failure: report the error text and the state the
                    // engine actually reports (STOPPED or ERROR). Never
                    // show RUNNING for a failed start.
                    engineState = NativeBridge.getEngineState()
                    bridgeResult = result.errorMessage ?: "Unknown error"
                    isError = true
                }
                isStarting.value = false
            },
            onStop = {
                val result = NativeBridge.stopEngine()
                if (result.isSuccess) {
                    // Confirm the engine's real state from the native side.
                    engineState = NativeBridge.getEngineState()
                } else {
                    engineState = NativeBridge.getEngineState()
                    bridgeResult = result.errorMessage ?: "Unknown error"
                    isError = true
                }
            }
        )

        NativeBridgeTestSection(
            bridgeResult = bridgeResult,
            isError = isError,
            onTestBridge = {
                bridgeResult = NativeBridge.testBridge()
            }
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

// CurrentConfigCard displays the current engine configuration.
@Composable
fun CurrentConfigCard(config: RSTAConfig?) {
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
            text = "CURRENT CONFIGURATION",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (config != null) {
            Text(
                text = "Listen: ${config.listenHost}:${config.listenPort}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = "Connect: ${config.connectIp}:${config.connectPort}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = "SNI: ${config.sniHostname}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        } else {
            Text(
                text = "No configuration available",
                color = Color(0xFFE53935),
                fontSize = 14.sp
            )
        }
    }
}

// EngineControlSection provides controls for starting and stopping the engine.
@Composable
fun EngineControlSection(
    engineState: EngineState,
    isStarting: State<Boolean>,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
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
            text = "ENGINE CONTROLS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStart,
            enabled = engineState == EngineState.STOPPED && !isStarting.value,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color(0xFF39204F),
                disabledContentColor = Color(0xFFC5A4FF)
            )
        ) {
            Text(
                text = if (isStarting.value) "STARTING..." else "START",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onStop,
            enabled = engineState == EngineState.RUNNING || engineState == EngineState.STARTING,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(text = "STOP", fontSize = 16.sp)
        }
    }
}

// NativeBridgeTestSection provides a test for the native bridge.
@Composable
fun NativeBridgeTestSection(
    bridgeResult: String,
    isError: Boolean,
    onTestBridge: () -> Unit
) {
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
            text = "NATIVE BRIDGE TEST",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = bridgeResult,
            color = if (isError) Color.Red else MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onTestBridge,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(text = "Run Native Test")
        }
    }
}
