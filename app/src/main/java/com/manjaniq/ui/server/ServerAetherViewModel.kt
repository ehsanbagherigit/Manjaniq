package com.manjaniq.ui.server

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manjaniq.R
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherExitNode
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.AetherIdentity
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherIdentityStatus
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherScanResult
import com.manjaniq.core.ExitNodeOutbound
import com.manjaniq.core.ProfileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

sealed interface AetherScanState {
    data object Idle : AetherScanState
    data object Scanning : AetherScanState
    data class Found(val result: AetherScanResult) : AetherScanState
    data object NotFound : AetherScanState
}

sealed interface AetherKeysCheck {
    data class SaveReady(val profile: ProfileItem) : AetherKeysCheck
    data class Missing(val scan: Boolean, val profile: ProfileItem) : AetherKeysCheck
    data class Refused(@StringRes val resId: Int, val args: List<String> = emptyList()) : AetherKeysCheck
}

internal fun holdsChecked(
    current: ProfileItem,
    checked: ProfileItem,
    listenPort: Int = AetherCoreManager.DEFAULT_SOCKS_PORT,
): Boolean {
    val first = current.copy(addedTime = 0L)
    val second = checked.copy(addedTime = 0L)
    AetherFmt.normalize(first, listenPort = listenPort)
    AetherFmt.normalize(second, listenPort = listenPort)
    return first == second
}

@Immutable
sealed interface AetherLogText {
    data class Raw(val value: String) : AetherLogText
    data class Resource(@StringRes val resId: Int, val args: List<String> = emptyList()) : AetherLogText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Resource -> if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args.toTypedArray())
        }
    }
}

@Immutable
data class AetherLogEntry(
    val id: Long,
    val priority: Int,
    val text: AetherLogText,
)

