package com.manjaniq.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manjaniq.R
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.AetherIpVersion
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherPsiphon
import com.manjaniq.core.AetherPsiphonCdnSet
import com.manjaniq.core.AetherPsiphonMode
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.AetherScanResult
import com.manjaniq.core.AetherSettingsStorage
import com.manjaniq.core.AetherTor
import com.manjaniq.core.AetherTorBridges
import com.manjaniq.core.AetherTorRelays
import com.manjaniq.core.AetherTransport
import com.manjaniq.core.ProfileItem
import com.manjaniq.core.models.EngineState
import com.manjaniq.service.AetherService
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.theme.ManjaniqTheme
import java.util.Locale

private val AetherNeonBlue = Color(0xFF00D5FF)

class ServerAetherActivity : ComponentActivity() {

    private val viewModel: ServerAetherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AetherSettingsStorage.init(applicationContext)
        val editGuid = intent.getStringExtra("guid").orEmpty()
        val isRunning = intent.getBooleanExtra("isRunning", false)
        setContent {
            ManjaniqTheme {
                ServerAetherScreen(
                    guid = editGuid,
                    isRunning = isRunning,
                    viewModel = viewModel,
                    onOpenKeys = { startActivity(Intent(this, ServerAetherKeysActivity::class.java)) },
                    onFinish = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerAetherScreen(
    guid: String,
    isRunning: Boolean,
    viewModel: ServerAetherViewModel,
    onOpenKeys: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val initialConfig = remember(guid) {
        if (guid.isNotEmpty()) {
            AetherSettingsStorage.decodeServerConfig(guid) ?: AetherSettingsStorage.getActiveProfile(context)
        } else {
            AetherSettingsStorage.getActiveProfile(context)
        }
    }

    var state by remember { mutableStateOf(AetherUiState.fromProfile(initialConfig)) }
    val initialSnapshot = remember { state }
    val hasUnsavedChanges = state != initialSnapshot
    var activeDialog by remember { mutableStateOf<AetherDialog?>(null) }

    val scanState by viewModel.scanState.collectAsState()
    val isCoreAvailable by viewModel.isCoreAvailable.collectAsState()
    val isPsiphonAvailable by viewModel.isPsiphonAvailable.collectAsState()
    val isTorTransportsAvailable by viewModel.isTorTransportsAvailable.collectAsState()
    val session by viewModel.session.collectAsState()
    val psiphonRegions by viewModel.psiphonRegions.collectAsState()
    val listenPort by viewModel.listenPort.collectAsState()
    val takenPorts by viewModel.takenPorts.collectAsState()
    val keysCheck by viewModel.keysCheck.collectAsState()
    val log by viewModel.log.collectAsState()

    val isScanning = scanState is AetherScanState.Scanning

    LaunchedEffect(state.protocol) {
        viewModel.showIdentity(state.protocol)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSession()
        viewModel.showIdentity(state.protocol)
    }

    LaunchedEffect(scanState) {
        when (val current = scanState) {
            is AetherScanState.Found -> {
                val found: AetherScanResult = current.result
                state = if (state.protocol.twoHops) {
                    val outerText = found.primary.toString()
                    val innerText = found.secondary?.toString().orEmpty()
                    state.copy(
                        wiwOuter = outerText,
                        wiwInner = innerText.ifEmpty { state.wiwInner },
                    )
                } else {
                    state.copy(
                        server = found.primary.host,
                        port = found.primary.port.toString(),
                    )
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.aether_scan_success, found.format()),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.onScanHandled()
            }
            AetherScanState.NotFound -> {
                Toast.makeText(context, R.string.aether_scan_failed, Toast.LENGTH_SHORT).show()
                viewModel.onScanHandled()
            }
            AetherScanState.Idle, AetherScanState.Scanning -> Unit
        }
    }

    fun buildCandidate(): ProfileItem = initialConfig.copy().also { state.applyTo(it, listenPort) }

    fun persistConfig(config: ProfileItem) {
        if (guid.isNotEmpty()) {
            AetherSettingsStorage.encodeServerConfig(guid, config)
        } else {
            AetherSettingsStorage.saveActiveProfile(context, config)
        }
        Toast.makeText(context, R.string.toast_success, Toast.LENGTH_SHORT).show()
        onFinish()
    }

    fun validateForAction(config: ProfileItem): Boolean {
        if (!isPsiphonAvailable && state.psiphon != AetherPsiphon.OFF) {
            Toast.makeText(context, R.string.aether_psiphon_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isTorTransportsAvailable && state.tor != AetherTor.OFF && state.torBridges == AetherTorBridges.OWN) {
            Toast.makeText(context, R.string.aether_tor_transports_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        val problem = AetherFmt.normalize(config, takenPorts, listenPort) ?: return true
        val message = messageOfAetherProblem(problem, config, takenPorts, listenPort)
        val text = if (message.argument != null) {
            context.getString(message.id, message.argument)
        } else {
            context.getString(message.id)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        return false
    }

    fun saveServer() {
        val candidate = buildCandidate()
        val normalized = candidate.copy()
        if (!validateForAction(normalized)) return
        viewModel.checkKeysBeforeSave(normalized, candidate)
    }

    fun startScan() {
        val candidate = buildCandidate()
        val normalized = candidate.copy()
        if (!validateForAction(normalized)) return
        viewModel.scan(candidate)
    }

    LaunchedEffect(keysCheck) {
        when (val current = keysCheck) {
            is AetherKeysCheck.SaveReady -> {
                val candidate = buildCandidate()
                if (holdsChecked(candidate, current.profile, listenPort)) {
                    val normalized = candidate.copy()
                    if (validateForAction(normalized)) {
                        persistConfig(normalized)
                    }
                }
                viewModel.onKeysCheckHandled()
            }
            is AetherKeysCheck.Missing -> {
                if (holdsChecked(buildCandidate(), current.profile, listenPort)) {
                    activeDialog = AetherDialog.MissingKeys(current.scan, current.profile)
                }
                viewModel.onKeysCheckHandled()
            }
            is AetherKeysCheck.Refused -> {
                val text = if (current.args.isEmpty()) {
                    context.getString(current.resId)
                } else {
                    context.getString(current.resId, *current.args.toTypedArray())
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                viewModel.onKeysCheckHandled()
            }
            null -> Unit
        }
    }

    fun handleBack() {
        if (hasUnsavedChanges) {
            activeDialog = AetherDialog.DiscardChanges
        } else {
            onFinish()
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        handleBack()
    }

    when (val dialog = activeDialog) {
        is AetherDialog.MissingKeys -> {
            val protocolEntries = stringArrayResource(R.array.aether_protocol_entries)
            val protocolValues = stringArrayResource(R.array.aether_protocol_values)
            val effectiveProtocol = AetherFmt.effectiveProtocol(dialog.profile, listenPort)
            val protocolIndex = protocolValues.indexOf(effectiveProtocol.type)
            val protocolLabel = protocolEntries.getOrElse(protocolIndex) { effectiveProtocol.type }
            ThreeChoiceDialog(
                message = stringResource(R.string.aether_keys_missing, protocolLabel),
                confirmText = stringResource(R.string.aether_action_get_keys),
                secondaryText = stringResource(
                    if (dialog.scan) R.string.aether_action_scan_anyway else R.string.aether_action_save_anyway
                ),
                onConfirm = {
                    activeDialog = null
                    viewModel.onKeysCheckHandled()
                    onOpenKeys()
                },
                onSecondary = {
                    activeDialog = null
                    viewModel.onKeysCheckHandled()
                    if (dialog.scan) {
                        viewModel.scan(dialog.profile, anyway = true)
                    } else {
                        val candidate = dialog.profile.copy()
                        if (validateForAction(candidate)) {
                            persistConfig(candidate)
                        }
                    }
                },
                onDismiss = {
                    activeDialog = null
                    viewModel.onKeysCheckHandled()
                },
            )
        }
        AetherDialog.DiscardChanges -> {
            ConfirmDialog(
                message = stringResource(R.string.reset_msg),
                confirmText = stringResource(android.R.string.ok),
                onConfirm = {
                    activeDialog = null
                    onFinish()
                },
                onDismiss = { activeDialog = null },
            )
        }
        AetherDialog.DeleteConfirm -> {
            ConfirmDialog(
                message = stringResource(R.string.menu_item_del_config),
                confirmText = stringResource(android.R.string.ok),
                onConfirm = {
                    activeDialog = null
                    if (guid.isNotEmpty()) {
                        AetherSettingsStorage.removeServer(guid)
                    }
                    onFinish()
                },
                onDismiss = { activeDialog = null },
            )
        }
        null -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aether_setting)) },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    if (guid.isNotEmpty() && !isRunning) {
                        IconButton(onClick = { activeDialog = AetherDialog.DeleteConfirm }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.menu_item_del_config),
                            )
                        }
                    }
                    IconButton(onClick = ::saveServer) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.menu_item_save_config),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ServerAetherContent(
            state = state,
            onStateChange = { state = it },
            isCoreAvailable = isCoreAvailable,
            isPsiphonAvailable = isPsiphonAvailable,
            isTorTransportsAvailable = isTorTransportsAvailable,
            isScanning = isScanning,
            session = session,
            psiphonRegions = psiphonRegions,
            listenPort = listenPort,
            log = log,
            onScan = ::startScan,
            onCancelScan = viewModel::cancelScan,
            onOpenKeys = onOpenKeys,
            onClearPsiphonData = viewModel::clearPsiphonData,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        )
    }
}

@Composable
fun AetherHomePanel(
    viewModel: ServerAetherViewModel = viewModel(),
) {
    val context = LocalContext.current
    val initialConfig = remember {
        AetherSettingsStorage.getActiveProfile(context).apply {
            if (remarks.isBlank()) remarks = "Aether"
        }
    }
    var state by remember { mutableStateOf(AetherUiState.fromProfile(initialConfig)) }
    var activeDialog by remember { mutableStateOf<AetherDialog?>(null) }

    val serviceState by AetherService.state.collectAsState()
    val serviceError by AetherService.lastError.collectAsState()
    val liveLogs by AetherService.liveLogs.collectAsState()

    val scanState by viewModel.scanState.collectAsState()
    val isCoreAvailable by viewModel.isCoreAvailable.collectAsState()
    val isPsiphonAvailable by viewModel.isPsiphonAvailable.collectAsState()
    val isTorTransportsAvailable by viewModel.isTorTransportsAvailable.collectAsState()
    val session by viewModel.session.collectAsState()
    val psiphonRegions by viewModel.psiphonRegions.collectAsState()
    val listenPort by viewModel.listenPort.collectAsState()
    val takenPorts by viewModel.takenPorts.collectAsState()
    val keysCheck by viewModel.keysCheck.collectAsState()
    val editorLogs by viewModel.log.collectAsState()

    val isScanning = scanState is AetherScanState.Scanning
    val running = serviceState == EngineState.RUNNING
    val starting = serviceState == EngineState.STARTING
    val stopping = serviceState == EngineState.STOPPING
    val activeOrStarting = running || starting
    val transition = starting || stopping

    LaunchedEffect(state.protocol) {
        viewModel.showIdentity(state.protocol)
    }

    LaunchedEffect(serviceState) {
        viewModel.refreshSession()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSession()
        viewModel.showIdentity(state.protocol)
    }

    LaunchedEffect(scanState) {
        when (val current = scanState) {
            is AetherScanState.Found -> {
                val found: AetherScanResult = current.result
                state = if (state.protocol.twoHops) {
                    val outerText = found.primary.toString()
                    val innerText = found.secondary?.toString().orEmpty()
                    state.copy(
                        wiwOuter = outerText,
                        wiwInner = innerText.ifEmpty { state.wiwInner },
                    )
                } else {
                    state.copy(
                        server = found.primary.host,
                        port = found.primary.port.toString(),
                    )
                }
                val updated = initialConfig.copy().also { state.applyTo(it, listenPort) }
                AetherSettingsStorage.saveActiveProfile(context, updated)
                Toast.makeText(
                    context,
                    context.getString(R.string.aether_scan_success, found.format()),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.onScanHandled()
            }
            AetherScanState.NotFound -> {
                Toast.makeText(context, R.string.aether_scan_failed, Toast.LENGTH_SHORT).show()
                viewModel.onScanHandled()
            }
            AetherScanState.Idle, AetherScanState.Scanning -> Unit
        }
    }

    fun buildCandidate(): ProfileItem = initialConfig.copy().also {
        state.applyTo(it, listenPort)
    }

    fun validateForAction(config: ProfileItem): Boolean {
        if (!isPsiphonAvailable && state.psiphon != AetherPsiphon.OFF) {
            Toast.makeText(context, R.string.aether_psiphon_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isTorTransportsAvailable && state.tor != AetherTor.OFF && state.torBridges == AetherTorBridges.OWN) {
            Toast.makeText(context, R.string.aether_tor_transports_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        val problem = AetherFmt.normalize(config, takenPorts, listenPort) ?: return true
        val message = messageOfAetherProblem(problem, config, takenPorts, listenPort)
        val text = if (message.argument != null) {
            context.getString(message.id, message.argument)
        } else {
            context.getString(message.id)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        return false
    }

    fun openKeysActivity() {
        context.startActivity(Intent(context, ServerAetherKeysActivity::class.java))
    }

    fun persistConfig(config: ProfileItem) {
        AetherSettingsStorage.saveActiveProfile(context, config)
        Toast.makeText(context, R.string.toast_success, Toast.LENGTH_SHORT).show()
    }

    fun saveAndStart() {
        val candidate = buildCandidate()
        val normalized = candidate.copy()
        if (!validateForAction(normalized)) return
        AetherSettingsStorage.saveActiveProfile(context, normalized)
        AetherService.requestStart(context)
    }

    fun saveServer() {
        val candidate = buildCandidate()
        val normalized = candidate.copy()
        if (!validateForAction(normalized)) return
        viewModel.checkKeysBeforeSave(normalized, candidate)
    }

    fun startScan() {
        val candidate = buildCandidate()
        val normalized = candidate.copy()
        if (!validateForAction(normalized)) return
        viewModel.scan(candidate)
    }

    LaunchedEffect(keysCheck) {
        when (val current = keysCheck) {
            is AetherKeysCheck.SaveReady -> {
                val candidate = buildCandidate()
                if (holdsChecked(candidate, current.profile, listenPort)) {
                    val normalized = candidate.copy()
                    if (validateForAction(normalized)) {
                        persistConfig(normalized)
                    }
                }
                viewModel.onKeysCheckHandled()
            }
            is AetherKeysCheck.Missing -> {
                if (holdsChecked(buildCandidate(), current.profile, listenPort)) {
                    activeDialog = AetherDialog.MissingKeys(current.scan, current.profile)
                }
                viewModel.onKeysCheckHandled()
            }
            is AetherKeysCheck.Refused -> {
                val text = if (current.args.isEmpty()) {
                    context.getString(current.resId)
                } else {
                    context.getString(current.resId, *current.args.toTypedArray())
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                viewModel.onKeysCheckHandled()
            }
            null -> Unit
        }
    }

    when (val dialog = activeDialog) {
        is AetherDialog.MissingKeys -> {
            val protocolEntries = stringArrayResource(R.array.aether_protocol_entries)
            val protocolValues = stringArrayResource(R.array.aether_protocol_values)
            val effectiveProtocol = AetherFmt.effectiveProtocol(dialog.profile, listenPort)
            val protocolIndex = protocolValues.indexOf(effectiveProtocol.type)
            val protocolLabel = protocolEntries.getOrElse(protocolIndex) { effectiveProtocol.type }
            ThreeChoiceDialog(
                message = stringResource(R.string.aether_keys_missing, protocolLabel),
                confirmText = stringResource(R.string.aether_action_get_keys),
                secondaryText = stringResource(
                    if (dialog.scan) R.string.aether_action_scan_anyway else R.string.aether_action_save_anyway
                ),
                onConfirm = {
                    activeDialog = null
                    viewModel.onKeysCheckHandled()
                    openKeysActivity()
                },
                onSecondary = {
                    activeDialog = null
                    viewModel.onKeysCheckHandled()
                    if (dialog.scan) {
                        viewModel.scan(dialog.profile, anyway = true)
                    } else {
                        val candidate = dialog.profile.copy()
                        if (validateForAction(candidate)) {
                            persistConfig(candidate)
                        }
                    }
                },
                onDismiss = {
                    activeDialog = null
                    viewModel.onKeysCheckHandled()
                },
            )
        }
        else -> Unit
    }

    val combinedLogs = remember(editorLogs, liveLogs) {
        (editorLogs + liveLogs).takeLast(ServerAetherViewModel.LOG_CAPACITY)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (serviceError != null) {
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
                        text = serviceError.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { AetherService.clearError() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (activeOrStarting) {
                    AetherService.requestStop(context)
                } else {
                    saveAndStart()
                }
            },
            enabled = !stopping && (activeOrStarting || (isCoreAvailable && !isScanning)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(TestTags.AETHER_TOGGLE),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (activeOrStarting) Color(0xFFB3261E) else AetherNeonBlue,
                contentColor = if (activeOrStarting) Color.White else Color(0xFF001824),
            ),
        ) {
            if (transition) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = if (activeOrStarting) Color.White else Color(0xFF001824),
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else if (starting) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = when {
                    starting -> stringResource(R.string.aether_action_cancel_start)
                    stopping -> stringResource(R.string.stopping)
                    running -> stringResource(R.string.stop_engine)
                    else -> stringResource(R.string.start_engine)
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
        }

        ServerAetherForm(
            state = state,
            onStateChange = { state = it },
            isCoreAvailable = isCoreAvailable,
            isPsiphonAvailable = isPsiphonAvailable,
            isTorTransportsAvailable = isTorTransportsAvailable,
            isScanning = isScanning,
            isStarting = starting,
            session = session,
            psiphonRegions = psiphonRegions,
            listenPort = listenPort,
            log = combinedLogs,
            onScan = ::startScan,
            onCancelScan = viewModel::cancelScan,
            onOpenKeys = ::openKeysActivity,
            onClearPsiphonData = viewModel::clearPsiphonData,
            onSave = ::saveServer,
        )
    }
}

@Composable
internal fun ServerAetherContent(
    state: AetherUiState,
    onStateChange: (AetherUiState) -> Unit,
    isCoreAvailable: Boolean,
    isPsiphonAvailable: Boolean,
    isTorTransportsAvailable: Boolean,
    isScanning: Boolean,
    session: AetherSession?,
    psiphonRegions: List<String>,
    listenPort: Int,
    log: List<AetherLogEntry>,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenKeys: () -> Unit,
    onClearPsiphonData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ServerAetherForm(
            state = state,
            onStateChange = onStateChange,
            isCoreAvailable = isCoreAvailable,
            isPsiphonAvailable = isPsiphonAvailable,
            isTorTransportsAvailable = isTorTransportsAvailable,
            isScanning = isScanning,
            isStarting = false,
            session = session,
            psiphonRegions = psiphonRegions,
            listenPort = listenPort,
            log = log,
            onScan = onScan,
            onCancelScan = onCancelScan,
            onOpenKeys = onOpenKeys,
            onClearPsiphonData = onClearPsiphonData,
            onSave = null,
        )
    }
}

@Composable
private fun ServerAetherForm(
    state: AetherUiState,
    onStateChange: (AetherUiState) -> Unit,
    isCoreAvailable: Boolean,
    isPsiphonAvailable: Boolean,
    isTorTransportsAvailable: Boolean,
    isScanning: Boolean,
    isStarting: Boolean = false,
    session: AetherSession?,
    psiphonRegions: List<String>,
    listenPort: Int,
    log: List<AetherLogEntry>,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenKeys: () -> Unit,
    onClearPsiphonData: () -> Unit,
    onSave: (() -> Unit)?,
) {
    val protocolEntries = stringArrayResource(R.array.aether_protocol_entries).toList()
    val protocolValues = stringArrayResource(R.array.aether_protocol_values).toList()
    val transportEntries = stringArrayResource(R.array.aether_transport_entries).toList()
    val transportValues = stringArrayResource(R.array.aether_transport_values).toList()
    val scanEntries = stringArrayResource(R.array.aether_scan_entries).toList()
    val scanValues = stringArrayResource(R.array.aether_scan_values).toList()
    val obfuscationEntries = stringArrayResource(R.array.aether_obfuscation_entries).toList()
    val obfuscationValues = stringArrayResource(R.array.aether_obfuscation_values).toList()
    val ipEntries = stringArrayResource(R.array.aether_ip_entries).toList()
    val ipValues = stringArrayResource(R.array.aether_ip_values).toList()
    val allPsiphonEntries = stringArrayResource(R.array.aether_psiphon_entries).toList()
    val allPsiphonValues = stringArrayResource(R.array.aether_psiphon_values).toList()
    val (psiphonValues, psiphonEntries) = remember(allPsiphonValues, allPsiphonEntries, isPsiphonAvailable, state.psiphon, state.protocol, state.tor) {
        val allowed = when {
            !isPsiphonAvailable && state.psiphon == AetherPsiphon.OFF -> setOf(AetherPsiphon.OFF.type)
            state.tor == AetherTor.ONLY -> setOf(AetherPsiphon.OFF.type, state.psiphon.type)
            else -> buildSet {
                add(AetherPsiphon.OFF.type)
                add(AetherPsiphon.ONLY.type)
                if (state.tor != AetherTor.CHAIN) add(AetherPsiphon.CHAIN.type)
                if (state.protocol.overMasque && state.tor != AetherTor.REVERSE) add(AetherPsiphon.REVERSE.type)
                add(state.psiphon.type)
            }
        }
        allPsiphonValues.zip(allPsiphonEntries)
            .filter { (value, _) -> value in allowed }
            .unzip()
    }
    val psiphonModeEntries = stringArrayResource(R.array.aether_psiphon_mode_entries).toList()
    val psiphonModeValues = stringArrayResource(R.array.aether_psiphon_mode_values).toList()
    val psiphonCdnSetEntries = stringArrayResource(R.array.aether_psiphon_cdn_set_entries).toList()
    val psiphonCdnSetOptions = remember { AetherPsiphonCdnSet.entries }
    val psiphonAnyRegionLabel = stringResource(R.string.aether_psiphon_region_any)
    val (psiphonRegionValues, psiphonRegionEntries) = remember(psiphonRegions, state.psiphonRegion, psiphonAnyRegionLabel) {
        val selected = state.psiphonRegion.trim().uppercase()
        val codes = if (selected.isNotEmpty() && selected !in psiphonRegions) {
            (psiphonRegions + selected).sorted()
        } else {
            psiphonRegions
        }
        (listOf("") + codes) to (listOf(psiphonAnyRegionLabel) + codes.map(::psiphonRegionDisplayName))
    }
    val allTorEntries = stringArrayResource(R.array.aether_tor_entries).toList()
    val allTorValues = stringArrayResource(R.array.aether_tor_values).toList()
    val (torValues, torEntries) = remember(allTorValues, allTorEntries, state.tor, state.protocol, state.psiphon) {
        val allowed = if (state.psiphon == AetherPsiphon.ONLY) {
            setOf(AetherTor.OFF.type, state.tor.type)
        } else {
            buildSet {
                add(AetherTor.OFF.type)
                add(AetherTor.ONLY.type)
                if (state.psiphon != AetherPsiphon.CHAIN) add(AetherTor.CHAIN.type)
                if (state.protocol.overMasque && state.psiphon != AetherPsiphon.REVERSE) add(AetherTor.REVERSE.type)
                add(state.tor.type)
            }
        }
        allTorValues.zip(allTorEntries)
            .filter { (value, _) -> value in allowed }
            .unzip()
    }
    val allTorBridgesEntries = stringArrayResource(R.array.aether_tor_bridges_entries).toList()
    val allTorBridgesValues = stringArrayResource(R.array.aether_tor_bridges_values).toList()
    val (torBridgesValues, torBridgesEntries) = remember(allTorBridgesValues, allTorBridgesEntries, isTorTransportsAvailable, state.torBridges) {
        allTorBridgesValues.zip(allTorBridgesEntries)
            .filter { (value, _) ->
                isTorTransportsAvailable || value != AetherTorBridges.OWN.type || state.torBridges == AetherTorBridges.OWN
            }
            .unzip()
    }
    val torRelaysEntries = stringArrayResource(R.array.aether_tor_relays_entries).toList()
    val torRelaysValues = stringArrayResource(R.array.aether_tor_relays_values).toList()
    val fingerprintEntries = stringArrayResource(R.array.aether_fingerprint_entries).toList()
    val fingerprintValues = stringArrayResource(R.array.aether_fingerprint_values).toList()
    val echDnsOptions = stringArrayResource(R.array.aether_ech_dns_options).toList()
    val echDomainOptions = stringArrayResource(R.array.aether_ech_domain_options).toList()
    val upstreamOptions = stringArrayResource(R.array.aether_upstream_options).toList()

    val builtCommand = remember(state, listenPort) {
        val draft = ProfileItem.create().also { state.copy(command = "").applyTo(it, listenPort) }
        AetherFmt.builtCommand(draft, listenPort)
    }
    val writtenCommand = state.command.trim()
    val isCustomCommand = writtenCommand.isNotEmpty() && writtenCommand != builtCommand
    val displayedCommand = if (isCustomCommand) state.command else builtCommand
    val fieldsEnabled = !isScanning && !isStarting && !isCustomCommand

    if (!isCoreAvailable) {
        Text(
            text = stringResource(R.string.aether_unsupported_abi),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
    }

    SectionHeader(title = stringResource(R.string.aether_setting))

    if (state.warpRuns) {
        FormDropdownField(
            label = stringResource(R.string.aether_lab_protocol),
            selectedValue = state.protocol.type,
            options = protocolValues,
            optionLabels = protocolEntries,
            enabled = fieldsEnabled,
            onValueChange = { value ->
                val next = AetherProtocol.fromString(value)
                onStateChange(
                    state.copy(
                        protocol = next,
                        psiphon = if (!next.overMasque && state.psiphon == AetherPsiphon.REVERSE) AetherPsiphon.OFF else state.psiphon,
                        tor = if (!next.overMasque && state.tor == AetherTor.REVERSE) AetherTor.OFF else state.tor,
                    )
                )
            },
        )
    }

    if (state.overMasque) {
        FormDropdownField(
            label = stringResource(R.string.aether_lab_transport),
            selectedValue = state.transport.type,
            options = transportValues,
            optionLabels = transportEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(transport = AetherTransport.fromString(it))) },
        )

        FormTextField(
            label = stringResource(R.string.aether_lab_masque_sni),
            value = state.masqueSni,
            onValueChange = { onStateChange(state.copy(masqueSni = it)) },
            placeholder = AetherCoreManager.AETHER_MASQUE_SNI,
            keyboardType = KeyboardType.Uri,
            enabled = fieldsEnabled,
        )

        FormDropdownField(
            label = stringResource(R.string.aether_lab_fingerprint),
            selectedValue = state.fingerprint.type,
            options = fingerprintValues,
            optionLabels = fingerprintEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(fingerprint = AetherFingerprint.fromString(it))) },
        )

        SettingsSwitch(
            title = stringResource(R.string.aether_lab_fragment),
            checked = state.fragment,
            enabled = fieldsEnabled,
            onCheckedChange = { onStateChange(state.copy(fragment = it)) },
        )

        if (state.fragment) {
            FormTextField(
                label = stringResource(R.string.aether_lab_fragment_size),
                value = state.fragmentSize,
                onValueChange = { onStateChange(state.copy(fragmentSize = it)) },
                supportingText = stringResource(R.string.aether_hint_fragment_size),
                keyboardType = KeyboardType.Ascii,
                enabled = fieldsEnabled,
            )

            FormTextField(
                label = stringResource(R.string.aether_lab_fragment_delay),
                value = state.fragmentDelay,
                onValueChange = { onStateChange(state.copy(fragmentDelay = it)) },
                supportingText = stringResource(R.string.aether_hint_fragment_delay),
                keyboardType = KeyboardType.Ascii,
                enabled = fieldsEnabled,
            )
        }

        SettingsSwitch(
            title = stringResource(R.string.aether_lab_ech),
            summary = stringResource(R.string.aether_hint_ech),
            checked = state.ech,
            enabled = fieldsEnabled,
            onCheckedChange = { enabled ->
                onStateChange(
                    state.copy(
                        ech = enabled,
                        echDns = if (enabled) state.echDns.ifEmpty { AetherCoreManager.AETHER_ECH_DNS } else state.echDns,
                        echDomain = if (enabled) state.echDomain.ifEmpty { AetherCoreManager.AETHER_ECH_DOMAIN } else state.echDomain,
                    )
                )
            },
        )

        if (state.ech) {
            FormComboField(
                label = stringResource(R.string.aether_lab_ech_dns),
                value = state.echDns,
                options = echDnsOptions,
                onValueChange = { onStateChange(state.copy(echDns = it)) },
                enabled = fieldsEnabled,
            )

            FormComboField(
                label = stringResource(R.string.aether_lab_ech_domain),
                value = state.echDomain,
                options = echDomainOptions,
                onValueChange = { onStateChange(state.copy(echDomain = it)) },
                enabled = fieldsEnabled,
            )
        }
    }

    if (state.warpRuns) {
        FormDropdownField(
            label = stringResource(R.string.aether_lab_scan_mode),
            selectedValue = state.scanMode.type,
            options = scanValues,
            optionLabels = scanEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(scanMode = AetherScanMode.fromString(it))) },
        )

        FormDropdownField(
            label = stringResource(R.string.aether_lab_obfuscation),
            selectedValue = state.obfuscation.type,
            options = obfuscationValues,
            optionLabels = obfuscationEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(obfuscation = AetherObfuscation.fromString(it))) },
        )

        FormDropdownField(
            label = stringResource(R.string.aether_lab_ip_version),
            selectedValue = state.ipVersion.type,
            options = ipValues,
            optionLabels = ipEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(ipVersion = AetherIpVersion.fromString(it))) },
        )
    }

    FormTextField(
        label = stringResource(R.string.aether_lab_dns),
        value = state.dns,
        onValueChange = { onStateChange(state.copy(dns = it)) },
        placeholder = "1.1.1.1,8.8.8.8",
        supportingText = stringResource(R.string.aether_hint_dns),
        keyboardType = KeyboardType.Ascii,
        enabled = fieldsEnabled,
    )

    FormComboField(
        label = stringResource(R.string.aether_lab_upstream),
        value = state.upstream,
        options = upstreamOptions,
        onValueChange = { onStateChange(state.copy(upstream = it)) },
        supportingText = stringResource(R.string.aether_hint_upstream),
        enabled = fieldsEnabled,
    )

    if (state.warpRuns && state.psiphon != AetherPsiphon.OFF) {
        FormTextField(
            label = stringResource(R.string.aether_lab_exit_loc),
            value = state.exitLoc,
            onValueChange = { onStateChange(state.copy(exitLoc = it)) },
            placeholder = "DE,NL",
            supportingText = stringResource(R.string.aether_hint_exit_loc),
            keyboardType = KeyboardType.Ascii,
            enabled = fieldsEnabled,
        )
    }

    FormDropdownField(
        label = stringResource(R.string.aether_lab_psiphon),
        selectedValue = state.psiphon.type,
        options = psiphonValues,
        optionLabels = psiphonEntries,
        supportingText = if (!isPsiphonAvailable) stringResource(R.string.aether_psiphon_unavailable) else null,
        enabled = fieldsEnabled && (isPsiphonAvailable || state.psiphon != AetherPsiphon.OFF),
        onValueChange = { value ->
            val next = AetherPsiphon.fromString(value)
            val clearTor = (next == AetherPsiphon.ONLY && state.tor != AetherTor.OFF) ||
                (next == AetherPsiphon.CHAIN && state.tor == AetherTor.CHAIN) ||
                (next == AetherPsiphon.REVERSE && state.tor == AetherTor.REVERSE)
            onStateChange(
                state.copy(
                    psiphon = next,
                    tor = if (clearTor) AetherTor.OFF else state.tor,
                )
            )
        },
    )

    if (state.psiphon != AetherPsiphon.OFF) {
        FormDropdownField(
            label = stringResource(R.string.aether_lab_psiphon_mode),
            selectedValue = state.psiphonMode.type,
            options = psiphonModeValues,
            optionLabels = psiphonModeEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(psiphonMode = AetherPsiphonMode.fromString(it))) },
        )

