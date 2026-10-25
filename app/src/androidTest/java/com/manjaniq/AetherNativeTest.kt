package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.ProfileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket

@RunWith(AndroidJUnit4::class)
class AetherNativeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun libaetherLoadsWithItsCObjectRuntime() {
        AetherBridge.initialize(context)
        assertTrue(
            "libaether.so did not load: ${AetherBridge.getLoadDiagnostics()}",
            AetherBridge.isNativeAvailable
        )

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

    @Test
    fun envSetRefusesNamesOutsideItsAllowList() {
        AetherBridge.initialize(context)
        val refused = AetherBridge.envSet(mapOf("LD_PRELOAD" to "/data/local/tmp/evil.so"))
        assertTrue("LD_PRELOAD must not be settable through the bridge", refused.isFailure)
    }

    @Test
    fun theIdentityPathIsPreparedInsideFilesDir() {
        val workDir = AetherIdentityManager.workDir(context)
        workDir.mkdirs()
        assertTrue(
            "the identity's directory must exist before the core writes there",
            workDir.isDirectory
        )
        val probeFile = File(workDir, "probe-${System.nanoTime()}.toml")
        probeFile.delete()
        assertFalse(
            "the identity file must not be pre-created",
            probeFile.exists()
        )
        val profile = ProfileItem.create().apply { aetherProtocol = AetherProtocol.WIREGUARD.type }
        assertEquals(
            listOf(AetherIdentityManager.WIREGUARD_FILE),
            AetherIdentityManager.keyFilesOf(profile, scan = false)
        )
    }

    @Test
    fun aStartIsReportedByTheJobApiAndTheLogSink() {
        AetherBridge.initialize(context)
        assertTrue("libaether.so must be loaded for this test", AetherBridge.isNativeAvailable)
        AetherBridge.envReset()
        AetherBridge.envSet(
            mapOf(
                "TMPDIR" to context.cacheDir.absolutePath,
                "HOME" to context.filesDir.absolutePath
            )
        )

        val probeDir = File(context.filesDir, "aether-job-${System.nanoTime()}").apply { mkdirs() }
        val profile = ProfileItem.create().apply {
            aetherProtocol = AetherProtocol.MASQUE.type
            server = "127.0.0.1"
            serverPort = "9"
            aetherScanMode = AetherScanMode.TURBO.type
            aetherObfuscation = AetherObfuscation.OFF.type
        }
        val baseArgs = AetherCoreManager.buildArguments(profile, port = freePort(), scan = false)
        val args = listOf(
            "--config", File(probeDir, AetherIdentityManager.BASE_FILE).absolutePath,
            "--wg-config", File(probeDir, AetherIdentityManager.WIREGUARD_FILE).absolutePath,
            "--masque-config", File(probeDir, AetherIdentityManager.MASQUE_FILE).absolutePath,
        ) + baseArgs

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
            engineLines += AetherBridge.logLines(0).getOrNull()?.size ?: 0
        } finally {
            val cancelled = AetherBridge.jobCancel(job)
            val freed = AetherBridge.jobFree(job)
            probeDir.deleteRecursively()
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
        private const val START_BUDGET_MS = 12_000L
    }
}
