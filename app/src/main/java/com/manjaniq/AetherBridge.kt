package com.manjaniq

import android.util.Log
import com.manjaniq.core.models.Result
import org.json.JSONObject

/**
 * Kotlin/JNI bridge for Aether v2.3.0 (commit 6175b67df370ab856bcee07fe85b524031903956).
 *
 * Uses `libaether.so` produced by aether-wrapper (cdylib) which bundles the
 * full Aether core + JNI shim (`Java_com_manjaniq_AetherBridge_*`).
 * All returned strings are already freed via aether_string_free inside the
 * Rust JNI layer; Kotlin sees only Java strings.
 *
 * Thread-safe: Aether's FFI uses global OnceLock runtime + Mutex<HashMap<Job>>.
 * This wrapper adds synchronization for duplicate start prevention and
 * exposes ergonomic polling helpers.
 */
object AetherBridge {

    private const val TAG = "AetherBridge"

    /** Captured library load error, null on success */
    private val loadError: Throwable?

    /** Whether native is available */
    val isNativeAvailable: Boolean
        get() = loadError == null

    init {
        val err = try {
            System.loadLibrary("aether")
            null
        } catch (t: Throwable) {
            t
        }
        loadError = err
        if (err != null) {
            Log.e(TAG, "Failed to load libaether.so: ${err.message}", err)
        } else {
            // Early version sanity check without throwing
            try {
                val v = nativeAetherVersion()
                Log.i(TAG, "Aether native loaded: $v")
            } catch (t: Throwable) {
                Log.w(TAG, "Version call failed post-load: ${t.message}")
            }
        }
    }

    // ---- Low-level JNI exports (Rust aether-jni provides these) ----
    private external fun nativeAetherVersion(): String
    private external fun nativeAetherJobPoll(id: Long): String
    private external fun nativeAetherJobCancel(id: Long): String
    private external fun nativeAetherJobFree(id: Long): String
    private external fun nativeAetherIdentityOpen(payload: String): String
    private external fun nativeAetherIdentitySummary(id: Long): String
    private external fun nativeAetherIdentityFree(id: Long): String
    private external fun nativeAetherScanStart(identityId: Long, payload: String): String
    private external fun nativeAetherVerifyStart(identityId: Long, payload: String): String
    private external fun nativeAetherTunnelStart(identityId: Long, payload: String): String
    private external fun nativeAetherCoreStart(argsJson: String): String
    private external fun nativeAetherTeamSignIn(payload: String): String
    private external fun nativeAetherTeamCodeRequest(payload: String): String
    private external fun nativeAetherTeamCodeResend(session: Long): String
    private external fun nativeAetherTeamCodeSubmit(session: Long, code: String): String
    private external fun nativeAetherTeamSessionFree(id: Long): String
    private external fun nativeAetherTeamTokenSet(token: String): String
    private external fun nativeAetherTeamTokenClear(): String

    // ---- High-level helpers ----

    fun getLoadError(): Throwable? = loadError

    fun testBridge(): String {
        if (loadError != null) return "Native library not loaded: $loadError"
        return try {
            nativeAetherVersion()
        } catch (t: Throwable) {
            "Native call failed: ${t.message}; loadError=$loadError"
        }
    }

    /** Returns parsed version JSON or error */
    fun version(): Result<JSONObject> = safeJsonCall { nativeAetherVersion() }

    fun jobPoll(id: Long): Result<JSONObject> = safeJsonCall { nativeAetherJobPoll(id) }
    fun jobCancel(id: Long): Result<JSONObject> = safeJsonCall { nativeAetherJobCancel(id) }
    fun jobFree(id: Long): Result<JSONObject> = safeJsonCall { nativeAetherJobFree(id) }

