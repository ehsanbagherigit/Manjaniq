package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.manjaniq.core.AetherPreflight
import com.manjaniq.core.models.AetherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket

/**
 * The Aether start path, on a real device - the part no host test can reach.
 *
 * `AetherService` cannot run here, so the pieces it depends on are exercised
 * directly: the loader (`libaether.so` and the C++ runtime it links against), the
 * JNI surface the service calls, the environment it writes before a start, the
 * identity path it hands the core, and the job API plus log sink it reads back.
 *
 * WHAT THIS IS FOR
 * ----------------
 * Every one of these fails *only* on a device: a shared library that is missing
 * from the APK for this ABI, a `DT_NEEDED` dependency the loader cannot resolve,
 * a temporary directory the app may not write, an identity file the core cannot
 * create, or a native job that ends the process instead of reporting an error.
 * All of them look fine in review, and all of them look fine on the host, where
 * there is no `libaether.so` at all.
 *
 * The suite is deliberately bounded: nothing here waits for a tunnel to be
 * established (that needs a working gateway), only for the engine to accept a
 * start, say something about it and stay alive.
 */
@RunWith(AndroidJUnit4::class)
class AetherNativeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /**
     * `libaether.so` loads through the same path the app uses, and its C++
     * runtime is where the linker looks for it.
     */
    @Test
    fun libaetherLoadsWithItsCObjectRuntime() {
        AetherBridge.initialize(context)
        assertTrue(
            "libaether.so did not load: ${AetherBridge.getLoadDiagnostics()}",
            AetherBridge.isNativeAvailable
        )

        // The runtime libaether.so is linked against (`libc++_shared.so` in its
        // DT_NEEDED) must be packaged beside it - the classic
        // "dlopen failed: library libc++_shared.so not found".
        val directory = context.applicationInfo.nativeLibraryDir
        assertNotNull("the platform exposed no native library directory", directory)
        assertTrue(
            "libc++_shared.so is not packaged in $directory",
            File(directory, "libc++_shared.so").isFile
        )
        assertTrue(
            "libaether.so is not packaged in $directory",
            File(directory, "libaether.so").isFile
        )

        val version = AetherBridge.version()
        assertTrue("version call failed: ${version.errorMessage}", version.isSuccess)
        assertTrue(
            "version must name a version: ${version.getOrNull()}",
            version.getOrNull()!!.optString("version").isNotEmpty()
        )
    }

    /**
     * The engine's writable locations are set from the app's own directories, and
     * they are writable.
     *
     * Android's `std::env::temp_dir()` is `$TMPDIR` and otherwise
     * `/data/local/tmp`, which an app may not write; `HOME` is unset or `/`. A
     * core component that writes a temporary file or a state directory therefore
     * fails on a device and nowhere else.
     */
    @Test
    fun theEngineGetsWritableDirectories() {
        AetherBridge.initialize(context)
        val cache = context.cacheDir.absolutePath
        val files = context.filesDir.absolutePath

        val applied = AetherBridge.envSet(mapOf("TMPDIR" to cache, "HOME" to files))
        assertTrue("envSet failed: ${applied.errorMessage}", applied.isSuccess)
        val set = applied.getOrNull()!!.optJSONObject("set")
        assertEquals("TMPDIR must be read back as applied", cache, set?.optString("TMPDIR"))
        assertEquals("HOME must be read back as applied", files, set?.optString("HOME"))

        val probe = File(cache, "aether-tmp-probe-${System.nanoTime()}")
        try {
            assertTrue("the cache directory must accept a new file", probe.createNewFile())
            probe.writeText("probe")
            assertTrue("the cache directory must accept contents", probe.length() > 0)
        } finally {
            probe.delete()
        }
    }

    /**
     * The names `envSet` may not write are refused, and nothing is written when
     * one is present: this surface exists to set four standard names and the
     * engine's own, not to rewrite the process environment.
     */
    @Test
    fun envSetRefusesNamesOutsideItsAllowList() {
        AetherBridge.initialize(context)
        val refused = AetherBridge.envSet(mapOf("LD_PRELOAD" to "/data/local/tmp/evil.so"))
        assertTrue("LD_PRELOAD must not be settable through the bridge", refused.isFailure)
    }

    /** The identity file is prepared inside `filesDir`, and is not pre-created. */
    @Test
    fun theIdentityPathIsPreparedInsideFilesDir() {
        val port = freePort()
        val identity = "aether/identity/probe-${System.nanoTime()}"
        val cfg = AetherConfig.DEFAULT.copy(
            identityPath = identity,
            socksBind = "127.0.0.1:$port"
        )
        val problem = AetherPreflight.check(cfg, context.filesDir.absolutePath)
        assertNull("the pre-flight refused a usable identity path: $problem", problem)

        val file = AetherPreflight.identityFile(cfg, context.filesDir.absolutePath)
        assertNotNull("the identity path must resolve inside filesDir", file)
        assertTrue(
            "the identity's directory must exist before the core writes there",
            file!!.parentFile!!.isDirectory
        )
        // An empty aether.toml is not a valid identity - the core would quarantine
        // it - so the check must not create one.
        assertFalse(
            "the pre-flight must not pre-create the identity file",
            file.exists()
        )
    }

    /**
     * A start reaches the engine, is answered by the job API, is described by the
     * engine's own log, and leaves the process answering.
     *
     * The peer is `127.0.0.1:9`, where nothing listens: the dial fails at once, so
     * this needs no gateway and no network. What it proves is that the JNI path,
     * the tokio runtime the job runs on, the log sink and the cancel/free cycle
     * all work on a device - a start that silently killed the process, or one
     * whose failure produced no line at all, fails here.
     */
    @Test
    fun aStartIsReportedByTheJobApiAndTheLogSink() {
        AetherBridge.initialize(context)
        assertTrue("libaether.so must be loaded for this test", AetherBridge.isNativeAvailable)
        // A start begins from this test's argument list and nothing else.
        AetherBridge.envReset()
        AetherBridge.envSet(
            mapOf(
                "TMPDIR" to context.cacheDir.absolutePath,
                "HOME" to context.filesDir.absolutePath
            )
        )

        val cfg = AetherConfig.DEFAULT.copy(
            identityPath = "aether/identity/job-${System.nanoTime()}",
            socksBind = "127.0.0.1:${freePort()}",
            // Nothing answers here, so the dial fails quickly instead of scanning
            // the internet for a gateway the emulator may not be able to reach.
            peer = "127.0.0.1:9",
            scanMode = "turbo",
            noizeProfile = "off",
            quickReconnect = false
        )
        val args = cfg.toAetherArgs(context.filesDir.absolutePath)
        val started = AetherBridge.coreStart(args)
        assertTrue(
            "the engine refused its own configuration (${args.filter { it.startsWith("-") }}): " +
                "${started.errorMessage}",
            started.isSuccess
        )
        val job = started.getOrNull()!!.optLong("job", -1L)
        assertTrue("a start must hand back a job id: ${started.getOrNull()}", job > 0)

        var engineLines = 0
        var state = ""
        try {
            val deadline = System.currentTimeMillis() + START_BUDGET_MS
            while (System.currentTimeMillis() < deadline) {
                engineLines += AetherBridge.logLines(0).getOrNull()?.size ?: 0
                state = AetherBridge.jobPoll(job).getOrNull()?.optString("state") ?: ""
                if (state == "done") break
                Thread.sleep(250)
            }
            // Drain once more: the last line the job wrote explains its outcome.
            engineLines += AetherBridge.logLines(0).getOrNull()?.size ?: 0
        } finally {
            val cancelled = AetherBridge.jobCancel(job)
            val freed = AetherBridge.jobFree(job)
            assertTrue("cancel must be answered: ${cancelled.errorMessage}", cancelled.isSuccess)
            assertTrue("free must be answered: ${freed.errorMessage}", freed.isSuccess)
        }

        assertTrue(
            "the engine reported nothing about the start (state='$state'): without a log " +
                "sink a device-side failure is invisible",
            engineLines > 0
        )
        assertTrue(
            "the job must end or still be running, never vanish (state='$state')",
            state == "done" || state == "running"
        )
        assertTrue(
            "the process must still answer after a start and a cancel",
            AetherBridge.version().isSuccess
        )
    }

    companion object {
        /** How long a start attempt is given before it is cancelled. */
        private const val START_BUDGET_MS = 12_000L
    }
}
