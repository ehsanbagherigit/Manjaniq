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

    /** ABI list from the OS - for diagnostics and tests */
    val SUPPORTED_ABIS: List<String>
        get() = try { android.os.Build.SUPPORTED_ABIS.toList() } catch (_: Throwable) { emptyList() }

    /** Public accessor for load diagnostics (used by tests/UI) */
    fun getLoadDiagnostics(): String? = loadError?.let { buildLoadDiagnostics(it) }

    /** Detailed diagnostics for missing deps (e.g. libc++_shared.so) */
    private fun buildLoadDiagnostics(t: Throwable): String {
        val chain = mutableListOf<String>()
        var cur: Throwable? = t
        while (cur != null) {
            chain.add((cur.javaClass.simpleName + ": " + (cur.message ?: "null")).take(300))
            if (chain.size>5) break
            cur = cur.cause
        }
        val hint = when {
            chain.joinToString().contains("libc++_shared") -> "Missing libc++_shared.so - NDK STL not packaged. Fixed in gradle by copying from NDK."
            chain.joinToString().contains("dlopen failed") -> "dlopen failed - check APK lib/arm64-v8a/ inventory and DT_NEEDED"
            else -> ""
        }
        return chain.joinToString(" -> ") + if(hint.isNotEmpty()) " | $hint" else ""
    }

    init {
        var err: Throwable? = null
        var diag = ""
        try {
            System.loadLibrary("aether")
        } catch (t: Throwable) {
            err = t
            diag = buildLoadDiagnostics(t)
            Log.e(TAG, "Failed to load libaether.so: $diag", t)
            // Try to hint ABI mismatch
            try {
                val abi = android.os.Build.SUPPORTED_ABIS.joinToString(",")
                Log.e(TAG, "Supported ABIs: $abi, diagnostics: $diag")
            } catch (_: Throwable) {}
        }
        loadError = err
        if (err == null) {
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
    private external fun nativeAetherFragmentingApply(payload: String): String
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
    /**
     * Applies the TLS ClientHello fragmenting configuration to the engine.
     *
     * Aether reads fragmenting from its environment (`FragmentConfig::from_env`,
     * `fragment.rs:32-61`) and picks the carrier the same way
     * (`masque_h2::enabled()` → `AETHER_MASQUE_HTTP2`, `masque_h2.rs:141-149`), which
     * the tunnel (`lib.rs:1530`), the scanner's verification (`prober.rs:414`) and the
     * CLI's own transport question (`lib.rs:3255`) all consult. `fragment.rs` has one
     * consumer, `masque_h2.rs`, so fragmenting only exists on the MASQUE-over-HTTP/2
     * carrier: [http2Carrier] travels with [enabled] rather than letting fragments be
     * asked for on a carrier that ignores them.
     *
     * The JNI side writes those five variables and nothing else, and answers with what
     * the environment holds afterwards, so the caller can log the applied values
     * instead of the requested ones. A blank [size] or [delay] removes that variable
     * and leaves Aether's own default in place.
     */
    fun fragmentingApply(
        enabled: Boolean,
        size: String,
        delay: String,
        sniSplit: Boolean,
        http2Carrier: Boolean = enabled
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("enabled", enabled)
            put("http2", http2Carrier)
            put("sniSplit", sniSplit)
            put("size", size.trim())
            put("delay", delay.trim())
        }
        return safeJsonCall { nativeAetherFragmentingApply(payload.toString()) }
    }

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
}