class ServerAetherViewModel @JvmOverloads constructor(
    application: Application,
    private val source: AetherEditorSource = AetherEditorRepository(application),
) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow<AetherScanState>(AetherScanState.Idle)
    val scanState: StateFlow<AetherScanState> = _scanState.asStateFlow()

    private val _isCoreAvailable = MutableStateFlow(true)
    val isCoreAvailable: StateFlow<Boolean> = _isCoreAvailable.asStateFlow()

    private val _isPsiphonAvailable = MutableStateFlow(true)
    val isPsiphonAvailable: StateFlow<Boolean> = _isPsiphonAvailable.asStateFlow()

    private val _isTorTransportsAvailable = MutableStateFlow(true)
    val isTorTransportsAvailable: StateFlow<Boolean> = _isTorTransportsAvailable.asStateFlow()

    private val _session = MutableStateFlow<AetherSession?>(null)
    val session: StateFlow<AetherSession?> = _session.asStateFlow()

    private val _psiphonRegions = MutableStateFlow<List<String>>(emptyList())
    val psiphonRegions: StateFlow<List<String>> = _psiphonRegions.asStateFlow()

    private val _listenPort = MutableStateFlow(AetherCoreManager.DEFAULT_SOCKS_PORT)
    val listenPort: StateFlow<Int> = _listenPort.asStateFlow()

    private val _takenPorts = MutableStateFlow<Set<Int>>(emptySet())
    val takenPorts: StateFlow<Set<Int>> = _takenPorts.asStateFlow()

    private val _exitNodes = MutableStateFlow<List<AetherExitNode>>(emptyList())
    val exitNodes: StateFlow<List<AetherExitNode>> = _exitNodes.asStateFlow()

    private val _keysCheck = MutableStateFlow<AetherKeysCheck?>(null)
    val keysCheck: StateFlow<AetherKeysCheck?> = _keysCheck.asStateFlow()

    private var keysCheckJob: Job? = null

    private val _log = MutableStateFlow<List<AetherLogEntry>>(emptyList())
    val log: StateFlow<List<AetherLogEntry>> = _log.asStateFlow()

    private val nextLogId = AtomicLong(1L)
    private var activeJob: Job? = null
    private var cancelling = false
    private var lastReportedStatus: AetherIdentityStatus? = null

    init {
        viewModelScope.launch {
            _isCoreAvailable.value = source.isCoreAvailable()
            _isPsiphonAvailable.value = source.isPsiphonAvailable()
            _isTorTransportsAvailable.value = source.isTorTransportsAvailable()
            _session.value = source.activeSession()
            _psiphonRegions.value = source.psiphonRegions()
            _listenPort.value = source.listenPort()
            _takenPorts.value = source.takenPorts()
            _exitNodes.value = source.exitNodes()
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            // Refresh packaged-helper availability along with live session state. This avoids keeping
            // stale capability UI after an ABI-specific install/update or an activity recreation.
            _isCoreAvailable.value = source.isCoreAvailable()
            _isPsiphonAvailable.value = source.isPsiphonAvailable()
            _isTorTransportsAvailable.value = source.isTorTransportsAvailable()
            _session.value = source.activeSession()
            _psiphonRegions.value = source.psiphonRegions()
            _listenPort.value = source.listenPort()
            _takenPorts.value = source.takenPorts()
            _exitNodes.value = source.exitNodes()
        }
    }

    fun showIdentity(protocol: AetherProtocol) {
        viewModelScope.launch {
            reportIdentity(protocol)
        }
    }

    fun clearPsiphonData() {
        if (activeJob?.isActive == true) return
        activeJob = viewModelScope.launch {
            val active = source.activeSession()
            _session.value = active
            if (active != null) {
                appendResource(Log.WARN, R.string.aether_psiphon_clear_blocked)
                return@launch
            }
            if (source.clearPsiphonData()) {
                _psiphonRegions.value = source.psiphonRegions()
                appendResource(Log.INFO, R.string.aether_log_psiphon_cleared)
            } else {
                appendResource(Log.ERROR, R.string.aether_log_psiphon_clear_failed)
            }
        }
    }

    fun scan(profile: ProfileItem, anyway: Boolean = false) {
        if (activeJob?.isActive == true) return
        cancelling = false
        activeJob = viewModelScope.launch {
            val active = source.activeSession()
            _session.value = active
            val protocol = AetherProtocol.fromString(profile.aetherProtocol)
            if (active?.sharesKeyWith(protocol) == true) {
                appendResource(Log.WARN, R.string.aether_scan_blocked)
                return@launch
            }
            val exitNode = profile.aetherExitNode?.trim().orEmpty()
            if (exitNode.isNotEmpty()) {
                when (val found = source.findExitNode(exitNode)) {
                    is ExitNodeOutbound.Built -> Unit
                    ExitNodeOutbound.NotFound, ExitNodeOutbound.SameName, ExitNodeOutbound.NoOutbound, ExitNodeOutbound.EchUnusable -> {
                        appendResource(Log.WARN, found.message, exitNode)
                        return@launch
                    }
                }
            }
            if (!anyway) {
                val needed = AetherIdentityManager.keyFilesOf(profile, scan = true)
                if (needed.isNotEmpty() && source.missingKeys(needed).isNotEmpty()) {
                    _keysCheck.value = AetherKeysCheck.Missing(scan = true, profile = profile)
                    return@launch
                }
            }
            _scanState.value = AetherScanState.Scanning
            appendResource(Log.INFO, R.string.aether_log_scan_started)
            try {
                val result = source.scan(profile, ::appendCoreLine)
                _psiphonRegions.value = source.psiphonRegions()
                if (result != null) {
                    appendEntry(Log.INFO, scanOutcome(result))
                    _scanState.value = AetherScanState.Found(result)
                } else {
                    appendResource(Log.WARN, R.string.aether_scan_failed)
                    _scanState.value = AetherScanState.NotFound
                }
                reportIdentity(protocol)
            } catch (cancelled: CancellationException) {
                _scanState.value = AetherScanState.Idle
                throw cancelled
            } finally {
                cancelling = false
            }
        }
    }

    fun checkKeysBeforeSave(profile: ProfileItem, held: ProfileItem = profile) {
        keysCheckJob?.cancel()
        val job = viewModelScope.launch {
            val exitNode = profile.aetherExitNode?.trim().orEmpty()
            if (exitNode.isNotEmpty()) {
                when (val found = source.findExitNode(exitNode)) {
                    is ExitNodeOutbound.Built -> Unit
                    ExitNodeOutbound.NotFound, ExitNodeOutbound.SameName, ExitNodeOutbound.NoOutbound, ExitNodeOutbound.EchUnusable -> {
                        _keysCheck.value = AetherKeysCheck.Refused(found.message, listOf(exitNode))
                        return@launch
                    }
                }
            }
            val needed = AetherIdentityManager.keyFilesOf(profile, scan = false)
            val missing = if (needed.isEmpty()) emptyList() else source.missingKeys(needed)
            _keysCheck.value = if (missing.isEmpty()) {
                AetherKeysCheck.SaveReady(held)
            } else {
                AetherKeysCheck.Missing(scan = false, profile = held)
            }
        }
        keysCheckJob = job
    }

    fun onKeysCheckHandled() {
        keysCheckJob?.cancel()
        keysCheckJob = null
        _keysCheck.value = null
    }

    fun cancelScan() {
        val job = activeJob ?: return
        if (!job.isActive || cancelling) return
        cancelling = true
        job.cancel()
        appendResource(Log.WARN, R.string.aether_log_scan_cancelled)
    }

    fun onScanHandled() {
        if (_scanState.value != AetherScanState.Scanning) {
            _scanState.value = AetherScanState.Idle
        }
    }

    fun clearLog() {
        lastReportedStatus = null
        _log.value = emptyList()
    }

    private suspend fun reportIdentity(protocol: AetherProtocol) {
        val status = source.identityStatus(protocol)
        if (status == lastReportedStatus) return
        lastReportedStatus = status
        identityLines(status).forEach { appendEntry(Log.INFO, it) }
    }

    private fun appendCoreLine(raw: String) {
        val parsed = parseCoreLine(raw) ?: return
        appendEntry(parsed.first, AetherLogText.Raw(parsed.second))
    }

    private fun appendResource(priority: Int, @StringRes resId: Int, vararg args: String) {
        appendEntry(priority, AetherLogText.Resource(resId, args.toList()))
    }

    private fun appendEntry(priority: Int, text: AetherLogText) {
        val entry = AetherLogEntry(
            id = nextLogId.getAndIncrement(),
            priority = priority,
            text = text,
        )
        _log.update { current ->
            if (current.size >= LOG_CAPACITY) {
                current.drop(current.size - LOG_CAPACITY + 1) + entry
            } else {
                current + entry
            }
        }
    }

    companion object {
        internal const val LOG_CAPACITY = 200

        private val coreLogPrefix =
            Regex("""^\[\d{4}-\d{2}-\d{2}T[^\s]+\s+(ERROR|WARN|INFO|DEBUG|TRACE)\s+[^\]]+]\s*""")

        internal fun parseCoreLine(raw: String): Pair<Int, String>? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val match = coreLogPrefix.find(trimmed)
            if (match != null) {
                val level = when (match.groupValues[1]) {
                    "ERROR" -> Log.ERROR
                    "WARN" -> Log.WARN
                    "DEBUG", "TRACE" -> Log.DEBUG
                    else -> Log.INFO
                }
                val body = trimmed.substring(match.range.last + 1).trim()
                return if (body.isNotEmpty()) level to body else null
            }
            val level = when {
                trimmed.startsWith("Error:") || trimmed.startsWith("error:") -> Log.ERROR
                trimmed.startsWith("[-]") -> Log.WARN
                else -> Log.INFO
            }
            return level to trimmed
        }

        internal fun scanOutcome(result: AetherScanResult): AetherLogText =
            if (result.secondary != null) {
                AetherLogText.Resource(
                    R.string.aether_log_scan_found_hops,
                    listOf(result.primary.format(), result.secondary.format()),
                )
            } else {
                AetherLogText.Resource(
                    R.string.aether_log_scan_found,
                    listOf(result.primary.format()),
                )
            }

        internal fun identityLines(status: AetherIdentityStatus): List<AetherLogText> =
            when (status.protocol) {
                AetherProtocol.WIREGUARD -> listOf(
                    keyLine(
                        status.primary,
                        R.string.aether_log_wireguard_key_ready,
                        R.string.aether_log_wireguard_key_missing,
                    )
                )
                AetherProtocol.MASQUE -> listOf(
                    keyLine(
                        status.primary,
                        R.string.aether_log_masque_key_ready,
                        R.string.aether_log_masque_key_missing,
                    )
                )
                AetherProtocol.GOOL, AetherProtocol.MIM, AetherProtocol.WG_OVER_MASQUE -> listOf(
                    keyLine(
                        status.primary,
                        R.string.aether_log_outer_key_ready,
                        R.string.aether_log_outer_key_missing,
                    ),
                    keyLine(
                        status.secondary,
                        R.string.aether_log_inner_key_ready,
                        R.string.aether_log_inner_key_missing,
                    ),
                )
            }

        internal fun keyLine(
            identity: AetherIdentity?,
            @StringRes readyRes: Int,
            @StringRes missingRes: Int,
        ): AetherLogText =
            if (identity != null) {
                AetherLogText.Resource(
                    readyRes,
                    listOf(identity.shortDeviceId, identity.ipv4, identity.ipv6),
                )
            } else {
                AetherLogText.Resource(missingRes)
            }
    }
}
