package com.manjaniq

import com.manjaniq.core.AetherCore
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherExit
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherIpVersion
import com.manjaniq.core.AetherKeyKind
import com.manjaniq.core.AetherKeys
import com.manjaniq.core.AetherKeysSettings
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.AetherTransport
import com.manjaniq.core.ProfileItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AetherCoreTest {

    private fun profile(
        protocol: AetherProtocol = AetherProtocol.MASQUE,
        transport: AetherTransport = AetherTransport.HTTP3,
        server: String? = null,
        port: String? = null,
        outer: String? = null,
        inner: String? = null,
        fragment: Boolean? = null,
    ) = ProfileItem(
        remarks = "test",
        server = server,
        serverPort = port,
        aetherProtocol = protocol.type,
        aetherTransport = transport.type,
        aetherScanMode = AetherScanMode.AGGRESSIVE.type,
        aetherObfuscation = AetherObfuscation.HEAVY.type,
        aetherIpVersion = AetherIpVersion.DUAL.type,
        aetherWiwOuter = outer,
        aetherWiwInner = inner,
        aetherFragment = fragment,
    )

    private fun valueAfter(arguments: List<String>, flag: String): String? =
        arguments.indexOf(flag).takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }

    @Test
    fun defaultSocksListenerBindsAndRendersLoopbackPort1819() {
        val profile = ProfileItem.create()
        val core = AetherCore.of(profile)

        assertEquals("127.0.0.1:1819", valueAfter(core.arguments, "--bind"))
        assertEquals(1819, core.port)
        assertNull(core.invalidListener)
        assertNull(core.unreachableListener)

        val reparsed = AetherCore.ofCommand(core.command)
        assertNotNull(reparsed)
        assertEquals(1819, reparsed?.port)
        assertTrue(reparsed?.runsAs(core.arguments) == true)
    }

    @Test
    fun bindsToTheLoopbackPortItIsGiven() {
        assertEquals("127.0.0.1:10819", valueAfter(AetherCoreManager.buildArguments(profile(), 10819), "--bind"))
        assertEquals("127.0.0.1:0", valueAfter(AetherCoreManager.buildArguments(profile(), 0, scan = true), "--bind"))
    }

    @Test
    fun eachProtocolIsNamedToTheCore() {
        assertEquals("wg", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.WIREGUARD), 10819), "--protocol"))
        assertEquals("gool", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.GOOL), 10819), "--protocol"))
        assertEquals("mim", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.MIM), 10819), "--protocol"))
        assertEquals("gool", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.WG_OVER_MASQUE), 10819), "--protocol"))
        assertTrue("--gool-classic" in AetherCoreManager.buildArguments(profile(AetherProtocol.GOOL), 10819))
        for (protocol in AetherProtocol.entries - AetherProtocol.GOOL) {
            assertFalse(protocol.type, "--gool-classic" in AetherCoreManager.buildArguments(profile(protocol), 10819))
        }
    }

    @Test
    fun theProtocolOfTheArgumentsOfAProfileIsTheProfiles() {
        for (protocol in AetherProtocol.entries) {
            for (scan in listOf(false, true)) {
                val hopless = AetherCoreManager.buildArguments(profile(protocol), 10819, scan)
                assertEquals("${protocol.type} scan=$scan", protocol, AetherCoreManager.protocolOf(hopless))
                val hops = AetherCoreManager.buildArguments(
                    profile(protocol, outer = "162.159.192.1:443", inner = "188.114.96.1:2408"),
                    10819,
                    scan,
                )
                assertEquals("${protocol.type} hops scan=$scan", protocol, AetherCoreManager.protocolOf(hops))
            }
        }
    }

    @Test
    fun uriRoundTripAndNormalizePreservesSettings() {
        val original = profile(
            protocol = AetherProtocol.MASQUE,
            transport = AetherTransport.HTTP2,
            server = "162.159.197.3",
            port = "443",
            fragment = true,
        ).apply {
            aetherFragmentSize = "40-100"
            aetherFragmentDelay = "10-50"
            aetherFingerprint = AetherFingerprint.FIREFOX.type
            aetherDns = "1.1.1.1, 8.8.8.8"
            aetherExitLoc = "de,nl"
        }
        assertNull(AetherFmt.normalize(original, emptySet(), 1819))
        val uri = AetherFmt.toUri(original)
        val parsed = AetherFmt.parse(uri)
        assertNotNull(parsed)
        assertEquals("162.159.197.3", parsed?.server)
        assertEquals("443", parsed?.serverPort)
        assertEquals("40-100", parsed?.aetherFragmentSize)
        assertEquals("10-50", parsed?.aetherFragmentDelay)
        assertEquals("firefox", parsed?.aetherFingerprint)
    }

    @Test
    fun aetherKeysBuildsRegistrationArgumentsAndValidates() {
        val settings = AetherKeysSettings(
            kind = AetherKeyKind.WG_OVER_MASQUE,
            enrollAddress = "162.159.137.105:443",
            apiSni = "www.cloudflare.com",
            apiHost = "api.cloudflareclient.com",
            fingerprint = AetherFingerprint.SAFARI,
            fragment = true,
            fragmentSize = "20-60",
            fragmentDelay = "5-15",
            ech = true,
        )
        assertNull(AetherKeys.validate(settings))
        val args = AetherKeys.arguments(settings)
        assertNotNull(args)
        assertEquals("gool", valueAfter(args!!, "--register"))
        assertEquals("162.159.137.105:443", valueAfter(args, "--api-address"))
        assertEquals("www.cloudflare.com", valueAfter(args, "--api-sni"))
        assertEquals("api.cloudflareclient.com", valueAfter(args, "--api-host"))
        assertTrue("--fragment" in args)
        assertEquals("20-60", valueAfter(args, "--fragment-size"))
        assertEquals("5-15", valueAfter(args, "--fragment-delay"))
    }

    @Test
    fun commandParsingValidatesAndExtractsListeners() {
        val core = AetherCore.ofCommand("aether --protocol wg --bind 127.0.0.1:1819", 1819)
        assertNotNull(core)
        assertEquals(1819, core?.port)
        assertEquals(listOf(1819), core?.ports)
    }

    @Test
    fun v24ReadinessAndNoEchKeyWordsMatchCoreOutput() {
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--protocol", "masque", "--bind", "127.0.0.1:1819")))
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--protocol", "wg", "--bind", "127.0.0.1:1819")))
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--tor-only", "--bind", "127.0.0.1:1819")))
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--protocol", "masque", "--psiphon-reverse")))
        assertTrue(AetherCoreManager.readyNeedsWord(listOf("--psiphon-only", "--bind", "127.0.0.1:1819")))
        assertTrue(AetherCoreManager.readyNeedsWord(listOf("--protocol", "masque", "--psiphon")))

        assertTrue(AetherCoreManager.isReadyWord("[+] psiphon is ready; 127.0.0.1:1819 leaves through psiphon"))
        assertFalse(AetherCoreManager.isReadyWord("[+] identity ready: device=abc ipv4=172.16.0.2"))

        assertTrue(AetherCoreManager.isNoEchKeyWord("[-] ech: ECH is on but there is no ECH key to offer"))
        assertFalse(AetherCoreManager.isNoEchKeyWord("[*] looking up ECH key"))
    }

    @Test
    fun upstreamNormalizationAndProvisioningProxyRoutingWorksForLoopback() {
        assertEquals("", AetherFmt.normalizeUpstreamUrl("   "))
        assertEquals("socks5://127.0.0.1:10808", AetherFmt.normalizeUpstreamUrl("10808"))
        assertEquals("socks5://127.0.0.1:1080", AetherFmt.normalizeUpstreamUrl("127.0.0.1:1080"))
        assertEquals("socks5://[::1]:10808", AetherFmt.normalizeUpstreamUrl("[::1]:10808"))
        assertEquals("socks5h://127.0.0.1:9050", AetherFmt.normalizeUpstreamUrl("SOCKS5H://127.0.0.1:9050/"))
        assertEquals("http://user:pass@127.0.0.1:8080", AetherFmt.normalizeUpstreamUrl("http://user:pass@127.0.0.1:8080"))
        assertNull(AetherFmt.normalizeUpstreamUrl("ftp://127.0.0.1:1080"))
        assertNull(AetherFmt.normalizeUpstreamUrl("127.0.0.1:99999"))

        val keySettings = AetherKeysSettings(
            kind = AetherKeyKind.ALL,
            upstreamProxy = "127.0.0.1:10808",
        )
        assertNull(AetherKeys.validate(keySettings))
        val keyArgs = AetherKeys.arguments(keySettings)
        assertNotNull(keyArgs)
        assertEquals("all", valueAfter(keyArgs!!, "--register"))
        assertEquals("socks5://127.0.0.1:10808", valueAfter(keyArgs, "--upstream"))

        // Auto-detects active local 127.0.0.1 SOCKS5 proxy during registration
        val routed = AetherCoreManager.standaloneArguments(
            arguments = listOf("--register", "all"),
            exit = AetherExit.PLAIN,
            excludePorts = setOf(1819),
            autoDetectLocalSocks = true,
            probe = { port -> port == 10808 },
        )
        assertEquals("socks5://127.0.0.1:10808", valueAfter(routed, "--upstream"))

        // Falls back to local RSTA relay when RSTA is active and no SOCKS5 proxy answers
        val rstaRouted = AetherCoreManager.standaloneArguments(
            arguments = listOf("--register", "masque"),
            exit = AetherExit.PLAIN,
            autoDetectLocalSocks = true,
            rstaListenPort = 40443,
            probe = { false },
        )
        assertEquals("127.0.0.1:40443", valueAfter(rstaRouted, "--api-address"))
    }

    @Test
    fun cancellingMultiIdentityRenewalCleansStagingAndPreservesExistingKeys() = runBlocking {
        val root = Files.createTempDirectory("aether-cancel-test").toFile()
        try {
            val workDir = java.io.File(root, "aether").apply { mkdirs() }
            val renewalDir = java.io.File(root, "aether-renewal")
            val existingMasque = java.io.File(workDir, AetherIdentityManager.MASQUE_FILE)
            existingMasque.writeText(
                """
                device_id = "existing-device"
                ipv4 = "172.16.0.2"
                ipv6 = "2606:4700::2"
                """.trimIndent()
            )

            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                AetherIdentityManager.renew(
                    workDir = workDir,
                    renewalDir = renewalDir,
                    files = AetherIdentityManager.KEY_FILES,
                    io = Dispatchers.IO,
                ) {
                    java.io.File(renewalDir, AetherIdentityManager.MASQUE_FILE).writeText(
                        """
                        device_id = "partial-device"
                        ipv4 = "172.16.0.99"
                        ipv6 = "2606:4700::99"
                        """.trimIndent()
                    )
                    started.complete(Unit)
                    delay(60_000L)
                    true
                }
            }

            started.await()
            job.cancelAndJoin()

            assertFalse("renewal staging directory must be removed on cancel", renewalDir.exists())
            val preserved = AetherIdentityManager.read(existingMasque)
            assertNotNull(preserved)
            assertEquals("existing-device", preserved?.deviceId)
        } finally {
            root.deleteRecursively()
        }
    }
}
