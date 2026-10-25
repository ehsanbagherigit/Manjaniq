package com.manjaniq

import com.manjaniq.core.AetherEndpoint
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AetherScannerTest {

    private fun logLine(message: String) = "[2026-09-11T10:00:00.000Z INFO  aether] $message"

    @Test
    fun readsTheMasqueGatewayOutOfTheLog() {
        val found = AetherScanner.parse(AetherProtocol.MASQUE, logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)"))
        assertEquals(AetherEndpoint("162.159.197.3", 443), found?.endpoint)
        assertNull(found?.innerHop)
    }

    @Test
    fun readsAnIpv6MasqueGatewayWrittenWithoutBrackets() {
        val found = AetherScanner.parse(AetherProtocol.MASQUE, logLine("[+] selected MASQUE gateway 2606:4700:102::3:443 (rtt 90ms)"))
        assertEquals(AetherEndpoint("2606:4700:102::3", 443), found?.endpoint)
    }

    @Test
    fun readsTheWireguardEndpointOutOfTheLog() {
        val found = AetherScanner.parse(
            AetherProtocol.WIREGUARD,
            logLine("[+] selected WireGuard endpoint 162.159.192.1:894 using aethernoize profile 'balanced'")
        )
        assertEquals(AetherEndpoint("162.159.192.1", 894), found?.endpoint)

        val ipv6 = AetherScanner.parse(
            AetherProtocol.WIREGUARD,
            logLine("[+] selected WireGuard endpoint [2606:4700:d0::a29f:c001]:2408 using aethernoize profile 'light'")
        )
        assertEquals(AetherEndpoint("2606:4700:d0::a29f:c001", 2408), ipv6?.endpoint)
    }

    @Test
    fun readsBothGoolHopsOutOfOneLine() {
        val found = AetherScanner.parse(
            AetherProtocol.GOOL,
            logLine("[+] using cloudflare edge 162.159.192.1:2408 (outer) and 188.114.96.1:894 (inner)")
        )
        assertEquals(AetherEndpoint("162.159.192.1", 2408), found?.endpoint)
        assertEquals(AetherEndpoint("188.114.96.1", 894), found?.innerHop)
    }

    @Test
    fun goolWaitsForBothHopsInsteadOfTheFirstEndpoint() {
        assertNull(AetherScanner.parse(AetherProtocol.GOOL, logLine("[+] selected WireGuard endpoint 162.159.192.1:2408 (rtt 61ms)")))
        assertNull(AetherScanner.parse(AetherProtocol.GOOL, logLine("[+] using cloudflare edge 162.159.192.1:2408")))
    }

    @Test
    fun readsBothMimHopsOutOfTheReadyLine() {
        val found = AetherScanner.parse(
            AetherProtocol.MIM,
            logLine("[+] masque-in-masque ready: 162.159.192.1:443 (outer) and 188.114.96.1:443 (inner)")
        )
        assertEquals(AetherEndpoint("162.159.192.1", 443), found?.endpoint)
        assertEquals(AetherEndpoint("188.114.96.1", 443), found?.innerHop)

        val ipv6 = AetherScanner.parse(
            AetherProtocol.MIM,
            logLine("[+] masque-in-masque ready: [2606:4700:102::3]:443 (outer) and [2606:4700:103::4]:443 (inner)")
        )
        assertEquals(AetherEndpoint("2606:4700:102::3", 443), ipv6?.endpoint)
        assertEquals(AetherEndpoint("2606:4700:103::4", 443), ipv6?.innerHop)
    }

    @Test
    fun wireGuardOverMasqueEndsOnItsReadyLineWithTheGatewayAlone() {
        assertNull(AetherScanner.parse(AetherProtocol.WG_OVER_MASQUE, logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)")))
        assertNull(AetherScanner.parse(AetherProtocol.WG_OVER_MASQUE, logLine("[+] using cloudflare edge 162.159.197.3:443")))
        val found = AetherScanner.parse(
            AetherProtocol.WG_OVER_MASQUE,
            logLine("[+] gool ready: masque 162.159.197.3:443 carries wireguard 162.159.192.1:2408")
        )
        assertEquals(AetherEndpoint("162.159.197.3", 443), found?.endpoint)
        assertNull(found?.innerHop)
    }

    @Test
    fun withAnExitRuleTheScanEndsOnTheEndpointWhoseExitTheCoreAccepted() {
        val match = AetherScanner.matcher(AetherProtocol.MASQUE, exitRuled = true)
        assertNull(match(logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)")))
        assertNull(match(logLine("[-] exit location IR rejected (not IR,RU)")))
        assertNull(match(logLine("[+] exit location DE accepted (not IR,RU)")))
        assertNull(match(logLine("[+] selected MASQUE gateway 188.114.96.1:443 (rtt 90ms)")))
        assertEquals(AetherEndpoint("188.114.96.1", 443), match(logLine("[+] exit location DE accepted (not IR,RU)"))?.endpoint)
    }
}
