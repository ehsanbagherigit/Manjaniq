package com.manjaniq.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherState
import com.manjaniq.ui.TestTags

@Composable
fun AetherSettingsForm(
    initial: AetherConfig,
    aetherState: AetherState,
    onSave: (AetherConfig, Boolean) -> Unit,
    onResetDefaults: () -> AetherConfig
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }
    var showRestartConfirm by remember { mutableStateOf<AetherConfig?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var importVisible by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    val clipboard = LocalClipboardManager.current

    fun buildDraft(): AetherConfig? {
        val v = draft.validate()
        if (v != null) { errorText = v; return null }
        return draft
    }

    Card(
        // TestTags.AETHER_SETTINGS was declared for this form but never applied.
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.AETHER_SETTINGS),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.aether_settings).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
            Text(text = stringResource(R.string.aether_settings_intro),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)

            if (errorText != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = errorText ?: "", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
            if (infoText != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Text(text = infoText ?: "", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { infoText = null }) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }

            // Transport selector (chips)
            Text(text = stringResource(R.string.aether_transport), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("masque" to R.string.aether_transport_masque, "wg" to R.string.aether_transport_wg, "gool" to R.string.aether_transport_gool, "mim" to R.string.aether_transport_mim).forEach { (id,res) ->
                    FilterChip(selected = draft.transport==id, onClick = { draft=draft.copy(transport=id); errorText=null }, label = { Text(stringResource(res), fontSize = 12.sp) })
                }
            }
            Text(text = stringResource(R.string.aether_transport_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

            // Scan mode
            Text(text = stringResource(R.string.aether_scan_mode), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("turbo","balanced","thorough","verified","ironclad").forEach { mode ->
                    val selected = draft.scanMode==mode
                    FilterChip(selected = selected, onClick = { draft=draft.copy(scanMode=mode) }, label = { Text(mode, fontSize = 11.sp) })
                }
            }

            // Noize
            Text(text = stringResource(R.string.aether_noize), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("firewall","balanced","gfw","aggressive","off").forEach { p ->
                    FilterChip(selected = draft.noizeProfile==p, onClick = { draft=draft.copy(noizeProfile=p) }, label = { Text(p, fontSize=11.sp) })
                }
            }

            // SOCKS bind
            OutlinedTextField(value = draft.socksBind, onValueChange = { draft=draft.copy(socksBind=it); errorText=null },
                label = { Text(stringResource(R.string.aether_socks_bind)) }, supportingText = { Text(stringResource(R.string.aether_socks_hint), fontSize=11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))

            // HTTP bind
            OutlinedTextField(value = draft.httpProxyBind, onValueChange = { draft=draft.copy(httpProxyBind=it) },
                label = { Text(stringResource(R.string.aether_http_bind)) }, supportingText = { Text(stringResource(R.string.aether_http_hint), fontSize=11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Peer
            OutlinedTextField(value = draft.peer, onValueChange = { draft=draft.copy(peer=it) },
                label = { Text(stringResource(R.string.aether_peer)) }, supportingText = { Text(stringResource(R.string.aether_peer_hint), fontSize=11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Upstream
            OutlinedTextField(value = draft.upstream, onValueChange = { draft=draft.copy(upstream=it) },
                label = { Text(stringResource(R.string.aether_upstream)) }, supportingText = { Text(stringResource(R.string.aether_upstream_hint), fontSize=11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            // DNS
            OutlinedTextField(value = draft.dns, onValueChange = { draft=draft.copy(dns=it) },
                label = { Text(stringResource(R.string.aether_dns)) }, supportingText = { Text(stringResource(R.string.aether_dns_hint), fontSize=11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Identity path
            OutlinedTextField(value = draft.identityPath, onValueChange = { draft=draft.copy(identityPath=it) },
                label = { Text(stringResource(R.string.aether_identity_path)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Team
            OutlinedTextField(value = draft.teamName, onValueChange = { draft=draft.copy(teamName=it) },
                label = { Text(stringResource(R.string.aether_team)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // ECH & Quick reconnect
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = draft.echEnabled, onCheckedChange = { draft=draft.copy(echEnabled=it) })
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(text = stringResource(R.string.aether_ech), fontWeight = FontWeight.Medium, fontSize=13.sp)
                    Text(text = stringResource(R.string.aether_ech_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = draft.quickReconnect, onCheckedChange = { draft=draft.copy(quickReconnect=it) })
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(text = stringResource(R.string.aether_quick_reconnect), fontWeight = FontWeight.Medium, fontSize=13.sp)
                    Text(text = stringResource(R.string.aether_quick_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp)
                }
            }

            // Save/Export/Import/Reset actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    val built = buildDraft() ?: return@Button
                    val wantsRestart = aetherState == AetherState.RUNNING
                    if (wantsRestart) showRestartConfirm = built else { onSave(built,false); infoText = "Aether settings saved."; errorText=null }
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save_settings), fontSize=13.sp) }
                OutlinedButton(onClick = { exportText = draft.toJson() }) { Text(stringResource(R.string.export_json), fontSize=12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { importVisible = true; importText = draft.toJson() }) { Text(stringResource(R.string.import_json), fontSize=12.sp) }
                OutlinedButton(onClick = { showResetConfirm = true }) { Text(stringResource(R.string.reset_defaults), fontSize=12.sp) }
            }

            // Export dialog
            if (exportText != null) {
                AlertDialog(onDismissRequest = { exportText = null }, title = { Text(stringResource(R.string.aether_export_title)) },
                    text = { Text(exportText ?: "", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize=11.sp) },
                    confirmButton = {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(exportText ?: "")); exportText=null; infoText="Copied to clipboard" }) { Text(stringResource(R.string.copy)) }
                    }, dismissButton = { TextButton(onClick = { exportText=null }) { Text(stringResource(R.string.close)) } })
            }
            if (importVisible) {
                AlertDialog(onDismissRequest = { importVisible=false }, title = { Text(stringResource(R.string.aether_import_title)) },
                    text = {
                        OutlinedTextField(value = importText, onValueChange = { importText=it }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 12)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val parsed = com.manjaniq.core.models.AetherConfig.fromJson(importText)
                            if (parsed==null) errorText="Invalid JSON" else { draft=parsed; infoText="Imported - review then Save"; importVisible=false }
                        }) { Text(stringResource(R.string.import_action)) }
                    }, dismissButton = { TextButton(onClick = { importVisible=false }) { Text(stringResource(R.string.cancel)) } })
            }
            if (showRestartConfirm != null) {
                AlertDialog(onDismissRequest = { showRestartConfirm=null }, title = { Text(stringResource(R.string.restart_engine_title)) },
                    text = { Text("Aether is running. Save and restart to apply new settings?") },
                    confirmButton = {
                        Button(onClick = { val c=showRestartConfirm!!; showRestartConfirm=null; onSave(c,true); infoText="Saved + restarting"; errorText=null }) { Text(stringResource(R.string.save_and_restart)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { val c=showRestartConfirm!!; showRestartConfirm=null; onSave(c,false); infoText="Saved; applies on next start" }) { Text(stringResource(R.string.save_only)) }
                    })
            }
            if (showResetConfirm) {
                AlertDialog(onDismissRequest = { showResetConfirm=false }, title = { Text(stringResource(R.string.reset_title)) },
                    text = { Text("Replace Aether config with defaults?") },
                    confirmButton = {
                        Button(onClick = { val d=onResetDefaults(); draft=d; infoText="Defaults restored"; errorText=null; showResetConfirm=false }) { Text(stringResource(R.string.reset)) }
                    },
                    dismissButton = { TextButton(onClick = { showResetConfirm=false }) { Text(stringResource(R.string.cancel)) } })
            }
        }
    }
}
