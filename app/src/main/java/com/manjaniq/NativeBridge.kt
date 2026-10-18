package com.manjaniq

import android.util.Log
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.core.models.EngineState

object NativeBridge {

    /** Captured [System.loadLibrary] failure, or null when loading succeeded. */
    private val loadError: Throwable?

    init {
        val error = try {
            System.loadLibrary("manjaniq_native")
            null
        } catch (t: Throwable) {
            t
        }
        loadError = error
        if (error != null) {
            try {
                Log.e("NativeBridge", "Failed to load manjaniq_native: ${error.message}")
            } catch (t: Throwable) {
                // Logging must never mask the library-load failure.
            }
        }
    }

    external fun GoHelloWorld(): String?

    external fun StartEngine(configJSON: String): String?
    external fun StopEngine(): String?
    external fun IsEngineRunning(): Int
    external fun GetEngineState(): String?
    external fun GetMetrics(): String?
    external fun GetLogs(): String?
    external fun ClearLogs()
    external fun Cleanup()

    fun testBridge(): String {
        return try {
            GoHelloWorld() ?: "Native call returned null"
        } catch (t: Throwable) {
            val loadFailure = loadError?.let { "; library load failed: $it" } ?: ""
            "Native call failed: ${t.message}$loadFailure"
        }
    }

    fun startEngine(config: RSTAConfig): com.manjaniq.core.models.Result<String> {
        return try {
            val configJSON = convertConfigToJSON(config)
            val result = StartEngine(configJSON)
            // The native side returns exactly "OK" on success and a
            // human-readable explanation (for example "Config error: ...")
            // on failure. Any other string must be treated as an error so
            // the UI never reports a successful start that did not happen.
            if (result == "OK") {
                com.manjaniq.core.models.Result.success("OK")
            } else {
                com.manjaniq.core.models.Result.failure(result ?: "Engine start failed with no error message")
            }
        } catch (t: Throwable) {
            com.manjaniq.core.models.Result.failure("Failed to start engine: ${t.message}")
        }
    }

    fun stopEngine(): com.manjaniq.core.models.Result<String> {
        return try {
            val result = StopEngine()
            // See startEngine: only "OK" is success on the native side.
            if (result == "OK") {
                com.manjaniq.core.models.Result.success("OK")
            } else {
                com.manjaniq.core.models.Result.failure(result ?: "Engine stop failed with no error message")
            }
        } catch (t: Throwable) {
            com.manjaniq.core.models.Result.failure("Failed to stop engine: ${t.message}")
        }
    }

    fun isEngineRunning(): Boolean {
        return IsEngineRunning() == 1
    }

    fun getEngineState(): EngineState {
        return try {
            val stateStr = GetEngineState()
            EngineState.valueOf(stateStr ?: "STOPPED")
        } catch (e: Exception) {
            EngineState.STOPPED
        }
    }

    fun getMetrics(): com.manjaniq.core.models.Result<String> {
        return try {
            val metrics = GetMetrics()
            com.manjaniq.core.models.Result.success(metrics ?: "{}")
        } catch (t: Throwable) {
            com.manjaniq.core.models.Result.failure("Failed to get metrics: ${t.message}")
        }
    }

    private fun convertConfigToJSON(config: RSTAConfig): String {
        return """{
  "LISTEN_HOST": "${config.listenHost}",
  "LISTEN_PORT": ${config.listenPort},
  "CONNECT_IP": "${config.connectIp}",
  "CONNECT_PORT": ${config.connectPort},
  "FAKE_SNI": "${config.sniHostname}",
  "BYPASS_METHOD": "${config.bypassMethod}",
  "FRAGMENT_STRATEGY": "${config.fragmentStrategy}",
  "FRAGMENT_DELAY": ${config.fragmentDelay},
  "USE_TTL_TRICK": ${config.useTtlTrick},
  "FAKE_SNI_METHOD": "${config.fakeSNIMethod}",
  "FAKE_SNI_FRAGMENT_REAL": ${config.fakeSNIFragmentReal},
  "MONITOR": ${config.monitorEnabled},
  "quiet": ${config.quietMode}
}"""
    }

    /** Raw JSON array of engine log entries, or null if native is unavailable. */
    fun logsJson(): String? {
        return try {
            GetLogs()
        } catch (t: Throwable) {
            null
        }
    }

    fun clearLogs() {
        try {
            ClearLogs()
        } catch (t: Throwable) {
            // Clearing logs must never crash the app.
        }
    }

    /** Raw JSON metrics object, or null if native is unavailable. */
    fun metricsJson(): String? {
        return try {
            GetMetrics()
        } catch (t: Throwable) {
            null
        }
    }

    fun cleanup() {
        try {
            Cleanup()
        } catch (t: Throwable) {
            Log.e("NativeBridge", "Failed to cleanup native resources", t)
        }
    }
}
