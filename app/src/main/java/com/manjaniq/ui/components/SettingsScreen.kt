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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.R

private fun methodDescription(method: String): Int = when (method) {
    "fragment" -> R.string.method_fragment_desc
    "fake_sni" -> R.string.method_fake_sni_desc
    "combined" -> R.string.method_combined_desc
    else -> R.string.method_fragment_desc
}

private fun strategyDescription(strategy: String): Int = when (strategy) {
    "sni_split" -> R.string.strategy_sni_split_desc
    "half" -> R.string.strategy_half_desc
    "multi" -> R.string.strategy_multi_desc
    "tls_record_frag" -> R.string.strategy_tls_record_frag_desc
    else -> R.string.strategy_sni_split_desc
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
    val appContext = LocalContext.current

    // Resolved in composable scope; plain callbacks below
    // must not call @Composable functions themselves.
    val errPortNumber = stringResource(R.string.error_listen_port_number)
    val errConnectPortNumber = stringResource(R.string.error_connect_port_number)
    val errDelayNumber = stringResource(R.string.error_delay_number)
    val savedNextStart = stringResource(R.string.saved_next_start)
    val savedRestarting = stringResource(R.string.saved_restarting)
    val savedSaveOnly = stringResource(R.string.saved_save_only)
    val defaultsRestored = stringResource(R.string.defaults_restored)
    val configCopied = stringResource(R.string.config_copied)
    val errorInvalidJson = stringResource(R.string.error_invalid_json)
    val importedReview = stringResource(R.string.imported_review)

    fun buildDraft(): RSTAConfig? {
        val lp = listenPortText.toIntOrNull()
        val cp = connectPortText.toIntOrNull()
        val delay = delayText.toDoubleOrNull()
        if (lp == null) { errorText = errPortNumber; return null }
        if (cp == null) { errorText = errConnectPortNumber; return null }
        if (delay == null) { errorText = errDelayNumber; return null }
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

        InfoCard(title = stringResource(R.string.section_connection)) {
            ConfigTextField(
                stringResource(R.string.settings_listen_host),
                draft.listenHost,
                stringResource(R.string.settings_listen_host_hint),
                tag = "field_listen_host"
            ) {
                draft = draft.copy(listenHost = it)
            }
            ConfigTextField(
                stringResource(R.string.settings_listen_port),
                listenPortText,
                null,
                KeyboardType.Number,
                tag = "field_listen_port"
            ) { listenPortText = it }
            ConfigTextField(
                stringResource(R.string.settings_connect_host),
                draft.connectIp,
                stringResource(R.string.settings_connect_host_hint),
                tag = "field_connect_host"
            ) {
                draft = draft.copy(connectIp = it)
            }
            ConfigTextField(
                stringResource(R.string.settings_connect_port),
                connectPortText,
                null,
                KeyboardType.Number,
                tag = "field_connect_port"
            ) { connectPortText = it }
            ConfigTextField(
                stringResource(R.string.settings_fake_sni),
                draft.sniHostname,
                stringResource(R.string.settings_fake_sni_hint),
                tag = "field_fake_sni"
            ) {
                draft = draft.copy(sniHostname = it)
            }
        }

        InfoCard(title = stringResource(R.string.section_bypass_method)) {
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
                stringResource(methodDescription(draft.bypassMethod)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            ToggleRow(
                stringResource(R.string.settings_ttl_trick),
                stringResource(R.string.settings_ttl_trick_hint),
                draft.useTtlTrick
            ) { draft = draft.copy(useTtlTrick = it) }
        }

        InfoCard(title = stringResource(R.string.section_fragmentation)) {
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
                stringResource(strategyDescription(draft.fragmentStrategy)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            ConfigTextField(
                stringResource(R.string.settings_fragment_delay),
                delayText,
                null,
                KeyboardType.Decimal,
                tag = "field_fragment_delay"
            ) { delayText = it }
        }

        InfoCard(title = stringResource(R.string.section_output)) {
            ToggleRow(
                stringResource(R.string.settings_monitor),
                stringResource(R.string.settings_monitor_hint),
                draft.monitorEnabled
            ) { draft = draft.copy(monitorEnabled = it) }
            ToggleRow(
                stringResource(R.string.settings_quiet),
                stringResource(R.string.settings_quiet_hint),
                draft.quietMode
            ) { draft = draft.copy(quietMode = it) }
        }

        InfoCard(title = stringResource(R.string.section_advanced)) {
            ConfigTextField(
                stringResource(R.string.settings_fake_sni_method),
                draft.fakeSNIMethod,
                null,
                tag = "field_fake_sni_method"
            ) {
                draft = draft.copy(fakeSNIMethod = it)
            }
            ToggleRow(
                stringResource(R.string.settings_fake_sni_fragment_real),
                stringResource(R.string.settings_fake_sni_fragment_real_hint),
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
                    infoText = savedNextStart
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("settings_save_button"),
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

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showRestartConfirm != null) {
        val cfg = showRestartConfirm!!
        AlertDialog(
            onDismissRequest = { showRestartConfirm = null },
            title = { Text(stringResource(R.string.restart_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.restart_confirm_text)
                )
            },
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
                    infoText = savedSaveOnly
                    showRestartConfirm = null
                }) { Text(stringResource(R.string.save_only)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = {
                Text(stringResource(R.string.reset_confirm_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    refreshTexts(onResetDefaults())
                    infoText = defaultsRestored
                    showResetConfirm = false
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (exportText != null) {
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text(stringResource(R.string.config_json)) },
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
                    infoText = configCopied
                    exportText = null
                }) { Text(stringResource(R.string.copy)) }
            },
            dismissButton = {
                TextButton(onClick = { exportText = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (importVisible) {
        AlertDialog(
            onDismissRequest = { importVisible = false },
            title = { Text(stringResource(R.string.import_config_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.import_config_hint),
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
                        errorText = errorInvalidJson
                    } else {
                        val problem = parsed.validate()
                        if (problem != null) {
                            errorText = appContext.getString(
                                R.string.error_imported_config,
                                problem
                            )
                        } else {
                            refreshTexts(parsed)
                            infoText = importedReview
                        }
                    }
                    importVisible = false
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { importVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
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
    tag: String? = null,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = tag
                ?.let { Modifier.fillMaxWidth().testTag(it) }
                ?: Modifier.fillMaxWidth()
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
