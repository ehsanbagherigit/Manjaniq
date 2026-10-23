package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.AetherCapabilities
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherFragmenting
import com.manjaniq.core.models.AetherIpChoice
import com.manjaniq.core.models.AetherNoizeChoice
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.ProfileCapability
import com.manjaniq.core.models.AetherReach
import com.manjaniq.core.models.AetherScanChoice
import com.manjaniq.core.models.AetherTransportChoice
import com.manjaniq.core.models.getSectionsForProfile
import com.manjaniq.core.models.profileSupportsFragmenting
import com.manjaniq.core.models.profileUsesAetherGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Aether capability inventory, pinned as executable documentation.
 *
 * Every value here is traceable to the vendored Aether v2.3.0 source:
 *  - transports: `aether/src/api.rs` `Transport::parse` maps `wg|wireguard|warp`
 *    to WireGuard and *everything else* to Masque, so gool/mim cannot be
 *    requested through `aether_tunnel_start`;
 *  - scan modes: `aether/src/prober.rs` `ScanMode::parse`;
 *  - IP versions: `aether/src/prober.rs` `IpScan::parse`;
 *  - obfuscation profiles: `aether/src/noize.rs` and `aethernoize.rs`
 *    `from_profile`, which both accept `light`;
 *  - fragmenting: `aether/src/fragment.rs`, applied only in `masque_h2.rs` and
 *    configured only through `AETHER_MASQUE_H2_FRAGMENT*`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherCapabilitiesTest {

    @Test
    fun everySettingKeyIsListedExactlyOnce() {
        val keys = AetherCapabilities.ALL.map { it.key }
        assertEquals("duplicate keys: $keys", keys.size, keys.distinct().size)
        // The base config's fields must all be classified, or the UI cannot say
        // how far a setting reaches.
        listOf(
            "transport", "scanMode", "ipVersion", "socksBind", "httpProxyBind", "peer",
            "identityPath", "echEnabled", "noizeProfile", "teamName", "dns", "upstream",
            "quickReconnect", "fragmentEnabled", "fragmentSize", "fragmentDelay",
            "fragmentSniSplit", "psiphonEnabled", "psiphonEndpoint", "psiphonRegion",
            "torSocks", "torBridges", "torUseBridges"
        ).forEach { key ->
            assertNotNull("capability row missing for '$key'", AetherCapabilities.of(key))
        }
        AetherCapabilities.ALL.forEach { info ->
            assertTrue("reason text must exist for ${info.key}", info.reasonRes != 0)
        }
    }

    @Test
    fun onlySettingsTheFfiPathCarriesAreMarkedApplied() {
        // aether_identity_open / aether_scan_start / aether_tunnel_start payloads.
        listOf("transport", "scanMode", "ipVersion", "socksBind", "httpProxyBind", "peer", "identityPath")
            .forEach { key ->
                assertEquals(key, AetherReach.Applied, AetherCapabilities.reachOf(key))
                assertTrue(key, AetherCapabilities.isForwarded(key))
            }
        // Forwarded, but honoured only on part of the path. Fragmenting is in this
        // group because AetherService writes it into the engine's environment before
        // the scan (AetherBridge.fragmentingApply) and fragment.rs has exactly one
        // consumer, masque_h2.rs: it applies on the MASQUE-over-HTTP/2 carrier and on
        // nothing else.
        listOf(
            "echEnabled", "noizeProfile", "teamName", "fragmentEnabled", "fragmentSize",
            "fragmentDelay", "fragmentSniSplit"
        ).forEach { key ->
            assertEquals(key, AetherReach.Partial, AetherCapabilities.reachOf(key))
            assertTrue(key, AetherCapabilities.isForwarded(key))
        }
        // No field on the FFI path in use: environment- or CLI-only upstream.
        listOf(
            "dns", "upstream", "quickReconnect", "psiphonEnabled", "psiphonEndpoint",
            "psiphonRegion", "torSocks", "torBridges", "torUseBridges"
        ).forEach { key ->
            assertEquals(key, AetherReach.NotForwarded, AetherCapabilities.reachOf(key))
            assertFalse(key, AetherCapabilities.isForwarded(key))
        }
        // An unknown key must not be reported as applied.
        assertEquals(AetherReach.NotForwarded, AetherCapabilities.reachOf("noSuchSetting"))
        assertFalse(AetherCapabilities.isForwarded("noSuchSetting"))
    }

    @Test
    fun transportsMatchTheAetherCliAndFlagTheUnreachableOnes() {
        assertEquals(listOf("masque", "wg", "gool", "mim"), AetherTransportChoice.IDS)
        // api::Transport::parse understands only these two on the tunnel path.
        assertEquals(listOf("masque", "wg"), AetherTransportChoice.SELECTABLE_IDS)
        assertTrue(AetherTransportChoice.isSelectable("masque"))
        assertTrue(AetherTransportChoice.isSelectable("wg"))
        assertFalse("gool degrades to plain MASQUE via api::Transport", AetherTransportChoice.isSelectable("gool"))
        assertFalse("mim degrades to plain MASQUE via api::Transport", AetherTransportChoice.isSelectable("mim"))
        assertEquals(AetherTransportChoice.MASQUE, AetherTransportChoice.byId("masque"))
        assertEquals(AetherTransportChoice.WIREGUARD, AetherTransportChoice.byId("wg"))
        assertNull(AetherTransportChoice.byId("h3"))
        // Aether's own default, and the default the app ships.
        assertEquals("masque", AetherConfig.DEFAULT.transport)
        AetherTransportChoice.ALL.forEach { assertTrue(it.id, it.detailRes != 0) }
    }

    @Test
    fun aTransportIsSelectableExactlyWhenThisBuildCanDispatchIt() {
        // `api::Transport` has only Masque and WireGuard (api.rs:13-16) and its
        // `parse` maps every other id to Masque, so gool and mim are reachable
        // only through `aether_core_start` (ffi.rs:507 -> lib.rs `run_with` ->
        // AETHER_PROTOCOL), which nothing in this app calls. `selectable` drives
        // both the chip's clickability and `validate()`, so the UI and the start
        // path can never disagree about what is dispatchable.
        AetherTransportChoice.ALL.forEach { choice ->
            val dispatchable = choice.id == "masque" || choice.id == "wg"
            assertEquals(choice.id, dispatchable, choice.selectable)
            assertEquals(choice.id, dispatchable, AetherTransportChoice.isSelectable(choice.id))
            val message = AetherConfig.DEFAULT.copy(transport = choice.id).validate()
            if (dispatchable) {
                assertNull("${choice.id} must be startable", message)
            } else {
                // Refused before any FFI call, and the message says what would
                // otherwise have run instead of the requested protocol.
                assertNotNull("${choice.id} must not reach the FFI", message)
                assertTrue(message!!.contains(choice.id))
                assertTrue(message!!.contains("MASQUE"))
            }
        }
        assertEquals(listOf("masque", "wg"), AetherTransportChoice.SELECTABLE_IDS)
    }

    @Test
    fun allFourFragmentingKeysAreAppliedThroughTheBridge() {
        // aether_tunnel_start's payload (ffi.rs:246-260) still has no fragmenting
        // field, and that is not how the values travel any more: AetherService calls
        // AetherBridge.fragmentingApply before the scan, whose JNI side writes the
        // five variables fragment.rs and masque_h2::enabled() read. Partial rather
        // than Applied, because one carrier is affected and the others are not.
        listOf("fragmentEnabled", "fragmentSize", "fragmentDelay", "fragmentSniSplit").forEach { key ->
            assertEquals(key, AetherReach.Partial, AetherCapabilities.reachOf(key))
            assertTrue(key, AetherCapabilities.isForwarded(key))
            assertNotNull(key, AetherCapabilities.of(key))
            assertTrue("$key must carry a reason", AetherCapabilities.of(key)!!.reasonRes != 0)
        }
        // The SNI split keeps its own reason: upstream has no --fragment-sni flag, so
        // the bridge is the only way it reaches Aether at all.
        assertTrue(
            AetherCapabilities.reasonResFor("fragmentSniSplit") !=
                AetherCapabilities.reasonResFor("fragmentSize")
        )
        // Fragmenting is still not a per-route setting group: it belongs to one
        // carrier, so it is edited once, for whichever route is selected.
        AetherProfile.ALL.forEach { profile ->
            val titles = getSectionsForProfile(profile).map { it.titleRes }
            assertFalse("$profile must not own a fragmenting section", titles.contains(R.string.section_fragmenting))
            val keys = getSectionsForProfile(profile).flatMap { it.keys }
            assertFalse("$profile must not offer fragment keys as settings", keys.contains("fragmentEnabled"))
        }
    }

    @Test
    fun scanModesMatchProberScanMode() {
        assertEquals(
            listOf("turbo", "balanced", "thorough", "verified", "ironclad"),
            AetherScanChoice.ALL.map { it.id }
        )
        assertEquals(AetherScanChoice.IDS, AetherScanChoice.ALL.map { it.id })
        assertEquals("balanced", AetherConfig.DEFAULT.scanMode)
        assertTrue(AetherConfig.DEFAULT.scanMode in AetherScanChoice.IDS)
    }

    @Test
    fun ipVersionsMatchProberIpScan() {
        assertEquals(listOf("v4", "v6", "both"), AetherIpChoice.IDS)
        assertEquals("v4", AetherConfig.DEFAULT.ipVersion)
    }

    @Test
    fun obfuscationProfilesIncludeLightWhichTheUiUsedToOmit() {
        // noize::from_profile and aethernoize::from_profile both accept "light";
        // the previous chip list offered five of the six values.
        assertEquals(
            listOf("off", "light", "firewall", "balanced", "gfw", "aggressive"),
            AetherNoizeChoice.ALL.map { it.id }
        )
        assertEquals(AetherNoizeChoice.IDS, AetherNoizeChoice.ALL.map { it.id })
        assertEquals("firewall", AetherConfig.DEFAULT.noizeProfile)
        assertTrue(AetherConfig.DEFAULT.noizeProfile in AetherNoizeChoice.IDS)
        AetherNoizeChoice.ALL.forEach { assertTrue(it.id, it.detailRes != 0) }
    }

    @Test
    fun fragmentingIsEnvironmentOnlyUpstreamAndTheBridgeWritesThoseVariables() {
        // The carrier variable is listed with them because fragmenting cannot run
        // without it: masque_h2.rs is fragment.rs's only consumer, and
        // masque_h2::enabled() reads AETHER_MASQUE_HTTP2 (masque_h2.rs:141-149).
        assertEquals(
            listOf(
                "AETHER_MASQUE_HTTP2",
                "AETHER_MASQUE_H2_FRAGMENT",
                "AETHER_MASQUE_H2_FRAGMENT_SIZE",
                "AETHER_MASQUE_H2_FRAGMENT_DELAY",
                "AETHER_MASQUE_H2_FRAGMENT_SNI"
            ),
            AetherFragmenting.ENV_VARS
        )
        assertEquals(AetherReach.Partial, AetherCapabilities.reachOf("fragmentEnabled"))
        assertEquals(
            R.string.aether_reach_partial_fragment,
            AetherCapabilities.reasonResFor("fragmentEnabled")
        )
        assertEquals(
            R.string.aether_reach_partial_fragment_sni,
            AetherCapabilities.reasonResFor("fragmentSniSplit")
        )
    }

    @Test
    fun everySettingAProfileOwnsIsClassified() {
        // Guards the section -> setting mapping: a typo'd key would render
        // nothing and silently drop an Aether capability from the UI.
        AetherProfile.ALL.forEach { profile ->
            val sections = getSectionsForProfile(profile)
            assertTrue("$profile must own at least one section", sections.isNotEmpty())
            sections.forEach { section ->
                assertTrue("$profile/${section.titleRes} must own settings", section.keys.isNotEmpty())
                section.keys.forEach { key ->
                    assertNotNull("$profile offers unknown setting '$key'", AetherCapabilities.of(key))
                }
            }
            // No setting may be listed twice for the same route.
            val keys = sections.flatMap { it.keys }
            assertEquals("$profile lists a setting twice: $keys", keys.size, keys.distinct().size)
        }
    }

    @Test
    fun fragmentingIsOfferedExactlyWhereMasqueCanRun() {
        assertTrue(profileSupportsFragmenting(AetherProfile.WARP_DIRECT))
        assertTrue(profileSupportsFragmenting(AetherProfile.AETHER_PSIPHON))
        assertTrue(profileSupportsFragmenting(AetherProfile.AETHER_TOR))
        assertTrue(profileSupportsFragmenting(AetherProfile.TOR_AETHER))
        // Tor-only routes never carry MASQUE, so fragmenting has no meaning.
        assertFalse(profileSupportsFragmenting(AetherProfile.TOR_DIRECT))
        assertFalse(profileSupportsFragmenting(AetherProfile.TOR_PSIPHON))

        AetherProfile.ALL.forEach { profile ->
            assertEquals(
                "$profile gateway classification",
                profile != AetherProfile.TOR_DIRECT && profile != AetherProfile.TOR_PSIPHON,
                profileUsesAetherGateway(profile)
            )
        }
    }

    @Test
    fun onlyOneRouteIsAvailableWithoutAnExternalComponent() {
        // The shipping table: the Tor routes need configuration (tor is compiled
        // into libaether.so, but a route only applies its settings on the
        // full-engine start path), and the Psiphon routes need the
        // psiphon-tunnel-core child process. The UI must keep showing all of
        // them, not hide them.
        val available = AetherProfile.ALL.filter { it.capability.name == "Available" }
        assertEquals(listOf(AetherProfile.WARP_DIRECT), available)
        // What this installation can do, which is the badge the route cards show.
        assertEquals(ProfileCapability.Available, AetherProfile.WARP_DIRECT.capabilityInThisBuild())
        listOf(AetherProfile.TOR_DIRECT, AetherProfile.AETHER_TOR, AetherProfile.TOR_AETHER)
            .forEach { profile ->
                assertEquals(profile.name, ProfileCapability.NeedsConfiguration, profile.capabilityInThisBuild())
            }
        // The host JVM has no packaged helper, so these two stay honest about the
        // child process they need; on a device the check reads nativeLibraryDir.
        listOf(AetherProfile.AETHER_PSIPHON, AetherProfile.TOR_PSIPHON).forEach { profile ->
            assertEquals(
                profile.name,
                ProfileCapability.RequiresExternalComponent,
                profile.capabilityInThisBuild()
            )
        }
        assertEquals(6, AetherProfile.ALL.size)
        assertEquals(AetherProfile.WARP_DIRECT, AetherProfile.byId("warp_direct"))
        assertEquals(
            "an unknown id must fall back, not crash",
            AetherProfile.WARP_DIRECT,
            AetherProfile.byId("does_not_exist")
        )
    }
}
