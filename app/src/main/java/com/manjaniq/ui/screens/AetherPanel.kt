package com.manjaniq.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.AetherLog
import com.manjaniq.core.AetherUiState
import com.manjaniq.core.models.AetherCapabilities
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherFragmenting
import com.manjaniq.core.models.AetherIpChoice
import com.manjaniq.core.models.AetherNoizeChoice
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig
import com.manjaniq.core.models.AetherScanChoice
import com.manjaniq.core.models.AetherState
import com.manjaniq.core.models.AetherTransportChoice
import com.manjaniq.core.models.getSectionsForProfile
import com.manjaniq.ui.AetherTestTags
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.components.AetherLogPanel
import com.manjaniq.ui.components.AetherOption
import com.manjaniq.ui.components.AetherOptionGroup
import com.manjaniq.ui.components.AetherProfileCard
import com.manjaniq.ui.components.AetherReachNote
import com.manjaniq.ui.components.AetherSection
import com.manjaniq.ui.components.AetherStatusCard
import com.manjaniq.ui.components.ProfileStatusBanner
import com.manjaniq.ui.theme.AetherCyan
import com.manjaniq.ui.theme.AetherSurface
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.ManjaniqPurple
import com.manjaniq.ui.theme.ManjaniqPurpleDeep
import com.manjaniq.ui.theme.ManjaniqSurface
import com.manjaniq.ui.theme.StatusStarting

/** Start is neon blue, stop is purple — the accent split the Aether UI already used. */
private val NeonBlue = Color(0xFF00D5FF)

/**
 * The Aether region of the home screen: everything the Aether core owns, in one
 * Aether-owned file.
 *
 * The flow is progressive, mirroring the order the Aether CLI asks its questions
 * (protocol → scan → IP version → carrier → obfuscation → local proxy):
 *
 *  1. a compact notice and the start control, so the tunnel is one tap away
 *     without scrolling past settings;
 *  2. the route list, where picking a route is the only decision on screen;
 *  3. the selected route's settings, grouped in collapsible sections that show
 *     only what that route owns — [getSectionsForProfile] is the single source
 *     of truth, so no Aether setting is dropped, only deferred;
 *  4. the live status card and the operational log, both sized to content.
 *
 * Nothing here talks to Aether directly: every action is a callback the host
 * already supplies, so connection behaviour is unchanged.
 */
@Composable
fun AetherPanel(
    ui: AetherUiState,
    logs: List<AetherLog>,
    selectedProfile: AetherProfile?,
    profileConfig: AetherProfileConfig?,
    legacyConfig: AetherConfig,
    onSelectProfile: ((AetherProfile) -> Unit)?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onSaveProfileConfig: ((AetherProfileConfig, Boolean) -> Unit)?,
    onResetProfileDefaults: ((AetherProfile) -> AetherProfileConfig)?,
    onSaveLegacyConfig: (AetherConfig, Boolean) -> Unit,
    onResetLegacyDefaults: () -> AetherConfig,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AetherTestTags.PANEL),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AetherNotice(socksEndpoint = ui.socksEndpoint)

        if (ui.lastError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.aether_error_title),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ui.lastError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                    Row {
                        TextButton(onClick = onDismissError) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }

        if (!ui.isNativeLoaded) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.aether_not_native),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp
                    )
                    if (ui.loadError != null) {
                        Text(
                            text = ui.loadError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "Build requires libaether.so (aether-wrapper cdylib). CI builds it; this APK was built without it.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp
                    )
                }
            }
        }

        AetherStartControl(ui = ui, onStart = onStart, onStop = onStop)

        if (selectedProfile == null || onSelectProfile == null) {
            // No profile-aware bindings: fall back to the single-config form so
            // that path keeps working exactly as before.
            AetherSettingsForm(
                initial = legacyConfig,
                aetherState = ui.state,
                onSave = onSaveLegacyConfig,
                onResetDefaults = onResetLegacyDefaults
            )
        } else {
            AetherRouteFlow(
                profile = selectedProfile,
                onSelectProfile = onSelectProfile,
                profileConfig = profileConfig,
                legacyConfig = legacyConfig,
                aetherState = ui.state,
                onSaveProfileConfig = onSaveProfileConfig,
                onResetProfileDefaults = onResetProfileDefaults
            )
        }

        AetherStatusCard(
            state = ui.state,
            socksEndpoint = ui.socksEndpoint,
            endpoint = ui.endpoint,
            version = ui.version,
            lastError = null,
            isNativeLoaded = ui.isNativeLoaded
        )

        AetherLogPanel(
            logs = logs,
            state = ui.state,
            onClear = onClearLogs
        )
    }
}

