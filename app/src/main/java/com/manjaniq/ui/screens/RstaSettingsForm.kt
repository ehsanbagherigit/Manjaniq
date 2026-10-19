package com.manjaniq.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import com.manjaniq.R
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig

@Composable
private fun methodDescription(method: String): String = when (method) {
    "fragment" -> stringResource(R.string.method_fragment_desc)
    "fake_sni" -> stringResource(R.string.method_fake_sni_desc)
    "combined" -> stringResource(R.string.method_combined_desc)
    else -> ""
}

@Composable
private fun strategyDescription(strategy: String): String = when (strategy) {
    "sni_split" -> stringResource(R.string.strategy_sni_split_desc)
    "half" -> stringResource(R.string.strategy_half_desc)
    "multi" -> stringResource(R.string.strategy_multi_desc)
    "tls_record_frag" -> stringResource(R.string.strategy_tls_record_frag_desc)
    else -> ""
}

@Composable
fun RstaSettingsForm(
    initial: RSTAConfig,
    engineState: EngineState,
    onSave: (RSTAConfig, Boolean) -> Unit,
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
    val context = LocalContext.current

    // Pre-resolve strings for use in non-composable helpers
    val listenPortError = stringResource(R.string.listen_port_number)
    val targetPortError = stringResource(R.string.target_port_number)
    val delayError = stringResource(R.string.fragment_delay_number)
    val savedNextManual = stringResource(R.string.settings_saved_next)
    val savedRestarting = stringResource(R.string.settings_saved_restart)
    val defaultsRestored = stringResource(R.string.defaults_restored)
    val copied = stringResource(R.string.config_copied)
    val invalidJson = stringResource(R.string.invalid_json)
    val importedReview = stringResource(R.string.imported_review)

    fun buildDraft(): RSTAConfig? {
        val lp = listenPortText.toIntOrNull()
        val cp = connectPortText.toIntOrNull()
        val delay = delayText.toDoubleOrNull()
        if (lp == null) { errorText = listenPortError; return null }
        if (cp == null) { errorText = targetPortError; return null }
        if (delay == null) { errorText = delayError; return null }
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (errorText != null) {
            Text(errorText!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        if (infoText != null) {
            Text(infoText!!, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }

        InfoCardLikeHelper(title = stringResource(R.string.connection).uppercase()) {
            ConfigTextField(stringResource(R.string.listen_host), draft.listenHost, stringResource(R.string.listen_host_hint)) {
                draft = draft.copy(listenHost = it)
            }
            ConfigTextField(stringResource(R.string.listen_port), listenPortText, null, KeyboardType.Number) { listenPortText = it }
            ConfigTextField(stringResource(R.string.target_host), draft.connectIp, stringResource(R.string.target_host_hint)) {
                draft = draft.copy(connectIp = it)
            }
            ConfigTextField(stringResource(R.string.target_port), connectPortText, null, KeyboardType.Number) { connectPortText = it }
            ConfigTextField(stringResource(R.string.fake_sni), draft.sniHostname, stringResource(R.string.fake_sni_hint)) {
                draft = draft.copy(sniHostname = it)
            }
        }

        InfoCardLikeHelper(title = stringResource(R.string.bypass_method).uppercase()) {
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
                stringResource(R.string.ttl_trick),
                stringResource(R.string.ttl_trick_desc),
                draft.useTtlTrick
            ) { draft = draft.copy(useTtlTrick = it) }
        }

        InfoCardLikeHelper(title = stringResource(R.string.fragmentation).uppercase()) {
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
                stringResource(R.string.fragment_delay),
                delayText, null, KeyboardType.Decimal
            ) { delayText = it }
        }

        InfoCardLikeHelper(title = stringResource(R.string.output).uppercase()) {
            ToggleRow(
                stringResource(R.string.monitor_enabled),
                stringResource(R.string.monitor_enabled_desc),
                draft.monitorEnabled
            ) { draft = draft.copy(monitorEnabled = it) }
            ToggleRow(
                stringResource(R.string.quiet_mode),
                stringResource(R.string.quiet_mode_desc),
                draft.quietMode
            ) { draft = draft.copy(quietMode = it) }
        }

        InfoCardLikeHelper(title = stringResource(R.string.advanced).uppercase()) {
            ConfigTextField("FAKE_SNI_METHOD", draft.fakeSNIMethod, null) {
                draft = draft.copy(fakeSNIMethod = it)
            }
            ToggleRow(
                "FAKE_SNI_FRAGMENT_REAL",
                stringResource(R.string.fake_sni_fragment_real_desc),
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
                    infoText = savedNextManual
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(stringResource(R.string.save_settings)) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { buildDraft()?.let { exportText = it.toJson() } },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.export_json)) }
            OutlinedButton(
                onClick = { importVisible = true },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.import_json)) }
        }
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.reset_defaults)) }
    }

    if (showRestartConfirm != null) {
        val cfg = showRestartConfirm!!
        AlertDialog(
            onDismissRequest = { showRestartConfirm = null },
            title = { Text(stringResource(R.string.restart_engine_title)) },
            text = { Text(stringResource(R.string.restart_engine_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    onSave(cfg, true)
                    refreshTexts(cfg)
                    infoText = savedRestarting
                    showRestartConfirm = null
                }) { Text(stringResource(R.string.save_and_restart)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSave(cfg, false)
                    refreshTexts(cfg)
                    infoText = savedNextManual
                    showRestartConfirm = null
                }) { Text(stringResource(R.string.save_only)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    refreshTexts(onResetDefaults())
                    infoText = defaultsRestored
                    showResetConfirm = false
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (exportText != null) {
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text(stringResource(R.string.config_json_title)) },
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
                    infoText = copied
                    exportText = null
                }) { Text(stringResource(R.string.copy)) }
            },
            dismissButton = {
                TextButton(onClick = { exportText = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (importVisible) {
        AlertDialog(
            onDismissRequest = { importVisible = false },
            title = { Text(stringResource(R.string.import_config_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_config_msg), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = RSTAConfig.fromJson(importText)
                    if (parsed == null) {
                        errorText = invalidJson
                    } else {
                        val problem = parsed.validate()
                        if (problem != null) {
                            errorText = context.getString(R.string.imported_config_error, problem)
                        } else {
                            refreshTexts(parsed)
                            infoText = importedReview
                        }
                    }
                    importVisible = false
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { importVisible = false }) { Text(stringResource(R.string.cancel)) }
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

@Composable
fun InfoCardLikeHelper(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp
        )
        content()
    }
}
