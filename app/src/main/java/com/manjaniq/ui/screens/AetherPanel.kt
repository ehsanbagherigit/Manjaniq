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
import com.manjaniq.core.AetherHelpers
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
import com.manjaniq.core.models.AetherStartMode
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
import com.manjaniq.ui.AetherAccent
import com.manjaniq.ui.AetherAccentDeep
import com.manjaniq.ui.AetherAccentSoft
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.ManjaniqPurple
import com.manjaniq.ui.theme.ManjaniqSurface

/** Start is neon blue, stop is purple — the accent split the Aether UI already used. */
private val NeonBlue = Color(0xFF00D5FF)

/**
 * The Aether region of the home screen: everything the Aether core owns, in one
 * Aether-owned file.
 *
 * The flow is progressive, mirroring the order the Aether CLI asks its questions
 * (protocol → scan → IP version → carrier → obfuscation → local proxy):
 *
 *  1. the start control, so the tunnel is one tap away without scrolling past
 *     settings;
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

        // Near the bottom, immediately before the log. The operational log stays
        // last, so the newest real Aether event is always the bottom thing on the
        // screen. These controls edit the same configuration the route settings do:
        // the selected route's when there is one, the stored configuration otherwise.
        val fragmentConfig = profileConfig?.base ?: legacyConfig
        val fragmentProfileConfig = profileConfig
        val saveProfileConfig = onSaveProfileConfig
        AetherFragmentingControl(
            config = fragmentConfig,
            routeName = selectedProfile?.let { stringResource(it.titleRes) },
            aetherState = ui.state,
            onSave = { updated, restart ->
                if (fragmentProfileConfig != null && saveProfileConfig != null) {
                    saveProfileConfig(fragmentProfileConfig.copy(base = updated), restart)
                } else {
                    onSaveLegacyConfig(updated, restart)
                }
            }
        )

        AetherLogPanel(
            logs = logs,
            state = ui.state,
            onClear = onClearLogs
        )
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
                color = AetherAccent,
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
    // Collapsed by default: the route's own groups come first, and these are the
    // flags most routes never touch.
    var engineOpen by remember(profile) { mutableStateOf(false) }

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
        border = BorderStroke(1.dp, AetherAccent.copy(alpha = 0.45f)),
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
                    color = AetherAccent,
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
                    color = AetherAccentSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Which path starts the engine. It is stored with the route and it
            // decides what the reach notes below can promise: the full-engine path
            // runs Aether's own command line, which is the only way gool, mim, Tor,
            // Psiphon, Zero Trust, routing, an upstream proxy or the in-tunnel DNS
            // list reach the core at all.
            AetherSettingLabel(R.string.aether_start_mode)
            AetherOptionGroup(
                options = startModeOptions(),
                selectedId = draft.base.startMode,
                onSelect = { id ->
                    onDraftChange(draft.copy(base = draft.base.copy(startMode = id)))
                },
                tagPrefix = "aether_start_mode_"
            )
            Text(
                text = stringResource(R.string.aether_start_mode_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            // Which child processes this installation can actually start. Read
            // from the native library directory, never from the writable data
            // directory, because Android 10+ refuses to execute from there.
            AetherReadOnlyRow(
                labelRes = R.string.aether_helpers_title,
                value = AetherHelpers.summary(),
                tag = "aether_helpers_summary"
            )
            val commandLineOnly = draft.base.commandLineOnlyFeatures()
            if (commandLineOnly.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.aether_command_line_only,
                        commandLineOnly.joinToString(", ")
                    ),
                    color = AetherAccentSoft,
                    fontSize = 11.sp
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

            // Every remaining flag the core accepts (Tor, Psiphon, Zero Trust,
            // routing, TLS/ECH, statistics, registration). They are edited in the
            // route's draft, so Save stores them with the route exactly like the
            // groups above.
            AetherEngineOptionsSection(
                base = draft.base,
                onChange = { next -> onDraftChange(draft.copy(base = next)) },
                expanded = engineOpen,
                onToggle = { engineOpen = !engineOpen }
            )

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
    // What this configuration's start path can apply: the reach notes below are
    // read through it, so a setting shown as applied really is applied.
    val startMode = AetherStartMode.byId(base.startMode) ?: AetherStartMode.GRANULAR

    when (key) {
        "transport" -> {
            AetherSettingLabel(R.string.aether_transport)
            AetherOptionGroup(
                options = transportOptions(base.transport, startMode),
                selectedId = base.transport,
                onSelect = { id -> updateBase { it.copy(transport = id) } },
                tagPrefix = "aether_transport_"
            )
            AetherKeyReachNote("transport", startMode)
        }

        "scanMode" -> if (base.peer.isBlank()) {
            AetherSettingLabel(R.string.aether_scan_mode)
            AetherOptionGroup(
                options = scanOptions(),
                selectedId = base.scanMode,
                onSelect = { mode -> updateBase { it.copy(scanMode = mode) } },
                tagPrefix = "aether_scan_"
            )
            AetherKeyReachNote("scanMode", startMode)
        }

        "ipVersion" -> if (base.peer.isBlank()) {
            AetherSettingLabel(R.string.aether_ip_version)
            AetherOptionGroup(
                options = ipOptions(),
                selectedId = base.ipVersion.ifBlank { AetherIpChoice.V4.id },
                onSelect = { ip -> updateBase { it.copy(ipVersion = ip) } },
                tagPrefix = "aether_ip_"
            )
            AetherKeyReachNote("ipVersion", startMode)
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
                    color = AetherAccentSoft,
                    fontSize = 11.sp
                )
            }
            AetherKeyReachNote("peer", startMode)
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
            AetherKeyReachNote("socksBind", startMode)
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
            AetherKeyReachNote("httpProxyBind", startMode)
        }

        "dns" -> {
            AetherField(
                value = base.dns,
                onValueChange = { v -> updateBase { it.copy(dns = v) } },
                labelRes = R.string.aether_dns,
                hintRes = R.string.aether_dns_hint,
                tag = "aether_dns_field"
            )
            AetherKeyReachNote("dns", startMode)
        }

        "upstream" -> {
            AetherField(
                value = base.upstream,
                onValueChange = { v -> updateBase { it.copy(upstream = v) } },
                labelRes = R.string.aether_upstream,
                hintRes = R.string.aether_upstream_hint,
                tag = "aether_upstream_field"
            )
            AetherKeyReachNote("upstream", startMode)
        }

        "noizeProfile" -> {
            AetherSettingLabel(R.string.aether_noize)
            AetherOptionGroup(
                options = noizeOptions(),
                selectedId = base.noizeProfile.ifBlank { "firewall" },
                onSelect = { p -> updateBase { it.copy(noizeProfile = p) } },
                tagPrefix = "aether_noize_"
            )
            AetherKeyReachNote("noizeProfile", startMode)
        }

        "echEnabled" -> {
            AetherToggleRow(
                titleRes = R.string.aether_ech,
                descRes = R.string.aether_ech_desc,
                checked = base.echEnabled,
                onCheckedChange = { v -> updateBase { it.copy(echEnabled = v) } },
                tag = "aether_ech_toggle"
            )
            AetherKeyReachNote("echEnabled", startMode)
        }

        "quickReconnect" -> {
            AetherToggleRow(
                titleRes = R.string.aether_quick_reconnect,
                descRes = R.string.aether_quick_desc,
                checked = base.quickReconnect,
                onCheckedChange = { v -> updateBase { it.copy(quickReconnect = v) } },
                tag = "aether_quick_toggle"
            )
            AetherKeyReachNote("quickReconnect", startMode)
        }

        "teamName" -> {
            AetherField(
                value = base.teamName,
                onValueChange = { v -> updateBase { it.copy(teamName = v) } },
                labelRes = R.string.aether_team,
                tag = "aether_team_field"
            )
            AetherKeyReachNote("teamName", startMode)
        }

        "identityPath" -> {
            AetherField(
                value = base.identityPath,
                onValueChange = { v -> updateBase { it.copy(identityPath = v) } },
                labelRes = R.string.aether_identity_path,
                hintRes = R.string.aether_identity_hint,
                tag = "aether_identity_field"
            )
            AetherKeyReachNote("identityPath", startMode)
        }

        "psiphonEnabled" -> {
            AetherToggleRow(
                titleRes = R.string.aether_psiphon_enable,
                descRes = R.string.profile_requires_external,
                checked = draft.psiphonEnabled,
                onCheckedChange = { v -> onDraftChange(draft.copy(psiphonEnabled = v)) },
                tag = "aether_psiphon_toggle"
            )
            AetherKeyReachNote("psiphonEnabled", startMode)
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
 * The Fragmenting area: editable, and applied.
 *
 * It sits near the bottom of the Aether screen, immediately before the operational
 * log (which stays last), and it is deliberately not a per-route setting group:
 * fragmenting belongs to one carrier rather than to a route, so it is shown once,
 * for whichever route is selected.
 *
 * What the repository establishes:
 *  - `third_party/Aether/aether/src/fragment.rs` implements it (`FragmentConfig`,
 *    `FragmentingStream`, `sni_host_range`, with its own unit tests);
 *  - `masque_h2.rs` is its only consumer (:239, :284, :402), so it applies to
 *    MASQUE over HTTP/2 and to nothing else - not to the HTTP/3 (QUIC) MASQUE path,
 *    not to WireGuard, not to gool or mim;
 *  - that carrier is chosen at connection time from `AETHER_MASQUE_HTTP2`
 *    (`masque_h2::enabled()`, `masque_h2.rs:141-149`), which the tunnel
 *    (`lib.rs:1530-1544`), the scanner's verification (`prober.rs:414-431`) and the
 *    CLI's own transport question (`lib.rs:3255-3268`) all consult;
 *  - configuration is environment-only upstream (`AETHER_MASQUE_H2_FRAGMENT*`),
 *    written by the CLI flags `--fragment`, `--no-fragment`, `--fragment-size` and
 *    `--fragment-delay` (`cli.rs:602-605`); the SNI split has no flag at all
 *    (`fragment.rs:49` reads it and nothing sets it);
 *  - `aether_tunnel_start`'s payload (`ffi.rs:246-260`) has no field for any of it,
 *    which is why the bridge writes those variables itself
 *    (`AetherBridge.fragmentingApply` → `aether-wrapper/src/lib.rs`) before the scan
 *    starts, and why enabling fragmenting also selects the carrier: without
 *    `AETHER_MASQUE_HTTP2` the fragments would have nowhere to be written.
 *
 * So the controls here are real. What they cannot do is apply to a transport that
 * has no HTTP/2 carrier, and in that case no switch is shown at all - a control that
 * changes nothing would be a lie - while the stored values stay visible, persisted
 * and exported with the route.
 */
