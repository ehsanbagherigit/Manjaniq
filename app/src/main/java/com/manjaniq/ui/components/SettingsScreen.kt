package com.manjaniq.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig

private fun methodDescription(method: String): String = when (method) {
    "fragment" ->
        "Splits the real TLS ClientHello into fragments (chosen strategy below) " +
            "so the SNI is never visible in one packet. No fake traffic is sent."
    "fake_sni" ->
        "As implemented in the genuine engine: sends a fake ClientHello probe " +
            "with the Fake SNI on a side connection, then sends the real hello " +
            "split at the SNI. With the TTL toggle on, the probe uses the " +
            "engine's TTL-trick path. (This build uses regular sockets — no " +
            "raw-packet injection and no root.)"
    "combined" ->
        "Fake-hello probe (when TTL trick is on) plus fragmentation of the " +
            "real ClientHello with your strategy and delay. The original " +
            "program's recommended method."
    else -> ""
}

private fun strategyDescription(strategy: String): String = when (strategy) {
    "sni_split" -> "Split in the middle of the SNI value (recommended)."
    "half" -> "Split the ClientHello record into two halves."
    "multi" -> "Split into many small 24-byte chunks."
    "tls_record_frag" -> "Re-wrap the handshake as two separate TLS records."
    else -> ""
}