    fun identityOpen(
        path: String,
        transport: String? = null,
        model: String? = null,
        locale: String? = null,
        team: JSONObject? = null
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("path", path)
            if (transport != null) put("transport", transport)
            if (model != null) put("model", model)
            if (locale != null) put("locale", locale)
            if (team != null) put("team", team)
        }
        return safeJsonCall { nativeAetherIdentityOpen(payload.toString()) }
    }

    fun identitySummary(id: Long): Result<JSONObject> =
        safeJsonCall { nativeAetherIdentitySummary(id) }

    fun identityFree(id: Long): Result<JSONObject> =
        safeJsonCall { nativeAetherIdentityFree(id) }

    fun scanStart(
        identityId: Long,
        transport: String? = null,
        mode: String? = null,
        ip: String? = null,
        profile: String? = null,
        ports: List<Int>? = null,
        excluded: List<String>? = null,
        ech: Boolean? = null
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            if (transport != null) put("transport", transport)
            if (mode != null) put("mode", mode)
            if (ip != null) put("ip", ip)
            if (profile != null) put("profile", profile)
            if (ports != null) put("ports", org.json.JSONArray(ports))
            if (excluded != null) put("excluded", org.json.JSONArray(excluded))
            if (ech != null) put("ech", ech)
        }
        return safeJsonCall { nativeAetherScanStart(identityId, payload.toString()) }
    }

    fun verifyStart(
        identityId: Long,
        peer: String,
        transport: String? = null,
        socks: String? = null,
        http: String? = null,
        profile: String? = null,
        keepalive: Int? = null,
        ech: Boolean? = null
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("peer", peer)
            if (transport != null) put("transport", transport)
            if (socks != null) put("socks", socks)
            if (http != null) put("http", http)
            if (profile != null) put("profile", profile)
            if (keepalive != null) put("keepalive", keepalive)
            if (ech != null) put("ech", ech)
        }
        return safeJsonCall { nativeAetherVerifyStart(identityId, payload.toString()) }
    }

    fun tunnelStart(
        identityId: Long,
        peer: String,
        transport: String? = null,
        socks: String? = null,
        http: String? = null,
        profile: String? = null,
        keepalive: Int? = null,
        ech: Boolean? = null
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("peer", peer)
            if (transport != null) put("transport", transport)
            if (socks != null) put("socks", socks)
            if (http != null) put("http", http)
            if (profile != null) put("profile", profile)
            if (keepalive != null) put("keepalive", keepalive)
            if (ech != null) put("ech", ech)
        }
        return safeJsonCall { nativeAetherTunnelStart(identityId, payload.toString()) }
    }

    /**
     * Low-level core start: args as JSON array of strings, e.g. ["--bind","127.0.0.1:1819","--masque"]
     * Use empty string for default args.
     */
    fun coreStart(args: List<String> = emptyList()): Result<JSONObject> {
        val json = if (args.isEmpty()) "[]" else org.json.JSONArray(args).toString()
        return safeJsonCall { nativeAetherCoreStart(json) }
    }

    fun teamSignIn(team: String, clientId: String? = null, clientSecret: String? = null, token: String? = null, email: String? = null): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("team", team)
            if (clientId != null) put("client_id", clientId)
            if (clientSecret != null) put("client_secret", clientSecret)
            if (token != null) put("token", token)
            if (email != null) put("email", email)
        }
        return safeJsonCall { nativeAetherTeamSignIn(payload.toString()) }
    }

    // Internal JSON helper - distinguishes "ok":false as failure
    private fun safeJsonCall(block: () -> String): Result<JSONObject> {
        if (loadError != null) return Result.failure("Native lib not loaded: $loadError")
        return try {
            val raw = block()
            val obj = JSONObject(raw)
            if (obj.optBoolean("ok", false)) {
                Result.success(obj)
            } else {
                Result.failure(obj.optString("error", "Aether returned ok=false"))
            }
        } catch (t: Throwable) {
            Result.failure("Aether call failed: ${t.message}")
        }
    }

    /**
     * Poll helper: blocks up to timeoutMs checking job status via jobPoll.
     * Returns the final job result JSON (done) or null if timeout/running.
     * Caller must still jobFree after.
     */
    fun pollUntilDone(jobId: Long, timeoutMs: Long = 30_000, pollIntervalMs: Long = 200): Result<JSONObject>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val poll = jobPoll(jobId)
            if (poll.isFailure) return poll
            val obj = poll.getOrNull() ?: return Result.failure("poll null")
            val state = obj.optString("state")
            if (state == "done") {
                // result is nested JSON under "result"
                val result = obj.optJSONObject("result")
                return if (result != null) {
                    if (result.optBoolean("ok", false)) Result.success(result)
                    else Result.failure(result.optString("error", "job done with error"))
                } else {
                    Result.success(obj)
                }
            }
            Thread.sleep(pollIntervalMs)
        }
        return null // timeout
    }
}