        if (state.psiphonMode != AetherPsiphonMode.DIRECT) {
            FormMultiSelectField(
                label = stringResource(R.string.aether_lab_psiphon_cdn_sets),
                options = psiphonCdnSetOptions,
                optionLabels = psiphonCdnSetEntries,
                selectedOptions = state.psiphonCdnSets,
                onSelectionChange = { onStateChange(state.copy(psiphonCdnSets = it)) },
                allSelectedSummary = stringResource(R.string.aether_psiphon_cdn_sets_all),
                supportingText = stringResource(R.string.aether_hint_psiphon_cdn_sets),
                enabled = fieldsEnabled,
            )

            FormTextField(
                label = stringResource(R.string.aether_lab_psiphon_cdn_ips),
                value = state.psiphonCdnIps,
                onValueChange = { onStateChange(state.copy(psiphonCdnIps = it)) },
                supportingText = stringResource(R.string.aether_hint_psiphon_list),
                keyboardType = KeyboardType.Ascii,
                enabled = fieldsEnabled,
            )

            FormTextField(
                label = stringResource(R.string.aether_lab_psiphon_cdn_sni),
                value = state.psiphonCdnSni,
                onValueChange = { onStateChange(state.copy(psiphonCdnSni = it)) },
                supportingText = stringResource(R.string.aether_hint_psiphon_list),
                keyboardType = KeyboardType.Uri,
                enabled = fieldsEnabled,
            )
        }

