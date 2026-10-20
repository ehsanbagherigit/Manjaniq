package com.manjaniq.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.AetherLog
import com.manjaniq.core.AetherUiState
import com.manjaniq.core.EngineLog
import com.manjaniq.core.EngineUiState
import com.manjaniq.core.SelectedCore
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig
import com.manjaniq.core.models.AetherState
import com.manjaniq.core.models.EngineState
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.components.*
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.ManjaniqPurple
import com.manjaniq.ui.theme.ManjaniqPurpleDeep
import com.manjaniq.ui.theme.ManjaniqPurpleNeon
import com.manjaniq.ui.theme.ManjaniqSurface
import com.manjaniq.ui.theme.RstaGold

// Neon blue for Aether Start only
private val NeonBlue = Color(0xFF00D5FF)

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
    // Aether single-config legacy (kept for compat, delegates to profiles)
    aetherConfig: AetherConfig,
    aetherUi: AetherUiState,
    aetherLogs: List<AetherLog>,
    onAetherStart: () -> Unit,
    onAetherStop: () -> Unit,
    onAetherDismissError: () -> Unit,
    onAetherSaveConfig: (AetherConfig, Boolean) -> Unit,
    onAetherResetDefaults: () -> AetherConfig,
    // New profile-aware bindings (null = use legacy single config)
    selectedAetherProfile: AetherProfile? = null,
    onSelectAetherProfile: ((AetherProfile) -> Unit)? = null,
    aetherProfileConfig: AetherProfileConfig? = null,
    onSaveAetherProfileConfig: ((AetherProfileConfig, Boolean) -> Unit)? = null,
    onResetAetherProfileDefaults: ((AetherProfile) -> AetherProfileConfig)? = null,
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
        CoreSelector(
            selected = selectedCore,
            onSelect = onSelectCore
        )

        when (selectedCore) {
            SelectedCore.RSTA -> {
                if (ui.lastError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.engine_error),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = ui.lastError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
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
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TestTags.ENGINE_TOGGLE),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFFB3261E) else RstaGold,
                        contentColor = if (running) Color.White else Color(0xFF1A1200)
                    )
                ) {
                    if (ui.busy || transition) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = if (running) Color.White else Color(0xFF1A1200))
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = when {
                            ui.state == EngineState.STARTING -> stringResource(R.string.starting)
                            ui.state == EngineState.STOPPING -> stringResource(R.string.stopping)
                            running -> stringResource(R.string.stop_engine)
                            else -> stringResource(R.string.start_engine)
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
                    )
                }

                RstaSettingsForm(
                    initial = config,
                    engineState = ui.state,
                    onSave = onSaveConfig,
                    onResetDefaults = onResetDefaults
                )

                RstaLiveStatsCard(metrics = ui.metrics)
                RstaRecentLogsCard(recentLogs = recentLogs, onOpenLogs = onOpenLogs)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ManjaniqSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManjaniqBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.how_it_works).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp
                        )
                        Text(
                            text = stringResource(R.string.how_it_works_body, config.listenPort.toString()),
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 18.sp
                        )
                    }
                }

                CompactConnectionStatus(
                    engineState = ui.state,
                    metrics = ui.metrics,
                    listenFallback = "${config.listenHost}:${config.listenPort}",
                    targetFallback = "${config.connectIp}:${config.connectPort}"
                )
            }

            SelectedCore.AETHER -> {
                val aRunning = aetherUi.state == AetherState.RUNNING
                val aTransition = aetherUi.state == AetherState.STARTING || aetherUi.state == AetherState.STOPPING

                // VPN limitation notice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ManjaniqPurpleDeep),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManjaniqPurple.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = stringResource(R.string.aether_coming_soon_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = stringResource(R.string.aether_coming_soon_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
                        Text(text = stringResource(R.string.profile_socks_note, aetherUi.socksEndpoint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        HorizontalDivider(color = ManjaniqBorder)
                        Text(text = stringResource(R.string.vpn_limitation_title), color = ManjaniqPurpleNeon, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(text = stringResource(R.string.vpn_limitation_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }

                if (aetherUi.lastError != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = stringResource(R.string.aether_error_title), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(text = aetherUi.lastError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                            Row { TextButton(onClick = onAetherDismissError) { Text(stringResource(R.string.dismiss)) } }
                        }
                    }
                }

                if (!aetherUi.isNativeLoaded) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(text = stringResource(R.string.aether_not_native), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                            if (aetherUi.loadError != null) Text(text = aetherUi.loadError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp)
                        }
                    }
                }

                // Profiles UI - if profile mode available, show cards; else fallback to old single form
                if (selectedAetherProfile != null && onSelectAetherProfile != null) {
                    ProfilesSection(
                        selectedProfile = selectedAetherProfile,
                        onSelectProfile = onSelectAetherProfile,
                        profileConfig = aetherProfileConfig ?: AetherProfileConfig(profileId = selectedAetherProfile.id, base = aetherConfig),
                        aetherState = aetherUi.state,
                        onSaveProfile = onSaveAetherProfileConfig,
                        onResetProfile = onResetAetherProfileDefaults
                    )
                } else {
                    // Legacy single settings form (fallback)
                    Text(text = stringResource(R.string.aether_profiles_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                }

                // Start/Stop - neon blue for Start, purple for Stop per theme spec
                Button(
                    onClick = { if (aRunning) onAetherStop() else onAetherStart() },
                    enabled = aetherUi.isNativeLoaded && !aetherUi.busy && !aTransition,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TestTags.AETHER_TOGGLE),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (aRunning) ManjaniqPurple else NeonBlue,
                        contentColor = if (aRunning) Color.White else Color(0xFF00212A)
                    )
                ) {
                    if (aetherUi.busy || aTransition) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = if (aRunning) Color.White else Color(0xFF00212A))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = when {
                            aetherUi.state == AetherState.STARTING -> stringResource(R.string.starting)
                            aetherUi.state == AetherState.STOPPING -> stringResource(R.string.stopping)
                            aRunning -> stringResource(R.string.stop_engine)
                            else -> stringResource(R.string.start_engine)
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
                    )
                }

                if (!aetherUi.isNativeLoaded) {
                    Text(text = "Build requires libaether.so (aether-wrapper cdylib). CI builds it; this APK was built without it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }

                // Keep per-profile settings form if profile mode, else old form
                if (selectedAetherProfile == null) {
                    AetherSettingsForm(
                        initial = aetherConfig,
                        aetherState = aetherUi.state,
                        onSave = onAetherSaveConfig,
                        onResetDefaults = onAetherResetDefaults
                    )
                }

                AetherStatusCard(
                    state = aetherUi.state,
                    socksEndpoint = aetherUi.socksEndpoint,
                    endpoint = aetherUi.endpoint,
                    version = aetherUi.version,
                    lastError = null,
                    isNativeLoaded = aetherUi.isNativeLoaded
                )

                if (aetherLogs.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ManjaniqSurface)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "Recent Aether logs".uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
                            aetherLogs.takeLast(8).forEach { log ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "[${log.level}] ",
                                        color = when (log.level) { "OK" -> ManjaniqPurpleNeon; "ERROR" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp)
                                    )
                                    Text(text = log.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                }
                            }
                            TextButton(onClick = { com.manjaniq.core.AetherRepository.clearLogs() }) { Text("Clear logs", fontSize = 12.sp, color = ManjaniqPurpleNeon) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ProfilesSection(
    selectedProfile: AetherProfile,
    onSelectProfile: (AetherProfile) -> Unit,
    profileConfig: AetherProfileConfig,
    aetherState: AetherState,
    onSaveProfile: ((AetherProfileConfig, Boolean) -> Unit)?,
    onResetProfile: ((AetherProfile) -> AetherProfileConfig)?
) {
    var expandedMap by remember { mutableStateOf(mapOf<String, Boolean>(selectedProfile.id to true)) }
    var draft by remember(profileConfig) { mutableStateOf(profileConfig) }

    // Keep draft in sync when profile changes externally
    LaunchedEffect(profileConfig) { draft = profileConfig }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.aether_profiles_title).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
            modifier = Modifier.testTag("aether_profiles_title")
        )
        Text(text = stringResource(R.string.aether_profiles_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)

        AetherProfile.values().forEach { profile ->
            val isSelected = profile == selectedProfile
            val isExpanded = expandedMap[profile.id] == true
            AetherProfileCard(
                profile = profile,
                selected = isSelected,
                onSelect = {
                    onSelectProfile(profile)
                    expandedMap = expandedMap + (profile.id to true)
                },
                expanded = isSelected && isExpanded
            )
            if (isSelected && isExpanded) {
                ProfileStatusBanner(profile)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ManjaniqSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ManjaniqPurple.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("profile_settings_${profile.id}")
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = stringResource(profile.titleRes), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ManjaniqPurpleNeon)
                        Text(text = stringResource(profile.routeRes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                        ProfileSettingsContent(
                            profile = profile,
                            draft = draft,
                            onDraftChange = { draft = it },
                            aetherState = aetherState
                        )

                        // Save / Reset per profile
                        if (onSaveProfile != null && onResetProfile != null) {
                            var errorText by remember { mutableStateOf<String?>(null) }
                            var infoText by remember { mutableStateOf<String?>(null) }
                            var showRestartConfirm by remember { mutableStateOf<AetherProfileConfig?>(null) }

                            if (errorText != null) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                    Row(Modifier.padding(8.dp)) {
                                        Text(text = errorText!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.dismiss)) }
                                    }
                                }
                            }
                            if (infoText != null) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Row(Modifier.padding(8.dp)) {
                                        Text(text = infoText!!, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { infoText = null }) { Text(stringResource(R.string.dismiss)) }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val v = draft.validateForProfile(profile)
                                        if (v != null) { errorText = v; return@Button }
                                        val baseErr = draft.base.validate()
                                        if (baseErr != null) { errorText = baseErr; return@Button }
                                        val wantsRestart = aetherState == AetherState.RUNNING
                                        if (wantsRestart) showRestartConfirm = draft else { onSaveProfile(draft, false); infoText = "Profile settings saved." }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ManjaniqPurple),
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.save_settings), fontSize = 12.sp) }
                                OutlinedButton(onClick = {
                                    val def = onResetProfile(profile)
                                    draft = def
                                    infoText = "Defaults restored"
                                }) { Text(stringResource(R.string.reset_defaults), fontSize = 11.sp) }
                            }

                            // Export/import per profile
                            ProfileImportExport(
                                draft = draft,
                                onImport = { parsed -> draft = parsed; infoText = "Imported - review then Save" },
                                onError = { errorText = it }
                            )

                            if (showRestartConfirm != null) {
                                AlertDialog(
                                    onDismissRequest = { showRestartConfirm = null },
                                    title = { Text(stringResource(R.string.restart_engine_title)) },
                                    text = { Text("Aether is running. Save and restart to apply new settings?") },
                                    confirmButton = {
                                        Button(onClick = {
                                            val c = showRestartConfirm!!; showRestartConfirm = null
                                            onSaveProfile(c, true); infoText = "Saved + restarting"
                                        }) { Text(stringResource(R.string.save_and_restart)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            val c = showRestartConfirm!!; showRestartConfirm = null
                                            onSaveProfile(c, false); infoText = "Saved; applies on next start"
                                        }) { Text(stringResource(R.string.save_only)) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingsContent(
    profile: AetherProfile,
    draft: AetherProfileConfig,
    onDraftChange: (AetherProfileConfig) -> Unit,
    aetherState: AetherState
) {
    fun updateBase(mut: (AetherConfig) -> AetherConfig) {
        onDraftChange(draft.copy(base = mut(draft.base)))
    }

    val sections = com.manjaniq.core.models.getSectionsForProfile(profile)
    val showAdvanced = remember { mutableStateOf(false) }

    sections.forEach { section ->
        Text(text = stringResource(section.titleRes).uppercase(), color = ManjaniqPurpleNeon, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        section.keys.forEach { key ->
            when (key) {
                "transport" -> {
                    Text(text = stringResource(R.string.aether_transport), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("masque" to R.string.aether_transport_masque, "wg" to R.string.aether_transport_wg, "gool" to R.string.aether_transport_gool, "mim" to R.string.aether_transport_mim).forEach { (id, res) ->
                            FilterChip(selected = draft.base.transport == id, onClick = { updateBase { it.copy(transport = id) } }, label = { Text(stringResource(res), fontSize = 11.sp) })
                        }
                    }
                }
                "scanMode" -> {
                    Text(text = stringResource(R.string.aether_scan_mode), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("turbo", "balanced", "thorough", "verified", "ironclad").forEach { mode ->
                            FilterChip(selected = draft.base.scanMode == mode, onClick = { updateBase { it.copy(scanMode = mode) } }, label = { Text(mode, fontSize = 11.sp) })
                        }
                    }
                }
                "peer" -> {
                    OutlinedTextField(value = draft.base.peer, onValueChange = { v -> updateBase { cfg -> cfg.copy(peer = v) } }, label = { Text(stringResource(R.string.aether_peer)) }, supportingText = { Text(stringResource(R.string.aether_peer_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                "socksBind" -> {
                    OutlinedTextField(value = draft.base.socksBind, onValueChange = { v -> updateBase { cfg -> cfg.copy(socksBind = v) } }, label = { Text(stringResource(R.string.aether_socks_bind)) }, supportingText = { Text(stringResource(R.string.aether_socks_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                "httpProxyBind" -> {
                    OutlinedTextField(value = draft.base.httpProxyBind, onValueChange = { v -> updateBase { cfg -> cfg.copy(httpProxyBind = v) } }, label = { Text(stringResource(R.string.aether_http_bind)) }, supportingText = { Text(stringResource(R.string.aether_http_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                "dns" -> {
                    OutlinedTextField(value = draft.base.dns, onValueChange = { v -> updateBase { cfg -> cfg.copy(dns = v) } }, label = { Text(stringResource(R.string.aether_dns)) }, supportingText = { Text(stringResource(R.string.aether_dns_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                "noizeProfile" -> {
                    if (section.titleRes != R.string.section_advanced || showAdvanced.value) {
                        Text(text = stringResource(R.string.aether_noize), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("firewall", "balanced", "gfw", "aggressive", "off").forEach { p ->
                                FilterChip(selected = draft.base.noizeProfile == p, onClick = { updateBase { it.copy(noizeProfile = p) } }, label = { Text(p, fontSize = 11.sp) })
                            }
                        }
                    }
                }
                "echEnabled" -> {
                    if (section.titleRes != R.string.section_advanced || showAdvanced.value) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = draft.base.echEnabled, onCheckedChange = { v -> updateBase { cfg -> cfg.copy(echEnabled = v) } })
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(text = stringResource(R.string.aether_ech), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(text = stringResource(R.string.aether_ech_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
                "ipVersion" -> {
                    if (section.titleRes != R.string.section_advanced || showAdvanced.value) {
                        Text(text = "IP version", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("v4" to "IPv4", "v6" to "IPv6", "both" to "Dual").forEach { (id, label) ->
                                FilterChip(selected = draft.base.ipVersion == id, onClick = { updateBase { it.copy(ipVersion = id) } }, label = { Text(label, fontSize = 11.sp) })
                            }
                        }
                    }
                }
                "upstream" -> {
                    if (section.titleRes != R.string.section_advanced || showAdvanced.value) {
                        OutlinedTextField(value = draft.base.upstream, onValueChange = { v -> updateBase { cfg -> cfg.copy(upstream = v) } }, label = { Text(stringResource(R.string.aether_upstream)) }, supportingText = { Text(stringResource(R.string.aether_upstream_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
                "quickReconnect" -> {
                    if (section.titleRes != R.string.section_advanced || showAdvanced.value) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = draft.base.quickReconnect, onCheckedChange = { v -> updateBase { cfg -> cfg.copy(quickReconnect = v) } })
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(text = stringResource(R.string.aether_quick_reconnect), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(text = stringResource(R.string.aether_quick_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
                "teamName" -> {
                    if (section.titleRes != R.string.section_advanced || showAdvanced.value) {
                        OutlinedTextField(value = draft.base.teamName, onValueChange = { v -> updateBase { cfg -> cfg.copy(teamName = v) } }, label = { Text(stringResource(R.string.aether_team)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
                "psiphonEndpoint" -> {
                    Text(text = stringResource(R.string.psiphon_endpoint), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    OutlinedTextField(value = draft.psiphonEndpoint, onValueChange = { onDraftChange(draft.copy(psiphonEndpoint = it)) }, label = { Text(stringResource(R.string.psiphon_endpoint)) }, supportingText = { Text(stringResource(R.string.psiphon_endpoint_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = profile.capability == com.manjaniq.core.models.ProfileCapability.Available || draft.psiphonEndpoint.isNotEmpty())
                    if (draft.psiphonEnabled || draft.psiphonEndpoint.isNotEmpty()) {
                        Text(text = "Psiphon chaining: Aether dials through verified Psiphon proxy. Requires external Psiphon binary.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        Text(text = stringResource(R.string.profile_requires_external), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
                "psiphonRegion" -> {
                    OutlinedTextField(value = draft.psiphonRegion, onValueChange = { onDraftChange(draft.copy(psiphonRegion = it)) }, label = { Text(stringResource(R.string.psiphon_region)) }, supportingText = { Text(stringResource(R.string.psiphon_region_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                "torSocks" -> {
                    Text(text = stringResource(R.string.tor_socks), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    OutlinedTextField(value = draft.torSocks, onValueChange = { onDraftChange(draft.copy(torSocks = it)) }, label = { Text(stringResource(R.string.tor_socks)) }, supportingText = { Text(stringResource(R.string.tor_socks_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(text = stringResource(R.string.profile_requires_external), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                "torBridges" -> {
                    OutlinedTextField(value = draft.torBridges, onValueChange = { onDraftChange(draft.copy(torBridges = it)) }, label = { Text(stringResource(R.string.tor_bridges)) }, supportingText = { Text(stringResource(R.string.tor_bridges_hint), fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
                "torUseBridges" -> {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = draft.torUseBridges, onCheckedChange = { onDraftChange(draft.copy(torUseBridges = it)) })
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(text = stringResource(R.string.tor_use_bridges), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(text = stringResource(R.string.tor_use_bridges_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        val hasAdvanced = sections.any { it.titleRes == R.string.section_advanced }
        if (hasAdvanced) {
            TextButton(onClick = { showAdvanced.value = !showAdvanced.value }) {
                Text(text = if (showAdvanced.value) "Hide advanced" else "Show advanced", color = ManjaniqPurpleNeon, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProfileImportExport(
    draft: AetherProfileConfig,
    onImport: (AetherProfileConfig) -> Unit,
    onError: (String) -> Unit
) {
    var exportText by remember { mutableStateOf<String?>(null) }
    var importVisible by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exportText = draft.toJson() }) { Text(stringResource(R.string.export_json), fontSize = 11.sp) }
        OutlinedButton(onClick = { importVisible = true; importText = draft.toJson() }) { Text(stringResource(R.string.import_json), fontSize = 11.sp) }
    }

    if (exportText != null) {
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text(stringResource(R.string.aether_export_title)) },
            text = { Text(exportText ?: "", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(exportText ?: "")); exportText = null }) { Text(stringResource(R.string.copy)) } },
            dismissButton = { TextButton(onClick = { exportText = null }) { Text(stringResource(R.string.close)) } }
        )
    }
    if (importVisible) {
        AlertDialog(
            onDismissRequest = { importVisible = false },
            title = { Text(stringResource(R.string.aether_import_title)) },
            text = { OutlinedTextField(value = importText, onValueChange = { importText = it }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 12) },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = AetherProfileConfig.fromJson(importText)
                    if (parsed == null) onError("Invalid JSON") else { onImport(parsed); importVisible = false }
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = { TextButton(onClick = { importVisible = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