/**
 * One compact line about what Aether is here, with the detail behind a toggle.
 *
 * It replaces a five-paragraph block that used to sit above the start control
 * and push it off the first screen; the VPN limitation stays visible in the
 * collapsed summary, so nothing important is hidden.
 */
@Composable
private fun AetherNotice(socksEndpoint: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AetherTestTags.NOTICE),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ManjaniqPurpleDeep),
        border = BorderStroke(1.dp, ManjaniqPurple.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Card(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AetherTestTags.NOTICE_TOGGLE),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.aether_coming_soon_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.aether_notice_summary) +
                                " · socks5h://$socksEndpoint",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text = if (expanded) "▲" else "▼", color = AetherCyan, fontSize = 10.sp)
                }
            }
            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.aether_coming_soon_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.profile_socks_note, socksEndpoint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                    HorizontalDivider(color = ManjaniqBorder)
                    Text(
                        text = stringResource(R.string.vpn_limitation_title),
                        color = AetherCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = stringResource(R.string.vpn_limitation_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Start, stop, and — while a start is in flight — cancel.
 *
 * The button used to go disabled for the whole start, so the only way out of a
 * slow scan was to kill the service. `AetherService` now honours a per-start
 * cancellation, so a starting tunnel offers a real cancel instead of a dead
 * control. Stopping stays disabled while stopping: there is nothing to cancel.
 */
@Composable
private fun AetherStartControl(ui: AetherUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val running = ui.state == AetherState.RUNNING
    val starting = ui.state == AetherState.STARTING
    val stopping = ui.state == AetherState.STOPPING
    Button(
        onClick = { if (running || starting) onStop() else onStart() },
        enabled = ui.isNativeLoaded && (!ui.busy || starting),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(TestTags.AETHER_TOGGLE),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) ManjaniqPurple else NeonBlue,
            contentColor = if (running) Color.White else Color(0xFF00212A)
        )
    ) {
        if (ui.busy || starting || stopping) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (running) Color.White else Color(0xFF00212A)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = when {
                starting -> stringResource(R.string.aether_cancel_start)
                stopping -> stringResource(R.string.stopping)
                running -> stringResource(R.string.stop_engine)
                else -> stringResource(R.string.start_engine)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Step 1 (route list) and step 2 (that route's settings).
 *
 * Selecting a route opens its settings; tapping the already-selected route
 * collapses them again. The old code could only ever add `true` to its expansion
 * map, so a route's settings could never be closed.
 */
@Composable
private fun AetherRouteFlow(
    profile: AetherProfile,
    onSelectProfile: (AetherProfile) -> Unit,
    profileConfig: AetherProfileConfig?,
    legacyConfig: AetherConfig,
    aetherState: AetherState,
    onSaveProfileConfig: ((AetherProfileConfig, Boolean) -> Unit)?,
    onResetProfileDefaults: ((AetherProfile) -> AetherProfileConfig)?
) {
    var settingsOpen by remember(profile) { mutableStateOf(true) }

    // Drafts and open sections live here, keyed by route id, so going back to an
    // earlier choice restores exactly what was on screen: unsaved edits and open
    // groups survive a round trip through the route list.
    var drafts by remember { mutableStateOf(mapOf<String, AetherProfileConfig>()) }
    var openSections by remember { mutableStateOf(mapOf<String, Set<Int>>()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.aether_route_step).uppercase(),
                color = AetherCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = stringResource(R.string.aether_profiles_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(AetherTestTags.ROUTE_TITLE)
            )
            Text(
                text = stringResource(R.string.aether_profiles_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AetherTestTags.ROUTE_LIST),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AetherProfile.ALL.forEach { route ->
                val selected = route == profile
                AetherProfileCard(
                    profile = route,
                    selected = selected,
                    onSelect = {
                        if (selected) {
                            settingsOpen = !settingsOpen
                        } else {
                            onSelectProfile(route)
                            settingsOpen = true
                        }
                    },
                    expanded = selected && settingsOpen
                )
                if (selected && settingsOpen) {
                    ProfileStatusBanner(route)
                    val stored = profileConfig ?: AetherProfileConfig(
                        profileId = route.id,
                        base = legacyConfig
                    )
                    AetherRouteSettings(
                        profile = route,
                        stored = stored,
                        draft = drafts[route.id] ?: stored,
                        onDraftChange = { next -> drafts = drafts + (route.id to next) },
                        openSections = openSections[route.id],
                        onOpenSectionsChange = { next -> openSections = openSections + (route.id to next) },
                        aetherState = aetherState,
                        onSaveProfileConfig = onSaveProfileConfig,
                        onResetProfileDefaults = onResetProfileDefaults
                    )
                }
            }
        }
    }
}

/** Step 2: the settings the selected route owns, in collapsible sections. */
@Composable
private fun AetherRouteSettings(
    profile: AetherProfile,
    stored: AetherProfileConfig,
    draft: AetherProfileConfig,
    onDraftChange: (AetherProfileConfig) -> Unit,
    openSections: Set<Int>?,
    onOpenSectionsChange: (Set<Int>) -> Unit,
    aetherState: AetherState,
    onSaveProfileConfig: ((AetherProfileConfig, Boolean) -> Unit)?,
    onResetProfileDefaults: ((AetherProfile) -> AetherProfileConfig)?
) {
    // Local vals: the null checks below stay valid inside the click lambdas.
    val saveProfile = onSaveProfileConfig
    val resetProfile = onResetProfileDefaults

    var errorText by remember(profile) { mutableStateOf<String?>(null) }
    var infoRes by remember(profile) { mutableStateOf<Int?>(null) }
    var pendingRestart by remember(profile) { mutableStateOf<AetherProfileConfig?>(null) }
    var confirmReset by remember(profile) { mutableStateOf(false) }

    val sections = remember(profile) { getSectionsForProfile(profile) }
    // Only the first group starts open: the route is chosen, the rest is one tap
    // away, and the screen never opens as a wall of settings.
    val open = openSections
        ?: sections.firstOrNull()?.let { setOf(it.titleRes) }
        ?: emptySet()
    val dirty = draft != stored

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ManjaniqSurface),
        border = BorderStroke(1.dp, AetherCyan.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AetherTestTags.routeSettings(profile.id))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag(AetherTestTags.routeToggle(profile.id))
            ) {
                Text(
                    text = stringResource(R.string.aether_settings_step).uppercase(),
                    color = AetherCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = stringResource(profile.titleRes),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(profile.routeRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            if (dirty) {
                Text(
                    text = stringResource(R.string.aether_unsaved_hint),
                    color = StatusStarting,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            sections.forEach { section ->
                val isOpen = section.titleRes in open
                AetherSection(
                    title = stringResource(section.titleRes),
                    summary = sectionSummary(section.titleRes, draft),
                    expanded = isOpen,
                    onToggle = {
                        onOpenSectionsChange(
                            if (isOpen) open - section.titleRes else open + section.titleRes
                        )
                    },
                    tag = AetherTestTags.section(sectionKey(section.titleRes))
                ) {
                    section.keys.forEach { key ->
                        AetherSetting(
                            key = key,
                            draft = draft,
                            onDraftChange = onDraftChange
                        )
                    }
                }
            }

            if (saveProfile != null && resetProfile != null) {
                if (errorText != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = errorText!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.dismiss)) }
                        }
                    }
                }
                if (infoRes != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(infoRes!!),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { infoRes = null }) { Text(stringResource(R.string.dismiss)) }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            errorText = null
                            infoRes = null
                            val problem = draft.validateForProfile(profile)
                            if (problem != null) {
                                errorText = problem
                                return@Button
                            }
                            if (aetherState == AetherState.RUNNING) {
                                pendingRestart = draft
                            } else {
                                saveProfile(draft, false)
                                infoRes = R.string.aether_settings_saved
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ManjaniqPurple),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(AetherTestTags.SAVE)
                    ) { Text(stringResource(R.string.save_settings), fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.testTag(AetherTestTags.RESET)
                    ) { Text(stringResource(R.string.reset_defaults), fontSize = 11.sp) }
                }

                AetherProfileImportExport(
                    draft = draft,
                    onImport = {
                        onDraftChange(it)
                        infoRes = R.string.aether_imported
                    },
                    onError = {
                        errorText = it
                        infoRes = null
                    }
                )
            }
        }
    }

    if (pendingRestart != null && saveProfile != null) {
        AlertDialog(
            onDismissRequest = { pendingRestart = null },
            title = { Text(stringResource(R.string.restart_engine_title)) },
            text = { Text(stringResource(R.string.aether_restart_question)) },
            confirmButton = {
                Button(onClick = {
                    val next = pendingRestart
                    pendingRestart = null
                    if (next != null) {
                        saveProfile(next, true)
                        infoRes = R.string.aether_settings_saved_restart
                    }
                }) { Text(stringResource(R.string.save_and_restart)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val next = pendingRestart
                    pendingRestart = null
                    if (next != null) {
                        saveProfile(next, false)
                        infoRes = R.string.aether_settings_saved_next_start
                    }
                }) { Text(stringResource(R.string.save_only)) }
            }
        )
    }

    if (confirmReset && resetProfile != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.aether_reset_question)) },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    onDraftChange(resetProfile(profile))
                    errorText = null
                    infoRes = R.string.aether_defaults_restored
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * One Aether setting, drawn according to what it is: a choice, a toggle or a
 * value. Settings this build cannot forward stay visible with the reason beside
 * them instead of pretending to work.
 */