        if (state.psiphon != AetherPsiphon.REVERSE) {
            FormDropdownField(
                label = stringResource(R.string.aether_lab_psiphon_region),
                selectedValue = state.psiphonRegion,
                options = psiphonRegionValues,
                optionLabels = psiphonRegionEntries,
                enabled = fieldsEnabled,
                onValueChange = { onStateChange(state.copy(psiphonRegion = it)) },
            )
        }

        SettingsSwitch(
            title = stringResource(R.string.aether_lab_psiphon_bundled_list),
            summary = stringResource(R.string.aether_hint_psiphon_bundled_list),
            checked = state.psiphonBundledList,
            enabled = fieldsEnabled,
            onCheckedChange = { onStateChange(state.copy(psiphonBundledList = it)) },
        )

        OutlinedButton(
            onClick = onClearPsiphonData,
            enabled = !isScanning && session == null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.aether_action_clear_psiphon))
        }
        Text(
            text = stringResource(
                if (session != null) R.string.aether_psiphon_clear_blocked else R.string.aether_hint_clear_psiphon
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
        )
    }

    FormDropdownField(
        label = stringResource(R.string.aether_lab_tor),
        selectedValue = state.tor.type,
        options = torValues,
        optionLabels = torEntries,
        enabled = fieldsEnabled,
        onValueChange = { value ->
            val next = AetherTor.fromString(value)
            val clearPsiphon = (next == AetherTor.ONLY && state.psiphon != AetherPsiphon.OFF) ||
                (next == AetherTor.CHAIN && state.psiphon == AetherPsiphon.CHAIN) ||
                (next == AetherTor.REVERSE && state.psiphon == AetherPsiphon.REVERSE)
            onStateChange(
                state.copy(
                    tor = next,
                    psiphon = if (clearPsiphon) AetherPsiphon.OFF else state.psiphon,
                )
            )
        },
    )

    if (state.tor != AetherTor.OFF) {
        FormDropdownField(
            label = stringResource(R.string.aether_lab_tor_bridges),
            selectedValue = state.torBridges.type,
            options = torBridgesValues,
            optionLabels = torBridgesEntries,
            enabled = fieldsEnabled,
            onValueChange = { onStateChange(state.copy(torBridges = AetherTorBridges.fromString(it))) },
        )

        if (state.torBridges == AetherTorBridges.AUTO || state.torBridges == AetherTorBridges.FIRST) {
            FormDropdownField(
                label = stringResource(R.string.aether_lab_tor_relays),
                selectedValue = state.torRelays.type,
                options = torRelaysValues,
                optionLabels = torRelaysEntries,
                enabled = fieldsEnabled,
                onValueChange = { onStateChange(state.copy(torRelays = AetherTorRelays.fromString(it))) },
            )
        }

        if (state.torBridges == AetherTorBridges.OWN) {
            FormTextField(
                label = stringResource(R.string.aether_lab_tor_bridge_lines),
                value = state.torBridgeLines,
                onValueChange = { onStateChange(state.copy(torBridgeLines = it)) },
                supportingText = stringResource(R.string.aether_hint_tor_bridge_lines),
                singleLine = false,
                minLines = 3,
                maxLines = 8,
                keyboardType = KeyboardType.Ascii,
                fontFamily = FontFamily.Monospace,
                enabled = fieldsEnabled,
            )
        }
    }

    if (state.usesSingleEndpoint) {
        SectionDivider()

        FormTextField(
            label = stringResource(R.string.server_lab_address),
            value = state.server,
            onValueChange = { onStateChange(state.copy(server = it)) },
            supportingText = stringResource(R.string.aether_hint_endpoint),
            keyboardType = KeyboardType.Uri,
            enabled = fieldsEnabled,
        )

        FormNumericField(
            label = stringResource(R.string.server_lab_port),
            value = state.port,
            onValueChange = { onStateChange(state.copy(port = it)) },
            imeAction = if (state.usesWiwInner) ImeAction.Next else ImeAction.Done,
            enabled = fieldsEnabled,
        )

        if (state.usesWiwInner) {
            FormTextField(
                label = stringResource(R.string.aether_lab_wiw_inner),
                value = state.wiwInner,
                onValueChange = { onStateChange(state.copy(wiwInner = it)) },
                supportingText = stringResource(R.string.aether_hint_gool_peer),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                enabled = fieldsEnabled,
            )
        }
    } else if (state.usesWiwOuter) {
        SectionDivider()

        FormTextField(
            label = stringResource(R.string.aether_lab_wiw_outer),
            value = state.wiwOuter,
            onValueChange = { onStateChange(state.copy(wiwOuter = it)) },
            supportingText = stringResource(R.string.aether_hint_endpoint),
            keyboardType = KeyboardType.Uri,
            enabled = fieldsEnabled,
        )

        FormTextField(
            label = stringResource(R.string.aether_lab_wiw_inner),
            value = state.wiwInner,
            onValueChange = { onStateChange(state.copy(wiwInner = it)) },
            supportingText = stringResource(R.string.aether_hint_endpoint),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            enabled = fieldsEnabled,
        )
    }

    SectionDivider()
    SectionHeader(title = stringResource(R.string.aether_lab_other_settings))

    FormTextField(
        label = stringResource(R.string.aether_lab_command),
        value = displayedCommand,
        onValueChange = { written ->
            val trimmed = written.trim()
            onStateChange(state.copy(command = if (trimmed.isEmpty() || trimmed == builtCommand) "" else written))
        },
        singleLine = false,
        minLines = 2,
        maxLines = 6,
        keyboardType = KeyboardType.Ascii,
        fontFamily = FontFamily.Monospace,
        supportingText = if (isCustomCommand) stringResource(R.string.aether_command_custom) else null,
        enabled = !isScanning,
    )

    if (isCustomCommand) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { onStateChange(state.copy(command = "")) },
                enabled = !isScanning,
            ) {
                Text(stringResource(R.string.aether_action_use_settings))
            }
        }
    }

    val sessionSharesKey = state.warpRuns && session?.sharesKeyWith(state.protocol) == true
    if (sessionSharesKey) {
        Text(
            text = stringResource(R.string.aether_scan_blocked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }

    if (state.warpRuns) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onScan,
                enabled = isCoreAvailable && !isScanning && !isStarting && !sessionSharesKey,
                modifier = Modifier.weight(1f),
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.aether_action_scanning))
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.aether_action_scan))
                }
            }

            if (isScanning) {
                OutlinedButton(onClick = onCancelScan) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_cancel))
                }
            } else {
                OutlinedButton(
                    onClick = onOpenKeys,
                    enabled = isCoreAvailable,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.aether_action_get_keys))
                }
            }
        }
    }

    if (onSave != null) {
        FilledTonalButton(
            onClick = onSave,
            enabled = !isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.menu_item_save_config))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    AetherLogPanel(entries = log)

    Spacer(modifier = Modifier.height(16.dp))
}

internal fun psiphonRegionDisplayName(code: String): String {
    val clean = code.trim().uppercase(Locale.ROOT)
    val name = Locale("", clean).displayCountry
    return if (name.isNotEmpty() && !name.equals(clean, ignoreCase = true)) {
        "$name ($clean)"
    } else {
        clean
    }
}

@Composable
internal fun AetherLogPanel(
    entries: List<AetherLogEntry>,
    @StringRes emptyResId: Int = R.string.aether_log_empty,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                title = stringResource(R.string.aether_log_title),
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val text = entries.joinToString("\n") { it.text.resolve(context) }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("aether_log", text))
                        Toast.makeText(context, R.string.toast_success, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.logcat_copy),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(emptyResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 260.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items = entries, key = { it.id }) { entry ->
                            val color = when (entry.priority) {
                                Log.ERROR -> MaterialTheme.colorScheme.error
                                Log.WARN -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = entry.text.resolve(context),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}
