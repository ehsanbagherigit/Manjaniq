package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherFragmenting
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Persistence of the Aether configuration, including the fragmenting fields.
 *
 * Robolectric because `org.json` is only a stub on the plain host JVM.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherConfigJsonTest {

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val cfg = AetherConfig(
            identityPath = "identity_two",
            transport = "wg",
            scanMode = "ironclad",
            noizeProfile = "light",
            socksBind = "127.0.0.1:1919",
            httpProxyBind = "127.0.0.1:1920",
            peer = "198.51.100.7:2408",
            upstream = "socks5://127.0.0.1:1080",
            echEnabled = true,
            dns = "9.9.9.9,149.112.112.112",
            ipVersion = "both",
            teamName = "example-team",
            quickReconnect = false,
            // Off here on purpose: this fixture is a WireGuard route, and fragmenting
            // is valid on MASQUE only - fragment.rs has exactly one consumer,
            // masque_h2.rs, so a WireGuard route asking for it is refused by
            // validate(). The three value fields stay non-default so the round trip
            // still carries them; fragmentEnabled = true is round-tripped by
            // fragmentingIsPersistedNotJustHeldInMemory and by
            // profileConfigCarriesBaseAndRouteExtras below.
            fragmentEnabled = false,
            fragmentSize = "24-48",
            fragmentDelay = "3-9",
            fragmentSniSplit = true
        )
        assertNull(cfg.validate())
        assertEquals(cfg, AetherConfig.fromJson(cfg.toJson()))
        // Fragmenting is a setting, not a lock: this WireGuard route stores it and
        // starts with it either way. What the engine does with the value is stated on
        // the screen (the fragments belong to the MASQUE carrier), not enforced by
        // refusing the route.
        assertNull(cfg.copy(fragmentEnabled = true).validate())
        assertNull(cfg.copy(transport = "masque", fragmentEnabled = true).validate())
    }

    @Test
    fun fragmentingIsPersistedNotJustHeldInMemory() {
        val cfg = AetherConfig.DEFAULT.copy(
            fragmentEnabled = true,
            fragmentSize = "20-40",
            fragmentDelay = "1-5",
            fragmentSniSplit = true
        )
        val json = cfg.toJson()
        assertTrue("fragmentEnabled must be written: $json", json.contains("\"fragmentEnabled\":true"))
        assertTrue(json.contains("\"fragmentSize\":\"20-40\""))
        assertTrue(json.contains("\"fragmentDelay\":\"1-5\""))
        assertTrue(json.contains("\"fragmentSniSplit\":true"))
        assertEquals(cfg, AetherConfig.fromJson(json))
    }

    @Test
    fun configWrittenBeforeFragmentingExistedStillLoads() {
        // Exactly the shape AetherConfigStore persisted before these fields
        // existed: no fragment keys at all.
        val legacy = """{"identityPath":"aether_identity","transport":"masque","scanMode":"balanced",
            |"noizeProfile":"firewall","socksBind":"127.0.0.1:1819","httpProxyBind":"","peer":"",
            |"upstream":"","echEnabled":false,"dns":"1.1.1.1,1.0.0.1","ipVersion":"v4",
            |"teamName":"","quickReconnect":true}""".trimMargin().replace("\n", "")
        val cfg = AetherConfig.fromJson(legacy)
        assertNotNull("a stored config from an older build must still load", cfg)
        assertEquals(AetherFragmenting.DEFAULT_ENABLED, cfg!!.fragmentEnabled)
        assertEquals(AetherFragmenting.DEFAULT_SIZE, cfg.fragmentSize)
        assertEquals(AetherFragmenting.DEFAULT_DELAY, cfg.fragmentDelay)
        assertEquals(AetherFragmenting.DEFAULT_SNI_SPLIT, cfg.fragmentSniSplit)
        assertNull("migrated config must stay valid", cfg.validate())
        // Nothing else may shift while migrating.
        assertEquals("masque", cfg.transport)
        assertEquals("127.0.0.1:1819", cfg.socksBind)
        assertTrue(cfg.quickReconnect)
    }

    @Test
    fun brokenJsonYieldsNullInsteadOfACrash() {
        assertNull(AetherConfig.fromJson(""))
        assertNull(AetherConfig.fromJson("not json"))
        assertNull(AetherConfig.fromJson("{\"transport\":"))
        assertNull(AetherProfileConfig.fromJson(""))
        assertNull(AetherProfileConfig.fromJson("{"))
    }

    @Test
    fun aStoredValueAetherCannotParseIsStillRejected() {
        // fromJson is deliberately permissive (it must not lose a stored file),
        // so validation stays the gate before a start.
        val cfg = AetherConfig.fromJson("""{"transport":"quic","scanMode":"balanced"}""")
        assertNotNull(cfg)
        assertNotNull(cfg!!.validate())
    }

    @Test
    fun profileConfigCarriesBaseAndRouteExtras() {
        val pc = AetherProfileConfig(
            profileId = AetherProfile.AETHER_TOR.id,
            // Deliberately a combination validate() refuses to start: persistence is a
            // dumb carrier, so a stored value must survive the round trip rather than be
            // dropped or silently rewritten on the way through JSON.
            base = AetherConfig.DEFAULT.copy(transport = "wg", fragmentEnabled = true),
            psiphonEnabled = true,
            psiphonEndpoint = "127.0.0.1:1821",
            psiphonRegion = "DE",
            torSocks = "127.0.0.1:9050",
            torBridges = "obfs4 198.51.100.9:443 ABCD cert=xyz iat-mode=0",
            torUseBridges = true
        )
        val back = AetherProfileConfig.fromJson(pc.toJson())
        assertEquals(pc, back)
        assertEquals("wg", back!!.base.transport)
        assertTrue(back.base.fragmentEnabled)
        assertEquals("127.0.0.1:9050", back.torSocks)
    }

    @Test
    fun profileConfigWithoutABaseFallsBackToDefaults() {
        val back = AetherProfileConfig.fromJson("""{"profileId":"warp_direct"}""")
        assertNotNull(back)
        assertEquals(AetherConfig.DEFAULT, back!!.base)
        assertEquals("warp_direct", back.profileId)
    }

    @Test
    fun routeExtrasAreValidatedForTheirOwnRoute() {
        val base = AetherProfileConfig(profileId = AetherProfile.TOR_DIRECT.id)
        assertNull(base.validateForProfile(AetherProfile.TOR_DIRECT))
        assertNotNull(
            base.copy(torSocks = "127.0.0.1:http").validateForProfile(AetherProfile.TOR_DIRECT)
        )
        assertNotNull(
            base.copy(psiphonEnabled = true, psiphonEndpoint = "127.0.0.1:1")
                .validateForProfile(AetherProfile.AETHER_PSIPHON)
        )
        // An unusable base config is rejected through the profile wrapper too.
        assertNotNull(
            base.copy(base = AetherConfig.DEFAULT.copy(socksBind = "0.0.0.0:1819"))
                .validateForProfile(AetherProfile.TOR_DIRECT)
        )
    }
}