@Composable
private fun AetherSetting(
    key: String,
    draft: AetherProfileConfig,
    onDraftChange: (AetherProfileConfig) -> Unit
) {
    val base = draft.base
    fun updateBase(mut: (AetherConfig) -> AetherConfig) = onDraftChange(draft.copy(base = mut(base)))

    when (key) {
        "transport" -> {
            AetherSettingLabel(R.string.aether_transport)
            AetherOptionGroup(
                options = transportOptions(base.transport),
                selectedId = base.transport,
                onSelect = { id -> updateBase { it.copy(transport = id) } },
                tagPrefix = "aether_transport_"
            )
            AetherKeyReachNote("transport")
        }

        "scanMode" -> if (base.peer.isBlank()) {
            AetherSettingLabel(R.string.aether_scan_mode)
            AetherOptionGroup(
                options = scanOptions(),
                selectedId = base.scanMode,
                onSelect = { mode -> updateBase { it.copy(scanMode = mode) } },
                tagPrefix = "aether_scan_"
            )
            AetherKeyReachNote("scanMode")
        }

        "ipVersion" -> if (base.peer.isBlank()) {
            AetherSettingLabel(R.string.aether_ip_version)
            AetherOptionGroup(
                options = ipOptions(),
                selectedId = base.ipVersion.ifBlank { AetherIpChoice.V4.id },
                onSelect = { ip -> updateBase { it.copy(ipVersion = ip) } },
                tagPrefix = "aether_ip_"
            )
            AetherKeyReachNote("ipVersion")
        }

        "peer" -> {
            AetherField(
                value = base.peer,
                onValueChange = { v -> updateBase { it.copy(peer = v) } },
                labelRes = R.string.aether_peer,
                hintRes = R.string.aether_peer_hint,
                tag = "aether_peer_field"
            )
            if (base.peer.isNotBlank()) {
                Text(
                    text = stringResource(R.string.aether_scan_skipped_peer),
                    color = StatusStarting,
                    fontSize = 11.sp
                )
            }
            AetherKeyReachNote("peer")
        }

        "socksBind" -> {
            val socksPort = base.socksBind.substringAfterLast(":").toIntOrNull()
            val httpPort = base.httpProxyBind.substringAfterLast(":").toIntOrNull()
            val problem = aetherBindError(base.socksBind)
                ?: if (socksPort != null && socksPort == httpPort) {
                    "SOCKS and HTTP proxy must listen on different ports"
                } else {
                    null
                }
            AetherField(
                value = base.socksBind,
                onValueChange = { v -> updateBase { it.copy(socksBind = v) } },
                labelRes = R.string.aether_socks_bind,
                hintRes = R.string.aether_socks_hint,
                errorText = problem,
                tag = "aether_socks_field"
            )
            AetherKeyReachNote("socksBind")
        }

        "httpProxyBind" -> {
            val socksPort = base.socksBind.substringAfterLast(":").toIntOrNull()
            val httpPort = base.httpProxyBind.substringAfterLast(":").toIntOrNull()
            val problem = aetherBindError(base.httpProxyBind)
                ?: if (base.httpProxyBind.isNotBlank() && httpPort != null && httpPort == socksPort) {
                    "SOCKS and HTTP proxy must listen on different ports"
                } else {
                    null
                }
            AetherField(
                value = base.httpProxyBind,
                onValueChange = { v -> updateBase { it.copy(httpProxyBind = v) } },
                labelRes = R.string.aether_http_bind,
                hintRes = R.string.aether_http_hint,
                errorText = problem,
                tag = "aether_http_field"
            )
            AetherKeyReachNote("httpProxyBind")
        }

        "dns" -> {
            AetherField(
                value = base.dns,
                onValueChange = { v -> updateBase { it.copy(dns = v) } },
                labelRes = R.string.aether_dns,
                hintRes = R.string.aether_dns_hint,
                tag = "aether_dns_field"
            )
            AetherKeyReachNote("dns")
        }

        "upstream" -> {
            AetherField(
                value = base.upstream,
                onValueChange = { v -> updateBase { it.copy(upstream = v) } },
                labelRes = R.string.aether_upstream,
                hintRes = R.string.aether_upstream_hint,
                tag = "aether_upstream_field"
            )
            AetherKeyReachNote("upstream")
        }

        "noizeProfile" -> {
            AetherSettingLabel(R.string.aether_noize)
            AetherOptionGroup(
                options = noizeOptions(),
                selectedId = base.noizeProfile.ifBlank { "firewall" },
                onSelect = { p -> updateBase { it.copy(noizeProfile = p) } },
                tagPrefix = "aether_noize_"
            )
            AetherKeyReachNote("noizeProfile")
        }

        "echEnabled" -> {
            AetherToggleRow(
                titleRes = R.string.aether_ech,
                descRes = R.string.aether_ech_desc,
                checked = base.echEnabled,
                onCheckedChange = { v -> updateBase { it.copy(echEnabled = v) } },
                tag = "aether_ech_toggle"
            )
            AetherKeyReachNote("echEnabled")
        }

        "quickReconnect" -> {
            AetherToggleRow(
                titleRes = R.string.aether_quick_reconnect,
                descRes = R.string.aether_quick_desc,
                checked = base.quickReconnect,
                onCheckedChange = { v -> updateBase { it.copy(quickReconnect = v) } },
                tag = "aether_quick_toggle"
            )
            AetherKeyReachNote("quickReconnect")
        }

        "teamName" -> {
            AetherField(
                value = base.teamName,
                onValueChange = { v -> updateBase { it.copy(teamName = v) } },
                labelRes = R.string.aether_team,
                tag = "aether_team_field"
            )
            AetherKeyReachNote("teamName")
        }

        "identityPath" -> {
            AetherField(
                value = base.identityPath,
                onValueChange = { v -> updateBase { it.copy(identityPath = v) } },
                labelRes = R.string.aether_identity_path,
                hintRes = R.string.aether_identity_hint,
                tag = "aether_identity_field"
            )
            AetherKeyReachNote("identityPath")
        }

        "fragmentEnabled", "fragmentSize", "fragmentDelay", "fragmentSniSplit" -> {
            // Rendered once, as a block, by the first fragmenting key so the
            // group reads as one explanation instead of four separate notes.
            if (key == "fragmentEnabled") AetherFragmentingBlock(base)
        }

        "psiphonEnabled" -> {
            AetherToggleRow(
                titleRes = R.string.aether_psiphon_enable,
                descRes = R.string.profile_requires_external,
                checked = draft.psiphonEnabled,
                onCheckedChange = { v -> onDraftChange(draft.copy(psiphonEnabled = v)) },
                tag = "aether_psiphon_toggle"
            )
            AetherKeyReachNote("psiphonEnabled")
        }

        "psiphonEndpoint" -> AetherField(
            value = draft.psiphonEndpoint,
            onValueChange = { v -> onDraftChange(draft.copy(psiphonEndpoint = v)) },
            labelRes = R.string.psiphon_endpoint,
            hintRes = R.string.psiphon_endpoint_hint,
            tag = "aether_psiphon_endpoint_field"
        )

        "psiphonRegion" -> AetherField(
            value = draft.psiphonRegion,
            onValueChange = { v -> onDraftChange(draft.copy(psiphonRegion = v)) },
            labelRes = R.string.psiphon_region,
            hintRes = R.string.psiphon_region_hint,
            tag = "aether_psiphon_region_field"
        )

        "torSocks" -> AetherField(
            value = draft.torSocks,
            onValueChange = { v -> onDraftChange(draft.copy(torSocks = v)) },
            labelRes = R.string.tor_socks,
            hintRes = R.string.tor_socks_hint,
            tag = "aether_tor_socks_field"
        )

        "torBridges" -> AetherField(
            value = draft.torBridges,
            onValueChange = { v -> onDraftChange(draft.copy(torBridges = v)) },
            labelRes = R.string.tor_bridges,
            hintRes = R.string.tor_bridges_hint,
            minLines = 2,
            tag = "aether_tor_bridges_field"
        )

        "torUseBridges" -> AetherToggleRow(
            titleRes = R.string.tor_use_bridges,
            descRes = R.string.tor_use_bridges_desc,
            checked = draft.torUseBridges,
            onCheckedChange = { v -> onDraftChange(draft.copy(torUseBridges = v)) },
            tag = "aether_tor_bridges_toggle"
        )
    }
}