/**
 * Full configuration editor. Every value shown here is persisted
 * and passed to the genuine engine on the next (re)start; nothing
 * is silently replaced by hardcoded values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: RSTAConfig,
    engineState: EngineState,
    onSave: (RSTAConfig, restartIfRunning: Boolean) -> Unit,
    onResetDefaults: () -> RSTAConfig
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var listenPortText by remember(initial) { mutableStateOf(initial.listenPort.toString()) }
    var connectPortText by remember(initial) { mutableStateOf(initial.connectPort.toString()) }
    var delayText by remember(initial) { mutableStateOf(initial.fragmentDelay.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }
    var showRestartConfirm by remember { mutableStateOf<RSTAConfig?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var importVisible by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    fun buildDraft(): RSTAConfig? {
        val lp = listenPortText.toIntOrNull()
        val cp = connectPortText.toIntOrNull()
        val delay = delayText.toDoubleOrNull()
        if (lp == null) { errorText = "Listen port must be a number"; return null }
        if (cp == null) { errorText = "Target port must be a number"; return null }
        if (delay == null) { errorText = "Fragment delay must be a number of seconds"; return null }
        val cfg = draft.copy(listenPort = lp, connectPort = cp, fragmentDelay = delay)
        val problem = cfg.validate()
        if (problem != null) { errorText = problem; return null }
        errorText = null
        return cfg
    }

    fun refreshTexts(cfg: RSTAConfig) {
        draft = cfg
        listenPortText = cfg.listenPort.toString()
        connectPortText = cfg.connectPort.toString()
        delayText = cfg.fragmentDelay.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (errorText != null) {
            Text(errorText!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        if (infoText != null) {
            Text(infoText!!, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }

        InfoCard(title = "CONNECTION") {
            ConfigTextField("Listen host", draft.listenHost, "0.0.0.0 listens on all interfaces; 127.0.0.1 is device-only") {
                draft = draft.copy(listenHost = it)
            }
            ConfigTextField("Listen port", listenPortText, null, KeyboardType.Number) { listenPortText = it }
            ConfigTextField("Target host or IP", draft.connectIp, "Every proxied connection is forwarded here") {
                draft = draft.copy(connectIp = it)
            }
            ConfigTextField("Target port", connectPortText, null, KeyboardType.Number) { connectPortText = it }
            ConfigTextField("Fake SNI hostname", draft.sniHostname, "The allowed-looking name DPI should see") {
                draft = draft.copy(sniHostname = it)
            }
        }

        InfoCard(title = "BYPASS METHOD") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RSTAConfig.BYPASS_METHODS.forEach { method ->
                    FilterChip(
                        selected = draft.bypassMethod == method,
                        onClick = { draft = draft.copy(bypassMethod = method) },
                        label = { Text(method) }
                    )
                }
            }
            Text(
                methodDescription(draft.bypassMethod),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            ToggleRow(
                "TTL trick / fake-hello probe",
                "Used by fake_sni and combined, as implemented in the genuine engine",
                draft.useTtlTrick
            ) { draft = draft.copy(useTtlTrick = it) }
        }

        InfoCard(title = "FRAGMENTATION") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RSTAConfig.FRAGMENT_STRATEGIES.forEach { strategy ->
                    FilterChip(
                        selected = draft.fragmentStrategy == strategy,
                        onClick = { draft = draft.copy(fragmentStrategy = strategy) },
                        label = { Text(strategy) }
                    )
                }
            }
            Text(
                strategyDescription(draft.fragmentStrategy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            ConfigTextField(
                "Delay between fragments (seconds, 0–5)",
                delayText, null, KeyboardType.Decimal
            ) { delayText = it }
        }

        InfoCard(title = "OUTPUT") {
            ToggleRow(
                "Connection monitoring",
                "Collect statistics and stream connection events to Logs",
                draft.monitorEnabled
            ) { draft = draft.copy(monitorEnabled = it) }
            ToggleRow(
                "Quiet mode",
                "Suppresses event logging (engine counters still update)",
                draft.quietMode
            ) { draft = draft.copy(quietMode = it) }
        }

        InfoCard(title = "ADVANCED (GENUINE CONFIG KEYS)") {
            ConfigTextField("FAKE_SNI_METHOD", draft.fakeSNIMethod, null) {
                draft = draft.copy(fakeSNIMethod = it)
            }
            ToggleRow(
                "FAKE_SNI_FRAGMENT_REAL",
                "Stored and sent to the engine; the current engine build always fragments the real hello",
                draft.fakeSNIFragmentReal
            ) { draft = draft.copy(fakeSNIFragmentReal = it) }
        }

        Button(
            onClick = {
                val cfg = buildDraft() ?: return@Button
                if (engineState == EngineState.RUNNING) {
                    showRestartConfirm = cfg
                } else {
                    onSave(cfg, false)
                    refreshTexts(cfg)
                    infoText = "Settings saved. They take effect on the next start."
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Save settings") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { buildDraft()?.let { exportText = it.toJson() } },
                modifier = Modifier.weight(1f)
            ) { Text("Export JSON") }
            OutlinedButton(
                onClick = { importVisible = true },
                modifier = Modifier.weight(1f)
            ) { Text("Import JSON") }
        }
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reset to defaults (supplied config.json values)") }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showRestartConfirm != null) {
        val cfg = showRestartConfirm!!
        AlertDialog(
            onDismissRequest = { showRestartConfirm = null },
            title = { Text("Restart engine?") },
            text = {
                Text("The engine is running. Save these settings and restart " +
                    "the engine so they take effect now? Active connections will drop.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(cfg, true)
                    refreshTexts(cfg)
                    infoText = "Settings saved; engine restarting."
                    showRestartConfirm = null
                }) { Text("Save & restart") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSave(cfg, false)
                    refreshTexts(cfg)
                    infoText = "Settings saved; they apply on the next manual start."
                    showRestartConfirm = null
                }) { Text("Save only") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset settings?") },
            text = { Text("Replace your saved configuration with the defaults " +
                "from the supplied config.json? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    refreshTexts(onResetDefaults())
                    infoText = "Defaults restored and saved."
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (exportText != null) {
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text("Configuration JSON") },
            text = {
                Text(
                    exportText!!,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exportText!!))
                    infoText = "Configuration copied to clipboard."
                    exportText = null
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { exportText = null }) { Text("Close") }
            }
        )
    }

    if (importVisible) {
        AlertDialog(
            onDismissRequest = { importVisible = false },
            title = { Text("Import configuration") },
            text = {
                Column {
                    Text(
                        "Paste a genuine RSTA config.json (for example the " +
                            "supplied rsta-source/config.json). It fills this " +
                            "form only — nothing is saved until you tap Save.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = RSTAConfig.fromJson(importText)
                    if (parsed == null) {
                        errorText = "That is not valid JSON"
                    } else {
                        val problem = parsed.validate()
                        if (problem != null) {
                            errorText = "Imported config: $problem"
                        } else {
                            refreshTexts(parsed)
                            infoText = "Imported. Review the values, then tap Save settings."
                        }
                    }
                    importVisible = false
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { importVisible = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    hint: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
        if (hint != null) {
            Text(
                hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
