package com.manjaniq

import android.content.Context
import android.util.Log
import com.manjaniq.core.models.Result
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    /**
     * Captured library load error, null on success.
     *
     * `@Volatile` (and not a `val`) because [initialize] can retry the load with an
     * absolute path once a `Context` is available - see [loadNativeLibrary]. The
     * first attempt happens in [init] so that a caller which never hands a
     * `Context` over (a unit test, a host tool) keeps exactly the old behaviour.
     */
    @Volatile
    private var loadError: Throwable? = null

    /** True once `libaether.so` is mapped into this process. */
    @Volatile
    private var loaded: Boolean = false

    /** Whether native is available */
    val isNativeAvailable: Boolean
        get() = loaded && loadError == null

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
        loadNativeLibrary(null)
    }

    /**
     * Loads `libaether.so` the way Android API 29+ requires, and can be called
     * again with a [Context] when the first, context-less attempt failed.
     *
     * TWO FAILURES THIS ADDRESSES
     * ---------------------------
     *  * **`libc++_shared.so` is not found.** `libaether.so` is a Rust cdylib built
     *    against the NDK's libc++, so its `DT_NEEDED` names `libc++_shared.so`. It
     *    *is* packaged next to it, but on Android the dependency is resolved inside
     *    the app's linker namespace, and a plain `System.loadLibrary("aether")` is
     *    the only thing that ever asked for `aether`. Loading the C++ runtime by
     *    name first, from the same directory, makes it resident before the core
     *    asks for it - the documented remedy for "dlopen failed: library
     *    libc++_shared.so not found".
     *  * **The library is not in the loader's search path.** With
     *    `android:extractNativeLibs` the files are real files in
     *    `applicationInfo.nativeLibraryDir`; `System.load` with that absolute path
     *    is the fallback when `loadLibrary` cannot find the name (an install whose
     *    namespace was restricted, an APK installed without extraction).
     *
     * Both attempts are recorded, so [getLoadDiagnostics] can say which paths were
     * tried and what the linker said. Nothing here throws: a missing library is a
     * failure result for the caller, never a crash at class-initialisation time.
     */
    @Synchronized
    fun initialize(context: Context?) {
        if (isNativeAvailable) return
        loadNativeLibrary(context?.applicationContext)
    }

    private fun loadNativeLibrary(context: Context?) {
        if (loaded) return

        // 1. The name-based path: this is what an APK installed with extraction
        //    uses, and it is the only form that pre-loads a dependency by name.
        try {
            System.loadLibrary("c++_shared")
        } catch (_: Throwable) {
            // Not packaged (or the core is linked statically): not fatal on its own.
        }
        try {
            System.loadLibrary("aether")
            markLoaded()
            return
        } catch (t: Throwable) {
            loadError = t
            Log.e(TAG, "System.loadLibrary(\"aether\") failed: ${buildLoadDiagnostics(t)}", t)
        }

        // 2. The absolute path inside the app's native library directory.
        val directory = nativeLibraryDir(context)
        if (directory == null) {
            reportLoadFailure("no native library directory is visible to this process")
            return
        }
        val runtime = File(directory, "libc++_shared.so")
        if (runtime.isFile) {
            try {
                System.load(runtime.absolutePath)
                Log.i(TAG, "Pre-loaded ${runtime.absolutePath}")
            } catch (t: Throwable) {
                Log.w(TAG, "Could not pre-load ${runtime.absolutePath}: ${t.message}")
            }
        }
        val core = File(directory, "libaether.so")
        if (!core.isFile) {
            reportLoadFailure(
                "${core.absolutePath} does not exist: the APK was installed without its " +
                    "native libraries (extractNativeLibs=false, or a split for another ABI)"
            )
            return
        }
        try {
            System.load(core.absolutePath)
            markLoaded()
        } catch (t: Throwable) {
            loadError = t
            reportLoadFailure(
                "System.load(${core.absolutePath}) failed: ${buildLoadDiagnostics(t)}"
            )
        }
    }

    /** The directory the platform exposes the packaged `.so` files from. */
    private fun nativeLibraryDir(context: Context?): String? {
        val fromContext = try {
            context?.applicationInfo?.nativeLibraryDir
        } catch (_: Throwable) {
            null
        }
        if (!fromContext.isNullOrBlank()) return fromContext
        // Without a Context the property is still the platform's own answer.
        return try {
            System.getProperty("java.library.path")?.split(File.pathSeparator)
                ?.firstOrNull { it.isNotBlank() && File(it, "libaether.so").isFile }
        } catch (_: Throwable) {
            null
        }
    }

    private fun markLoaded() {
        loaded = true
        loadError = null
        try {
            val v = nativeAetherVersion()
            Log.i(TAG, "Aether native loaded: $v")
        } catch (t: Throwable) {
            Log.w(TAG, "Version call failed post-load: ${t.message}")
        }
    }

    private fun reportLoadFailure(reason: String) {
        Log.e(TAG, "libaether.so is unavailable: $reason (ABIs: ${SUPPORTED_ABIS.joinToString(",")})")
        if (loadError == null) loadError = IllegalStateException(reason)
    }

    // ---- Low-level JNI exports (Rust aether-jni provides these) ----
    private external fun nativeAetherVersion(): String
    private external fun nativeAetherJobPoll(id: Long): String
    private external fun nativeAetherJobCancel(id: Long): String
    private external fun nativeAetherJobFree(id: Long): String
    private external fun nativeAetherEnvReset(): String
    private external fun nativeAetherEnvSet(payload: String): String
    private external fun nativeAetherLogLines(since: Long): String
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
        return safeJsonCall {
            val payload = JSONObject().apply {
                put("enabled", enabled)
                put("http2", http2Carrier)
                put("sniSplit", sniSplit)
                put("size", size.trim())
                put("delay", delay.trim())
            }
            nativeAetherFragmentingApply(payload.toString())
        }
    }

    /**
     * Starts the core with its own command line: `aether_core_start`
     * (`third_party/Aether/aether/src/ffi.rs`), which runs `cli::parse_args` and
     * then `run_with` as a pollable, cancellable job.
     *
     * [args] is the argument list built by
     * [com.manjaniq.core.models.AetherConfig.toAetherArgs]; an empty list means
     * "the engine's own defaults" and is accepted by the FFI too.
     */
    /**
     * Clears the engine's own `AETHER_*` environment before a start.
     *
     * `run_with` reads its settings from the process environment and `cli` writes
     * them there, so a value from the previous start would otherwise survive into
     * this one - turning Tor off would leave `AETHER_TOR=chain` set and the engine
     * would keep chaining through it. The app has no `setenv` of its own; this is
     * the JNI call that does it (`aether-wrapper/src/lib.rs`). Only `AETHER_*`
     * names are touched.
     */
    fun envReset(): Result<JSONObject> = safeJsonCall { nativeAetherEnvReset() }

    /**
     * Writes the writable locations the engine resolves from the process
     * environment, before a start.
     *
     * Android gives an app none of the variables a desktop process has:
     * `std::env::temp_dir()` is `$TMPDIR` and otherwise `/data/local/tmp`, which
     * the app cannot write (psiphon's state file and the pluggable-transport probe
     * both write there), and `HOME` - where a component that keeps a state
     * directory of its own would look - is unset or `/`. Kotlin cannot `setenv`,
     * so this call does it, and the native side accepts only `TMPDIR`, `TEMP`,
     * `TMP`, `HOME` and `AETHER_*` names.
     *
     * The reply holds what the environment holds *after* the write, so the caller
     * logs the paths in force rather than the ones it asked for.
     */
    fun envSet(values: Map<String, String>): Result<JSONObject> = safeJsonCall {
        nativeAetherEnvSet(JSONObject(values).toString())
    }

    /** One line the engine logged, as the native sink kept it. */
    data class EngineLogLine(val seq: Long, val level: String, val text: String)

    /**
     * The engine's own log lines recorded after [since], oldest first.
     *
     * Aether logs every step of a start through the `log` crate, and its own
     * writer is stderr - which under Android nobody reads. The bridge installs a
     * sink that keeps the last 512 lines in memory (and mirrors them to logcat
     * under the `AetherCore` tag), and this call hands them to the app so a
     * failed start is visible in the panel instead of silent.
     *
     * The caller passes the highest [EngineLogLine.seq] it has already seen, so
     * nothing is shown twice.
     */
    fun logLines(since: Long): Result<List<EngineLogLine>> {
        val reply = safeJsonCall { nativeAetherLogLines(since) }.getOrNull() ?: return Result.failure(
            loadError?.let { "Native lib not loaded: $it" } ?: "the engine log could not be read"
        )
        val array = reply.optJSONArray("lines") ?: return Result.success(emptyList())
        val lines = ArrayList<EngineLogLine>(array.length())
        for (index in 0 until array.length()) {
            val line = array.optJSONObject(index) ?: continue
            lines.add(
                EngineLogLine(
                    seq = line.optLong("seq", 0L),
                    level = line.optString("level", "INFO"),
                    text = line.optString("text", "")
                )
            )
        }
        return Result.success(lines)
    }

    fun coreStart(args: List<String> = emptyList()): Result<JSONObject> = safeJsonCall {
        nativeAetherCoreStart(JSONArray(args).toString())
    }

    fun teamSignIn(team: String, clientId: String? = null, clientSecret: String? = null, token: String? = null, email: String? = null): Result<JSONObject> {
        return safeJsonCall {
            val payload = JSONObject().apply {
                put("team", team)
                if (clientId != null) put("client_id", clientId)
                if (clientSecret != null) put("client_secret", clientSecret)
                if (token != null) put("token", token)
                if (email != null) put("email", email)
            }
            nativeAetherTeamSignIn(payload.toString())
        }
    }

    /**
     * Runs one JNI call and turns its answer into a [Result].
     *
     * Every serialisation happens inside [block], after the missing-native check,
     * so no call through this object can throw: without the library it is a
     * failure result, and a payload that cannot be encoded is one too.
     */
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
