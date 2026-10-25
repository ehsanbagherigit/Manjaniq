package com.manjaniq

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.AetherIpVersion
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.AetherPsiphon
import com.manjaniq.core.AetherTorRelays
import com.manjaniq.core.ProfileItem
import com.manjaniq.ui.server.AetherUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AetherUiOptionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun scanMenuContainsEveryCoreModeAndStoresItsExactValue() {
        val values = context.resources.getStringArray(R.array.aether_scan_values).toList()
        val labels = context.resources.getStringArray(R.array.aether_scan_entries).toList()

        assertEquals(AetherScanMode.entries.map { it.type }, values)
        assertEquals(values.size, labels.size)
        values.forEach { assertEquals(it, AetherScanMode.fromString(it).type) }
        assertEquals(AetherScanMode.THOROUGH, AetherScanMode.fromString("aggressive"))
        assertEquals(AetherScanMode.BALANCED, AetherScanMode.fromString("normal"))
        assertEquals(AetherScanMode.VERIFIED, AetherScanMode.fromString("gentle"))
    }

    @Test
    fun obfuscationMenuContainsEveryCoreProfileAndStoresItsExactValue() {
        val values = context.resources.getStringArray(R.array.aether_obfuscation_values).toList()
        val labels = context.resources.getStringArray(R.array.aether_obfuscation_entries).toList()

        assertEquals(AetherObfuscation.entries.map { it.type }, values)
        assertEquals(values.size, labels.size)
        values.forEach { assertEquals(it, AetherObfuscation.fromString(it).type) }
        assertEquals(AetherObfuscation.BALANCED, AetherObfuscation.fromString("standard"))
        assertEquals(AetherObfuscation.AGGRESSIVE, AetherObfuscation.fromString("heavy"))
    }

    @Test
    fun exitCountryCodesAreOnlyAppliedWhenPsiphonAndWarpAreEnabled() {
        val config = ProfileItem.create().apply { aetherExitLoc = "NL" }

        AetherUiState.fromProfile(config).applyTo(config)
        assertEquals(null, config.aetherExitLoc)

        AetherUiState.fromProfile(config).copy(
            psiphon = AetherPsiphon.CHAIN,
            exitLoc = "nl,de",
        ).applyTo(config)
        assertEquals("NL,DE", config.aetherExitLoc)
    }

    @Test
    fun ipVersionMenuUsesValuesTheProfileModelRecognizes() {
        val values = context.resources.getStringArray(R.array.aether_ip_values).toList()
        val labels = context.resources.getStringArray(R.array.aether_ip_entries).toList()

        assertEquals(AetherIpVersion.entries.map { it.type }, values)
        assertEquals(values.size, labels.size)
        values.forEach { assertEquals(it, AetherIpVersion.fromString(it).type) }
        assertEquals(AetherIpVersion.V4, AetherIpVersion.fromString("4"))
        assertEquals(AetherIpVersion.V6, AetherIpVersion.fromString("6"))
        assertEquals(AetherIpVersion.DUAL, AetherIpVersion.fromString("dual"))
        assertTrue(labels.all { it.contains("IPv") })
    }

    @Test
    fun v24FingerprintTorRelaysEchAndApiArraysMatchCoreValidators() {
        val fpValues = context.resources.getStringArray(R.array.aether_fingerprint_values).toList()
        val fpLabels = context.resources.getStringArray(R.array.aether_fingerprint_entries).toList()
        assertEquals(AetherFingerprint.entries.map { it.type }, fpValues)
        assertEquals(fpValues.size, fpLabels.size)

        val relayValues = context.resources.getStringArray(R.array.aether_tor_relays_values).toList()
        val relayLabels = context.resources.getStringArray(R.array.aether_tor_relays_entries).toList()
        assertEquals(AetherTorRelays.entries.map { it.type }, relayValues)
        assertEquals(relayValues.size, relayLabels.size)

        val echDnsOptions = context.resources.getStringArray(R.array.aether_ech_dns_options).toList()
        echDnsOptions.forEach { option ->
            assertTrue("ECH DNS option must be valid: $option", AetherFmt.isEchDns(option))
        }

        val apiHosts = context.resources.getStringArray(R.array.aether_api_name_options).toList()
        assertTrue(apiHosts.contains("api.devices.cloudflare.com"))
        assertTrue(apiHosts.contains("api.cloudflareclient.com"))
        apiHosts.forEach { host ->
            assertTrue("API host option must be valid SNI/host: $host", AetherFmt.isMasqueSni(host))
        }

        val upstreams = context.resources.getStringArray(R.array.aether_upstream_options).toList()
        upstreams.forEach { proxy ->
            assertNotNull("Upstream preset must normalize cleanly: $proxy", AetherFmt.normalizeUpstreamUrl(proxy))
        }
    }
}
