package com.manjaniq

import com.manjaniq.core.AetherCore
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.AetherIpVersion
import com.manjaniq.core.AetherKeyKind
import com.manjaniq.core.AetherKeys
import com.manjaniq.core.AetherKeysSettings
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.AetherTransport
import com.manjaniq.core.ProfileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
