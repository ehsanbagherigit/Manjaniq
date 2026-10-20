package com.manjaniq.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.manjaniq.AetherBridge
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherState
import com.manjaniq.service.AetherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Single source of truth for Aether UI (mirrors EngineRepository for RSTA).
 * All state originates from AetherService or direct FFI polling; no invented state.
 */
data class AetherUiState(
    val state: AetherState = AetherState.STOPPED,
    val busy: Boolean = false,
    val lastError: String? = null,
    val version: String = "",
    val activeJobId: Long? = null,
    val identityId: Long? = null,
    val endpoint: String = "",
    val socksEndpoint: String = "127.0.0.1:1819",
    val isNativeLoaded: Boolean = false,
    val loadError: String? = null,
    val lastPollRaw: String? = null
)

object AetherRepository {

    private val _uiState = MutableStateFlow(
        AetherUiState(
            isNativeLoaded = AetherBridge.isNativeAvailable,
            loadError = AetherBridge.getLoadError()?.message
        )
    )
    val uiState: StateFlow<AetherUiState> = _uiState.asStateFlow()

    private val _logs = MutableStateFlow<List<AetherLog>>(emptyList())
    val logs: StateFlow<List<AetherLog>> = _logs.asStateFlow()

    // In-memory log ring (Aether has no GetLogs like RSTA; we synthesize from FFI + service)
    private val logRing = ArrayDeque<AetherLog>(100)
    private var logSeq = 0L

    fun log(level: String, msg: String) {
        val entry = AetherLog(seq = ++logSeq, timeMs = System.currentTimeMillis(), level = level, message = msg)
        logRing.addLast(entry)
        if (logRing.size > 500) logRing.removeFirst()
        _logs.value = logRing.toList()
    }

    fun requestStart(context: Context) {
        // Port conflict guard is UI-side; repository just sets busy and starts service
        _uiState.update { it.copy(busy = true, lastError = null, state = AetherState.STARTING) }
        log("INFO", "Aether start requested")
        val intent = Intent(context, AetherService::class.java).setAction(AetherService.ACTION_START)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            _uiState.update { it.copy(busy = false, lastError = "Failed to start service: ${e.message}", state = AetherState.ERROR) }
            log("ERROR", "Service start failed: ${e.message}")
        }
    }

    fun requestStop(context: Context) {
        _uiState.update { it.copy(busy = true, state = AetherState.STOPPING) }
        log("INFO", "Aether stop requested")
        val intent = Intent(context, AetherService::class.java).setAction(AetherService.ACTION_STOP)
        try { context.startService(intent) } catch (_: Exception) {}
    }

    fun requestRestart(context: Context) {
        _uiState.update { it.copy(busy = true, lastError = null, state = AetherState.STARTING) }
        log("INFO", "Aether restart requested")
        val intent = Intent(context, AetherService::class.java).setAction(AetherService.ACTION_RESTART)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Called by AetherService upon action completion */
    internal fun onServiceActionFinished(error: String?, newState: AetherState? = null) {
        _uiState.update {
            it.copy(
                busy = false,
                lastError = error ?: it.lastError,
                state = newState ?: if (error != null) AetherState.ERROR else it.state
            )
        }
        if (error != null) log("ERROR", error)
    }

    internal fun updateState(state: AetherState, error: String? = null) {
        _uiState.update { it.copy(state = state, lastError = error ?: it.lastError, busy = (state == AetherState.STARTING || state == AetherState.STOPPING)) }
    }

    internal fun setActiveJob(id: Long?) {
        _uiState.update { it.copy(activeJobId = id) }
    }

    internal fun setIdentity(id: Long?) {
        _uiState.update { it.copy(identityId = id) }
    }

    internal fun setEndpoint(peer: String, socks: String) {
        _uiState.update { it.copy(endpoint = peer, socksEndpoint = socks) }
    }

    internal fun setVersion(v: String) {
        _uiState.update { it.copy(version = v, isNativeLoaded = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(lastError = null) }
    }

    fun clearLogs() {
        logRing.clear()
        _logs.value = emptyList()
    }

    /** Direct FFI version check (no service) */
    fun refreshVersionFromNative() {
        val v = AetherBridge.version()
        if (v.isSuccess) {
            val versionStr = v.getOrNull()?.optString("version", "") ?: ""
            setVersion(versionStr)
            log("INFO", "Aether native version: $versionStr")
        } else {
            val err = v.exceptionOrNull()?.message ?: "unknown"
            _uiState.update { it.copy(loadError = err) }
            log("ERROR", "Version call failed: $err")
        }
    }

    /** Exposes lastPollRaw for diagnostics */
    internal fun setLastPoll(raw: String) {
        _uiState.update { it.copy(lastPollRaw = raw) }
    }

    // For UI: check port conflict externally, but provide helper
    fun detectConflict(aetherCfg: AetherConfig, rstaPort: Int): String? {
        val aetherPort = aetherCfg.socksBind.substringAfterLast(":").toIntOrNull()
        if (aetherPort != null && aetherPort == rstaPort) {
            return "Port $aetherPort conflicts with RSTA ($rstaPort). Change Aether or RSTA port."
        }
        val httpPort = aetherCfg.httpProxyBind.substringAfterLast(":").toIntOrNull()
        if (httpPort != null && httpPort == rstaPort) return "Aether HTTP proxy conflicts with RSTA port $rstaPort"
        return null
    }
}

data class AetherLog(
    val seq: Long,
    val timeMs: Long,
    val level: String,
    val message: String
)
