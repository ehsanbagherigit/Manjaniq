package com.manjaniq

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherNetworkCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The connectivity facts the app reports next to a registration that could not
 * reach the WARP API.
 *
 * [AetherNetworkCheck.factsOf] only reads what the platform already knows, so the
 * assertions here are about what the app *says*: every branch must be truthful, an
 * unread state must be reported as unread rather than as "offline", and nothing may
 * suggest bypassing a security check. The platform read itself is exercised as a
 * smoke test only - Robolectric's shadow decides which capabilities the default
 * network carries, so asserting those values would test the shadow, not the app.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherNetworkCheckTest {

    private fun facts(
        activeNetwork: Boolean = true,
        validated: Boolean = true,
        internet: Boolean = true,
        captivePortal: Boolean = false,
        transports: List<String> = listOf("wifi"),
        dnsServers: Int = 2,
        systemProxy: String? = null,
        metered: Boolean = false
    ) = AetherNetworkCheck.Facts(
        activeNetwork = activeNetwork,
        validated = validated,
        internet = internet,
        captivePortal = captivePortal,
        transports = transports,
        dnsServers = dnsServers,
        systemProxy = systemProxy,
        metered = metered
    )

    @Test
    fun anUnreadNetworkStateIsReportedAsUnreadNotAsOffline() {
        val line = AetherNetworkCheck.summarize(null)
        assertTrue(line.contains("could not be read"))
        assertFalse("it must not claim the device is offline", line.contains("none active"))
    }

    @Test
    fun noActiveNetworkIsSaidPlainly() {
        val line = AetherNetworkCheck.summarize(facts(activeNetwork = false))
        assertTrue(line.contains("none active"))
        assertFalse(AetherNetworkCheck.looksReachable(facts(activeNetwork = false)))
    }

    @Test
    fun aValidatedNetworkIsReportedWithTheFactsThatMatter() {
        val line = AetherNetworkCheck.summarize(facts())
        assertTrue(line.contains("wifi"))
        assertTrue(line.contains("validated"))
        assertTrue(line.contains("2 DNS server(s)"))
        assertTrue(line.contains("no system proxy"))
        assertTrue(AetherNetworkCheck.looksReachable(facts()))
    }

    @Test
    fun aCaptivePortalIsNamedAsTheReasonHttpsWouldNotGetThrough() {
        val unvalidated = facts(validated = false, captivePortal = true)
        val line = AetherNetworkCheck.summarize(unvalidated)
        assertTrue(line.contains("captive portal"))
        assertFalse(AetherNetworkCheck.looksReachable(unvalidated))
    }

    @Test
    fun anUnvalidatedNetworkIsNotReportedAsUsable() {
        val unvalidated = facts(validated = false)
        assertTrue(AetherNetworkCheck.summarize(unvalidated).contains("not validated"))
        assertFalse(AetherNetworkCheck.looksReachable(unvalidated))
    }

    @Test
    fun aSystemProxyIsReportedRatherThanHidden() {
        val proxied = facts(systemProxy = "10.0.0.1:8080")
        assertTrue(AetherNetworkCheck.summarize(proxied).contains("system proxy 10.0.0.1:8080"))
        // A proxy does not by itself mean the device is offline, so it stays reachable
        assertTrue(AetherNetworkCheck.looksReachable(proxied))
    }

    @Test
    fun missingDnsIsReportedAsMissing() {
        assertTrue(AetherNetworkCheck.summarize(facts(dnsServers = 0)).contains("no DNS server reported"))
    }

    @Test
    fun transportsAreListedTogetherSoAVpnIsVisible() {
        val line = AetherNetworkCheck.summarize(facts(transports = listOf("wifi", "vpn")))
        assertTrue(line.contains("wifi+vpn"))
    }

    @Test
    fun noSuggestionWeakensACheck() {
        val lines = listOf(
            AetherNetworkCheck.summarize(null),
            AetherNetworkCheck.summarize(facts(activeNetwork = false)),
            AetherNetworkCheck.summarize(facts(validated = false, captivePortal = true)),
            AetherNetworkCheck.summarize(facts())
        )
        for (line in lines) {
            val lower = line.lowercase()
            assertFalse(lower.contains("disable"))
            assertFalse(lower.contains("insecure"))
            assertFalse(lower.contains("bypass"))
        }
    }

    @Test
    fun readingThePlatformStateDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val read = AetherNetworkCheck.factsOf(context)
        val line = AetherNetworkCheck.summarize(read)
        assertTrue(
            "the line must describe the device, whatever the shadow reported: $line",
            line.startsWith("Device network")
        )
        assertEquals(line, AetherNetworkCheck.summarize(read))
    }
}
