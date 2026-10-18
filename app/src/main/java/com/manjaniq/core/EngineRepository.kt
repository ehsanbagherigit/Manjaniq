package com.manjaniq.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.manjaniq.NativeBridge
import com.manjaniq.core.models.EngineState
import com.manjaniq.service.EngineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Live engine counters, parsed from the Go Metrics JSON. */
data class EngineMetrics(
    val listenAddr: String = "",
    val connectAddr: String = "",
    val method: String = "",
    val fragmentStrategy: String = "",
    val fakeSNI: String = "",
    val activeConns: Int = 0,
    val totalConns: Long = 0,
    val bypassOK: Long = 0,
    val blocked: Long = 0,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val uptimeSeconds: Long = 0,
    val version: String = ""
)

/** One engine log line, parsed from the Go log-ring JSON. */
data class EngineLog(
    val seq: Long,
    val timeMs: Long,
    val level: String,
    val message: String
)

data class EngineUiState(
    val state: EngineState = EngineState.STOPPED,
    val metrics: EngineMetrics = EngineMetrics(),
    val busy: Boolean = false,
    val lastError: String? = null
)

/**
 * Single source of truth between the UI and the engine. Start/stop
 * go through [EngineService] (which owns the native lifecycle and
 * the foreground notification); state/metrics/logs are polled from
 * the native side and republished as StateFlows. No value shown in
 * the UI is invented here — everything comes from the engine.
 */
object EngineRepository {

    private val _uiState = MutableStateFlow(EngineUiState())
    val uiState: StateFlow<EngineUiState> = _uiState.asStateFlow()

    private val _logs = MutableStateFlow<List<EngineLog>>(emptyList())
    val logs: StateFlow<List<EngineLog>> = _logs.asStateFlow()

    fun requestStart(context: Context) {
        _uiState.update { it.copy(busy = true, lastError = null) }
        val intent = Intent(context, EngineService::class.java)
            .setAction(EngineService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun requestStop(context: Context) {
        _uiState.update { it.copy(busy = true) }
        val intent = Intent(context, EngineService::class.java)
            .setAction(EngineService.ACTION_STOP)
        // The service is already running (foreground) when stopping.
        context.startService(intent)
    }

    fun requestRestart(context: Context) {
        _uiState.update { it.copy(busy = true, lastError = null) }
        val intent = Intent(context, EngineService::class.java)
            .setAction(EngineService.ACTION_RESTART)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Called by EngineService. [error] non-null means start/stop failed. */
    internal fun onEngineActionFinished(error: String?) {
        _uiState.update { it.copy(busy = false, lastError = error ?: it.lastError) }
    }

    fun clearError() {
        _uiState.update { it.copy(lastError = null) }
    }

    fun clearLogs() {
        NativeBridge.clearLogs()
        _logs.value = emptyList()
    }

    /** Polls the engine once. Call from a repeating effect. */
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            try {
                val state = NativeBridge.getEngineState()
                val metrics = parseMetrics(NativeBridge.metricsJson())
                val logs = parseLogs(NativeBridge.logsJson())
                _uiState.update { it.copy(state = state, metrics = metrics) }
                if (logs != null) {
                    _logs.value = logs
                }
            } catch (t: Throwable) {
                // Polling must never crash the UI; the next tick retries.
            }
        }
    }

    private fun parseMetrics(json: String?): EngineMetrics {
        if (json.isNullOrBlank()) return EngineMetrics()
        return try {
            val o = JSONObject(json)
            EngineMetrics(
                listenAddr = o.optString("listen_addr"),
                connectAddr = o.optString("connect_addr"),
                method = o.optString("method"),
                fragmentStrategy = o.optString("fragment_strategy"),
                fakeSNI = o.optString("fake_sni"),
                activeConns = o.optInt("active_conns"),
                totalConns = o.optLong("total_conns"),
                bypassOK = o.optLong("bypass_ok"),
                blocked = o.optLong("blocked"),
                bytesUp = o.optLong("bytes_c2s"),
                bytesDown = o.optLong("bytes_s2c"),
                uptimeSeconds = o.optLong("uptime_seconds"),
                version = o.optString("version")
            )
        } catch (e: Exception) {
            EngineMetrics()
        }
    }

    private fun parseLogs(json: String?): List<EngineLog>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<EngineLog>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    EngineLog(
                        seq = o.optLong("seq"),
                        timeMs = o.optLong("time_ms"),
                        level = o.optString("level", "INFO"),
                        message = o.optString("message")
                    )
                )
            }
            out
        } catch (e: Exception) {
            null
        }
    }
}