/**
 * Fragmenting, shown read-only.
 *
 * Aether implements it (`third_party/Aether/aether/src/fragment.rs`, applied in
 * `masque_h2.rs`) but reads it only from its environment or from CLI flags, and
 * this build starts Aether through `aether_tunnel_start`, which has no field for
 * it. An editable control here would be a switch that changes nothing, so the
 * stored value is displayed with the reason and the variables Aether reads.
 */
@Composable
private fun AetherFragmentingBlock(base: AetherConfig) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.aether_fragment_title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.aether_fragment_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        AetherReachNote(
            reach = AetherCapabilities.reachOf("fragmentEnabled"),
            reasonRes = AetherCapabilities.reasonResFor("fragmentEnabled")
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AetherSurface, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AetherReadOnlyRow(
                labelRes = R.string.aether_fragment_enable,
                value = onOffLabel(base.fragmentEnabled),
                tag = AetherTestTags.FRAGMENT_TOGGLE
            )
            AetherReadOnlyRow(
                labelRes = R.string.aether_fragment_size,
                value = base.fragmentSize.ifBlank { AetherFragmenting.DEFAULT_SIZE },
                tag = AetherTestTags.FRAGMENT_SIZE
            )
            AetherReadOnlyRow(
                labelRes = R.string.aether_fragment_delay,
                value = base.fragmentDelay.ifBlank { AetherFragmenting.DEFAULT_DELAY },
                tag = AetherTestTags.FRAGMENT_DELAY
            )
            AetherReadOnlyRow(
                labelRes = R.string.aether_fragment_sni,
                value = onOffLabel(base.fragmentSniSplit),
                tag = AetherTestTags.FRAGMENT_SNI
            )
        }
        Text(
            text = stringResource(R.string.aether_fragment_env_label, AetherFragmenting.ENV_VARS.joinToString(", ")),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )
        Text(
            text = stringResource(R.string.aether_fragment_how_to_change),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontStyle = FontStyle.Italic,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun AetherReadOnlyRow(labelRes: Int, value: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = AetherCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Localised on/off wording for read-only value rows. */
@Composable
private fun onOffLabel(value: Boolean): String =
    stringResource(if (value) R.string.aether_choice_on else R.string.aether_choice_off)

@Composable
private fun AetherSettingLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Reach note for one setting key; silent for settings that are fully applied. */
@Composable
private fun AetherKeyReachNote(key: String) {
    val info = AetherCapabilities.of(key) ?: return
    AetherReachNote(reach = info.reach, reasonRes = info.reasonRes)
}

@Composable
private fun AetherField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    tag: String,
    hintRes: Int? = null,
    errorText: String? = null,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        isError = errorText != null,
        singleLine = minLines == 1,
        minLines = minLines,
        supportingText = {
            when {
                errorText != null -> Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                hintRes != null -> Text(stringResource(hintRes), fontSize = 11.sp)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}

@Composable
private fun AetherToggleRow(
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(descRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Export / import of one route's settings, unchanged in behaviour. */
@Composable
private fun AetherProfileImportExport(
    draft: AetherProfileConfig,
    onImport: (AetherProfileConfig) -> Unit,
    onError: (String) -> Unit
) {
    var exportText by remember { mutableStateOf<String?>(null) }
    var importVisible by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exportText = draft.toJson() }) {
            Text(stringResource(R.string.export_json), fontSize = 11.sp)
        }
        OutlinedButton(onClick = {
            importVisible = true
            importText = draft.toJson()
        }) { Text(stringResource(R.string.import_json), fontSize = 11.sp) }
    }

    if (exportText != null) {
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text(stringResource(R.string.aether_export_title)) },
            text = {
                Text(
                    exportText ?: "",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exportText ?: ""))
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
            title = { Text(stringResource(R.string.aether_import_title)) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = 12
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = AetherProfileConfig.fromJson(importText)
                    if (parsed == null) {
                        onError("Invalid JSON")
                    } else {
                        onImport(parsed)
                        importVisible = false
                    }
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { importVisible = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ---------------------------------------------------------------- choices ----

@Composable
private fun transportOptions(current: String): List<AetherOption> {
    val reason = stringResource(R.string.aether_transport_unreachable)
    return AetherTransportChoice.ALL.map { choice ->
        val selectable = choice.selectable || choice.id == current
        AetherOption(
            id = choice.id,
            label = stringResource(choice.labelRes),
            detail = stringResource(choice.detailRes),
            // A value already stored (for example from an imported config) stays
            // visible and selected rather than disappearing from the list.
            enabled = selectable,
            note = if (selectable) null else reason
        )
    }
}

@Composable
private fun scanOptions(): List<AetherOption> = AetherScanChoice.ALL.map { choice ->
    AetherOption(
        id = choice.id,
        label = choice.id,
        detail = stringResource(choice.detailRes)
    )
}

@Composable
private fun ipOptions(): List<AetherOption> = AetherIpChoice.ALL.map { choice ->
    AetherOption(id = choice.id, label = stringResource(choice.labelRes), detail = choice.id)
}

@Composable
private fun noizeOptions(): List<AetherOption> = AetherNoizeChoice.ALL.map { choice ->
    AetherOption(
        id = choice.id,
        label = choice.id,
        detail = stringResource(choice.detailRes)
    )
}

// --------------------------------------------------------------- summaries ---

/** Stable, tag-friendly key for a section, derived from its title resource. */
internal fun sectionKey(titleRes: Int): String = when (titleRes) {
    R.string.section_gateway -> "gateway"
    R.string.section_fragmenting -> "fragmenting"
    R.string.section_tunnel -> "tunnel"
    R.string.section_advanced -> "advanced"
    R.string.section_psiphon -> "psiphon"
    R.string.section_tor -> "tor"
    else -> "section_$titleRes"
}

/** One line describing what a collapsed section currently holds. */
@Composable
private fun sectionSummary(titleRes: Int, draft: AetherProfileConfig): String {
    val base = draft.base
    val notSet = stringResource(R.string.aether_section_summary_default)
    return when (sectionKey(titleRes)) {
        "gateway" -> {
            val transport = AetherTransportChoice.byId(base.transport)?.labelRes
                ?.let { stringResource(it) } ?: base.transport
            if (base.peer.isNotBlank()) {
                "$transport · ${base.peer}"
            } else {
                val ip = AetherIpChoice.ALL.firstOrNull { it.id == base.ipVersion }
                    ?.let { stringResource(it.labelRes) } ?: base.ipVersion
                "$transport · ${base.scanMode} · $ip"
            }
        }

        "fragmenting" -> {
            val state = onOffLabel(base.fragmentEnabled)
            "$state · ${stringResource(R.string.aether_fragment_status_blocked)}"
        }

        "tunnel" -> {
            val http = if (base.httpProxyBind.isBlank()) "" else " · ${base.httpProxyBind}"
            "${base.socksBind}$http"
        }

        "advanced" -> {
            val noize = base.noizeProfile.ifBlank { notSet }
            val ech = if (base.echEnabled) " · ECH" else ""
            val team = if (base.teamName.isBlank()) "" else " · ${base.teamName}"
            "$noize$ech$team"
        }

        "psiphon" -> draft.psiphonEndpoint.ifBlank { notSet }

        "tor" -> draft.torSocks.ifBlank { notSet }

        else -> ""
    }
}

// ------------------------------------------------------------ validation ----

/**
 * Loopback bind check for a `host:port` field.
 *
 * Mirrors [AetherConfig.validate] per field so the panel can mark the offending
 * input instead of reporting one undifferentiated error after Save.
 */
internal fun aetherBindError(value: String): String? {
    val v = value.trim()
    if (v.isEmpty()) return null
    if (!v.startsWith("127.0.0.1:") && !v.startsWith("localhost:")) {
        return "Must be loopback (127.0.0.1:port)"
    }
    val port = v.substringAfterLast(":").toIntOrNull() ?: return "Invalid port"
    if (port !in 1024..65535) return "Port must be between 1024 and 65535"
    return null
}

/** Fragment chunk-size spec, or null when it is acceptable. */
internal fun aetherFragmentSizeError(value: String): String? =
    AetherFragmenting.validateRange(value, AetherFragmenting.MAX_SIZE_BYTES.toLong())

/** Fragment delay spec, or null when it is acceptable. */
internal fun aetherFragmentDelayError(value: String): String? =
    AetherFragmenting.validateRange(value, AetherFragmenting.MAX_DELAY_MS.toLong())
