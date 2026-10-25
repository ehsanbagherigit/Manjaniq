package com.manjaniq

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.manjaniq.core.AetherIpVersion
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.AetherPsiphon
import com.manjaniq.core.ProfileItem
import com.manjaniq.ui.server.AetherUiState
import org.junit.Assert.assertEquals
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
}
