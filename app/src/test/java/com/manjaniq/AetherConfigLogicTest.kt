package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherFragmenting
import com.manjaniq.core.models.RSTAConfig
import com.manjaniq.core.models.detectAetherPortConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Validation and CLI mapping of the Aether configuration.
 *
 * Robolectric because `AetherConfig.validate()` reads the choice enums, whose
 * labels are resource ids. No native library and no Service are involved.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherConfigLogicTest {

    @Test
    fun shippedDefaultIsValid() {
        assertNull("AetherConfig.DEFAULT must validate: ${AetherConfig.DEFAULT}", AetherConfig.DEFAULT.validate())
        assertNull(AetherConfig.DEFAULT.copy(httpProxyBind = "127.0.0.1:1820").validate())
    }

    /**
     * Every protocol id the screen offers is one the engine is started with.
     *
     * This used to be the opposite way round: three of the four protocols were
     * refused because the granular `aether_tunnel_start` could only dispatch
     * MASQUE and WireGuard, so `gool` and `mim` would have run plain MASQUE while
     * the stored configuration claimed something else. The app now starts the core
     * through its own command line, where `--gool` and `--mim` are real flags.
     */
    @Test
    fun everyProtocolTheScreenOffersIsStartedAsItself() {
        listOf("masque", "wg", "gool", "mim").forEach { id ->
            val cfg = AetherConfig.DEFAULT.copy(transport = id)
            assertNull("transport '$id' must validate", cfg.validate())
            assertEquals(
                "'$id' must be handed to the engine as its own flag",
                1,
                cfg.toAetherArgs("/data/user/0/com.manjaniq/files")
                    .count { it == "--$id" }
            )
        }
        // An id nothing knows is still refused rather than run as something else.
        val message = AetherConfig.DEFAULT.copy(transport = "quic").validate()
        assertNotNull("an unknown transport must not validate", message)
        assertTrue("says which field is wrong: $message", message!!.contains("transport"))
    }

    @Test
    fun unknownChoiceValuesAreRejected() {
        assertNotNull(AetherConfig.DEFAULT.copy(transport = "quic").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(scanMode = "yolo").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(noizeProfile = "stealth").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(ipVersion = "v5").validate())
    }

    @Test
    fun blankNoizeAndIpVersionStillMeanAetherPicksItsOwnDefault() {
        // These two were validated strictly for a while, which rejected configs
        // written before the fields had a value. Empty means "not set".
        assertNull(AetherConfig.DEFAULT.copy(noizeProfile = "").validate())
        assertNull(AetherConfig.DEFAULT.copy(ipVersion = "").validate())
    }

    @Test
    fun bindAddressesStayLoopbackAndDistinct() {
        assertNotNull(AetherConfig.DEFAULT.copy(socksBind = "0.0.0.0:1819").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:80").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:port").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(httpProxyBind = "10.0.0.1:1820").validate())
        // Both listeners on one port cannot bind; the service-side guard and the
        // UI-side guard must agree.
        assertNotNull(
            AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:1819", httpProxyBind = "127.0.0.1:1819").validate()
        )
        assertNull(AetherConfig.DEFAULT.copy(socksBind = "localhost:1819").validate())
    }

    @Test
    fun portConflictWithRstaIsDetectedIncludingSelfClash() {
        val rsta = RSTAConfig.DEFAULT
        assertNull(detectAetherPortConflict(AetherConfig.DEFAULT, rsta))
        assertNotNull(
            detectAetherPortConflict(AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:${rsta.listenPort}"), rsta)
        )
        assertNotNull(
            detectAetherPortConflict(
                AetherConfig.DEFAULT.copy(httpProxyBind = "127.0.0.1:${rsta.listenPort}"),
                rsta
            )
        )
        assertNotNull(
            detectAetherPortConflict(
                AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:1819", httpProxyBind = "127.0.0.1:1819"),
                rsta
            )
        )
    }

    @Test
    fun fragmentRangesFollowAetherParsing() {
        assertEquals(16L to 32L, AetherFragmenting.parseRange("16-32"))
        assertEquals(8L to 8L, AetherFragmenting.parseRange("8"))
        // fragment::parse_range swaps a reversed range instead of rejecting it.
        assertEquals(2L to 10L, AetherFragmenting.parseRange("10-2"))
        assertEquals(4L to 9L, AetherFragmenting.parseRange(" 4 - 9 "))
        assertNull(AetherFragmenting.parseRange(""))
        assertNull(AetherFragmenting.parseRange("abc"))
        assertNull(AetherFragmenting.parseRange("1-2-3"))
        assertNull(AetherFragmenting.parseRange("-5"))

        // A blank spec means "leave Aether's own default alone".
        assertNull(AetherFragmenting.validateRange("", 1500L))
        assertNull(AetherFragmenting.validateRange("16-32", 1500L))
        assertNotNull(AetherFragmenting.validateRange("16-x", 1500L))
        assertNotNull(AetherFragmenting.validateRange("0-4000", 1500L))
        assertNotNull(AetherFragmenting.validateRange("2-90000", 5000L))
    }

    @Test
    fun fragmentDefaultsAreTheDocumentedAetherDefaults() {
        // Docs/GUIDE.en.md documents size 16-32 and delay 2-10, off by default.
        // (aether/src/cli.rs help text claims "default on" — the conflict is
        // recorded in AETHER_CAPABILITIES.md rather than guessed at here.)
        assertEquals("16-32", AetherFragmenting.DEFAULT_SIZE)
        assertEquals("2-10", AetherFragmenting.DEFAULT_DELAY)
        assertFalse(AetherFragmenting.DEFAULT_ENABLED)
        assertFalse(AetherFragmenting.DEFAULT_SNI_SPLIT)
        assertEquals(AetherFragmenting.DEFAULT_SIZE, AetherConfig.DEFAULT.fragmentSize)
        assertEquals(AetherFragmenting.DEFAULT_DELAY, AetherConfig.DEFAULT.fragmentDelay)
        assertEquals(AetherFragmenting.DEFAULT_ENABLED, AetherConfig.DEFAULT.fragmentEnabled)
        assertTrue(AetherFragmenting.MAX_SIZE_BYTES in 1..1500)
        assertTrue(AetherFragmenting.MAX_DELAY_MS in 1..60_000)
    }

    @Test
    fun configRejectsUnusableFragmentValues() {
        assertNull(AetherConfig.DEFAULT.validate())
        assertNull(AetherConfig.DEFAULT.copy(fragmentSize = "", fragmentDelay = "").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(fragmentSize = "big").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(fragmentSize = "1-9000").validate())
        assertNotNull(AetherConfig.DEFAULT.copy(fragmentDelay = "1-900000").validate())
    }

    @Test
    fun argsEmitAnExplicitNoizeOffInsteadOfAethersOwnDefault() {
        val off = AetherConfig.DEFAULT.copy(noizeProfile = "off").toAetherArgs("/data/files")
        assertTrue("expected --noize in $off", off.contains("--noize"))
        assertEquals("off", off[off.indexOf("--noize") + 1])

        // Any other profile is passed through verbatim.
        val gfw = AetherConfig.DEFAULT.copy(noizeProfile = "gfw").toAetherArgs("/data/files")
        assertEquals("gfw", gfw[gfw.indexOf("--noize") + 1])

        // Unset means "no flag", i.e. Aether's own default (firewall/balanced).
        val unset = AetherConfig.DEFAULT.copy(noizeProfile = "").toAetherArgs("/data/files")
        assertFalse(unset.contains("--noize"))
    }

    @Test
    fun argsMapFragmentingOntoTheRealCliFlags() {
        val disabled = AetherConfig.DEFAULT.copy(fragmentEnabled = false).toAetherArgs("/data/files")
        assertTrue(disabled.contains("--no-fragment"))
        assertFalse(disabled.contains("--fragment-size"))
        assertFalse(disabled.contains("--fragment-delay"))

        val enabled = AetherConfig.DEFAULT.copy(
            fragmentEnabled = true,
            fragmentSize = "20-40",
            fragmentDelay = "1-5"
        ).toAetherArgs("/data/files")
        assertTrue(enabled.contains("--fragment"))
        assertFalse(enabled.contains("--no-fragment"))
        assertEquals("20-40", enabled[enabled.indexOf("--fragment-size") + 1])
        assertEquals("1-5", enabled[enabled.indexOf("--fragment-delay") + 1])

        // Blank specs must not emit a flag with an empty value.
        val blanks = AetherConfig.DEFAULT.copy(
            fragmentEnabled = true,
            fragmentSize = "",
            fragmentDelay = ""
        ).toAetherArgs("/data/files")
        assertFalse(blanks.contains("--fragment-size"))
        assertFalse(blanks.contains("--fragment-delay"))
    }

    @Test
    fun argsNeverInventASniSplitFlag() {
        // AETHER_MASQUE_H2_FRAGMENT_SNI has no CLI flag upstream; emitting one
        // would make aether_core_start fail on an unknown argument.
        listOf(true, false).forEach { sni ->
            val args = AetherConfig.DEFAULT.copy(
                fragmentEnabled = true,
                fragmentSniSplit = sni
            ).toAetherArgs("/data/files")
            assertFalse("no --fragment-sni exists in aether/src/cli.rs: $args", args.contains("--fragment-sni"))
            assertFalse(args.any { it.contains("sni", ignoreCase = true) && it.startsWith("--") })
        }
    }

    @Test
    fun argsCarryTheRestOfTheConfiguration() {
        val args = AetherConfig.DEFAULT.copy(
            transport = "wg",
            scanMode = "thorough",
            ipVersion = "both",
            peer = "1.2.3.4:2408",
            upstream = "socks5://127.0.0.1:1080",
            echEnabled = true,
            dns = "9.9.9.9",
            quickReconnect = false,
            socksBind = "127.0.0.1:1819",
            httpProxyBind = "127.0.0.1:1820",
            identityPath = "aether_identity"
        ).toAetherArgs("/data/files")

        assertTrue(args.contains("--wg"))
        assertEquals("thorough", args[args.indexOf("--scan") + 1])
        assertTrue(args.contains("--dual"))
        assertEquals("1.2.3.4:2408", args[args.indexOf("--peer") + 1])
        assertEquals("socks5://127.0.0.1:1080", args[args.indexOf("--upstream") + 1])
        assertEquals("auto", args[args.indexOf("--ech") + 1])
        assertEquals("9.9.9.9", args[args.indexOf("--dns") + 1])
        assertTrue(args.contains("--no-quick-reconnect"))
        assertEquals("127.0.0.1:1819", args[args.indexOf("--bind") + 1])
        assertEquals("127.0.0.1:1820", args[args.indexOf("--http-proxy") + 1])
        assertEquals("/data/files/aether_identity", args[args.indexOf("--config") + 1])

        // Protocol is mutually exclusive: exactly one of the four flags.
        assertEquals(1, args.count { it in setOf("--masque", "--wg", "--gool", "--mim") })
    }
}