@Composable
private fun AetherFragmentingControl(
    config: AetherConfig,
    routeName: String?,
    aetherState: AetherState,
    onSave: ((AetherConfig, Boolean) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember(config) { mutableStateOf(config) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoRes by remember { mutableStateOf<Int?>(null) }
    var pendingRestart by remember { mutableStateOf<AetherConfig?>(null) }

    // The carrier fragmenting needs. `AetherConfig.usesHttp2Carrier()` pairs the two
    // for the engine; this is the same condition, for the screen.
    val applies = draft.transport == AetherTransportChoice.MASQUE.id
    val dirty = draft.fragmentEnabled != config.fragmentEnabled ||
        draft.fragmentSize != config.fragmentSize ||
        draft.fragmentDelay != config.fragmentDelay ||
        draft.fragmentSniSplit != config.fragmentSniSplit
    val sizeError = aetherFragmentSizeError(draft.fragmentSize)
    val delayError = aetherFragmentDelayError(draft.fragmentDelay)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AetherTestTags.FRAGMENT_SECTION),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ManjaniqBorder)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Card(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AetherTestTags.FRAGMENT_SECTION_TOGGLE),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.section_fragmenting).uppercase(),
                            color = AetherAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        )
                        // The status line reports what is stored, and the carrier that
                        // comes with it, rather than a bare on/off.
                        Text(
                            text = stringResource(
                                if (config.fragmentEnabled && applies) {
                                    R.string.aether_fragment_status_on
                                } else {
                                    R.string.aether_fragment_status_off
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (expanded) "\u25B2" else "\u25BC",
                        color = AetherAccent,
                        fontSize = 10.sp
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    // Which carrier this applies to, stated before any value, so the
                    // numbers can never be read as global.
                    Text(
                        text = stringResource(R.string.aether_fragment_scope),
                        color = AetherAccentSoft,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    AetherReachNote(
                        reach = AetherCapabilities.reachOf("fragmentEnabled"),
                        reasonRes = AetherCapabilities.reasonResFor("fragmentEnabled")
                    )
                    Text(
                        text = if (routeName != null) {
                            stringResource(R.string.aether_fragment_recorded_for, routeName)
                        } else {
                            stringResource(R.string.aether_fragment_recorded_none)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    if (!applies) {
                        // No controls: this transport has no HTTP/2 carrier, so nothing
                        // here could reach Aether. The stored values stay visible.
                        Text(
                            text = stringResource(
                                R.string.aether_fragment_needs_masque,
                                stringResource(
                                    AetherTransportChoice.byId(draft.transport)?.labelRes
                                        ?: R.string.aether_transport_masque
                                )
                            ),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.testTag(AetherTestTags.FRAGMENT_UNAVAILABLE)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AetherAccentDeep, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AetherReadOnlyRow(
                                labelRes = R.string.aether_fragment_enable,
                                value = onOffLabel(config.fragmentEnabled),
                                tag = AetherTestTags.FRAGMENT_TOGGLE
                            )
                            AetherReadOnlyRow(
                                labelRes = R.string.aether_fragment_size,
                                value = config.fragmentSize.ifBlank { AetherFragmenting.DEFAULT_SIZE },
                                tag = AetherTestTags.FRAGMENT_SIZE
                            )
                            AetherReadOnlyRow(
                                labelRes = R.string.aether_fragment_delay,
                                value = config.fragmentDelay.ifBlank { AetherFragmenting.DEFAULT_DELAY },
                                tag = AetherTestTags.FRAGMENT_DELAY
                            )
                            AetherReadOnlyRow(
                                labelRes = R.string.aether_fragment_sni,
                                value = onOffLabel(config.fragmentSniSplit),
                                tag = AetherTestTags.FRAGMENT_SNI
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AetherAccentDeep, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AetherFragmentSwitchRow(
                                titleRes = R.string.aether_fragment_enable,
                                descRes = R.string.aether_fragment_enable_desc,
                                checked = draft.fragmentEnabled,
                                onCheckedChange = { v -> draft = draft.copy(fragmentEnabled = v) },
                                rowTag = AetherTestTags.FRAGMENT_TOGGLE,
                                switchTag = AetherTestTags.FRAGMENT_TOGGLE_SWITCH
                            )
                            if (draft.fragmentEnabled) {
                                // The consequence of the switch, next to the switch:
                                // fragments are written into this carrier's ClientHello,
                                // so asking for them changes the carrier.
                                Text(
                                    text = stringResource(R.string.aether_fragment_carrier_switch),
                                    color = AetherAccentSoft,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.testTag(AetherTestTags.FRAGMENT_CARRIER_NOTE)
                                )
                            }
                            AetherField(
                                value = draft.fragmentSize,
                                onValueChange = { v -> draft = draft.copy(fragmentSize = v) },
                                labelRes = R.string.aether_fragment_size,
                                hintRes = R.string.aether_fragment_size_hint,
                                errorText = sizeError,
                                tag = AetherTestTags.FRAGMENT_SIZE
                            )
                            AetherField(
                                value = draft.fragmentDelay,
                                onValueChange = { v -> draft = draft.copy(fragmentDelay = v) },
                                labelRes = R.string.aether_fragment_delay,
                                hintRes = R.string.aether_fragment_delay_hint,
                                errorText = delayError,
                                tag = AetherTestTags.FRAGMENT_DELAY
                            )
                            AetherFragmentSwitchRow(
                                titleRes = R.string.aether_fragment_sni,
                                descRes = R.string.aether_fragment_sni_desc,
                                checked = draft.fragmentSniSplit,
                                onCheckedChange = { v -> draft = draft.copy(fragmentSniSplit = v) },
                                rowTag = AetherTestTags.FRAGMENT_SNI,
                                switchTag = AetherTestTags.FRAGMENT_SNI_SWITCH
                            )
                        }

                        if (errorText != null) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = errorText!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { errorText = null }) {
                                        Text(stringResource(R.string.dismiss))
                                    }
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
                                    TextButton(onClick = { infoRes = null }) {
                                        Text(stringResource(R.string.dismiss))
                                    }
                                }
                            }
                        }
                        if (dirty) {
                            Text(
                                text = stringResource(R.string.aether_unsaved_hint),
                                color = AetherAccentSoft,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (onSave != null) {
                            Button(
                                onClick = {
                                    errorText = null
                                    infoRes = null
                                    // Refuse here rather than let Aether fall back to a
                                    // default nobody asked for: fragment.rs:93-113 parses
                                    // anything it does not understand away silently.
                                    val problem = sizeError?.let { "Fragment size: $it" }
                                        ?: delayError?.let { "Fragment delay: $it" }
                                        ?: draft.validate()
                                    if (problem != null) {
                                        errorText = problem
                                        return@Button
                                    }
                                    if (aetherState == AetherState.RUNNING) {
                                        pendingRestart = draft
                                    } else {
                                        onSave(draft, false)
                                        infoRes = R.string.aether_fragment_saved
                                    }
                                },
                                enabled = dirty,
                                colors = ButtonDefaults.buttonColors(containerColor = ManjaniqPurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(AetherTestTags.FRAGMENT_SAVE)
                            ) { Text(stringResource(R.string.save_settings), fontSize = 12.sp) }
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.aether_fragment_env_label,
                            AetherFragmenting.ENV_VARS.joinToString(", ")
                        ),
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
        }
    }

    if (pendingRestart != null && onSave != null) {
        AlertDialog(
            onDismissRequest = { pendingRestart = null },
            title = { Text(stringResource(R.string.restart_engine_title)) },
            text = { Text(stringResource(R.string.aether_restart_question)) },
            confirmButton = {
                Button(onClick = {
                    val next = pendingRestart
                    pendingRestart = null
                    if (next != null) {
                        onSave(next, true)
                        infoRes = R.string.aether_fragment_saved_restart
                    }
                }) { Text(stringResource(R.string.save_and_restart)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val next = pendingRestart
                    pendingRestart = null
                    if (next != null) {
                        onSave(next, false)
                        infoRes = R.string.aether_fragment_saved
                    }
                }) { Text(stringResource(R.string.save_only)) }
            }
        )
    }
}

/**
 * One fragmenting switch. The row carries the tag the screen has always used and the
 * switch carries its own, so a test can click the control itself rather than the row
 * around it.
 */
@Composable
private fun AetherFragmentSwitchRow(
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    rowTag: String,
    switchTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(rowTag)
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTag)
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
            color = AetherAccentSoft,
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
private fun AetherKeyReachNote(key: String, mode: AetherStartMode) {
    if (AetherCapabilities.of(key) == null) return
    AetherReachNote(
        reach = AetherCapabilities.reachOf(key, mode),
        reasonRes = AetherCapabilities.reasonResFor(key, mode)
    )
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
private fun startModeOptions(): List<AetherOption> = AetherStartMode.ALL.map { mode ->
    AetherOption(
        id = mode.id,
        label = stringResource(mode.labelRes),
        detail = stringResource(mode.detailRes)
    )
}

@Composable
private fun transportOptions(current: String, mode: AetherStartMode): List<AetherOption> {
    val reason = stringResource(R.string.aether_transport_unreachable)
    return AetherTransportChoice.ALL.map { choice ->
        // The full-engine path runs cli.rs, where all four ids are protocols; the
        // granular path must stay on the two api::Transport parses.
        val selectable = AetherTransportChoice.isSelectable(choice.id, mode.fullEngine) ||
            choice.id == current
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
