package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.ProfileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AetherNativeTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AetherBridge.initialize(context)
    }

    @Test
    fun versionReportIsAvailableAndLooksLikeAVersion() {
        assertTrue(
            "libaether.so must be loaded on device (loadError=${AetherBridge.getLoadError()})",
            AetherBridge.isNativeAvailable
        )
        val result = AetherBridge.version()
        assertTrue("version() must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val obj = result.getOrNull()
        assertNotNull(obj)
        assertTrue("ok must be true in $obj", obj!!.optBoolean("ok", false))
        val version = obj.optJSONObject("result")?.optString("version", "") ?: ""
        assertTrue(
            "version string '$version' must look like semver (digits.digits...)",
            version.matches(Regex("^\\d+\\.\\d+.*"))
        )
    }

    @Test
    fun logRingBufferStartsCleanAndAcceptsCursorZero() {
        assertTrue(AetherBridge.isNativeAvailable)
        assertTrue(AetherBridge.logClear().isSuccess)
        val batch = AetherBridge.logLines(0)
        assertTrue("logLines(0) must succeed: ${batch.exceptionOrNull()}", batch.isSuccess)
        val obj = batch.getOrNull()!!
        val lines = obj.optJSONArray("lines")
        assertNotNull("lines array must be present in $obj", lines)
        assertEquals("log must be empty right after logClear()", 0, lines!!.length())
    }

    @Test
    fun preflightRequiresNoIdentityFileOnFirstRun() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workDir = AetherIdentityManager.workDir(context)
        val file = File(workDir, AetherIdentityManager.WIREGUARD_FILE)
        file.delete()

        val profile = ProfileItem.create().apply { aetherProtocol = AetherProtocol.WIREGUARD.type }
        val needed = AetherIdentityManager.keyFilesOf(profile, scan = false)
        assertEquals(listOf(AetherIdentityManager.WIREGUARD_FILE), needed)
        assertFalse("identity file should not exist yet", file.exists())
        val status = AetherIdentityManager.statusIn(workDir, AetherProtocol.WIREGUARD)
        assertNull("WireGuard identity should be null on first run", status.primary)
    }

    @Test
    fun preflightReadsCompleteIdentityFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workDir = AetherIdentityManager.workDir(context)
        workDir.mkdirs()
        val idFile = File(workDir, AetherIdentityManager.WIREGUARD_FILE)
        try {
            idFile.writeText(
                """
                private_key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                device_id = "device-test-123"
                token = "token-test-456"
                ipv4 = "172.16.0.2"
                ipv6 = "2606:4700:110::2"
                peer_public_key = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
                """.trimIndent()
            )
            val status = AetherIdentityManager.statusIn(workDir, AetherProtocol.WIREGUARD)
            assertNotNull("Valid identity file must be parsed", status.primary)
            assertEquals("device-test-123", status.primary?.deviceId)
            assertEquals("172.16.0.2", status.primary?.ipv4)
        } finally {
            idFile.delete()
        }
    }
}
