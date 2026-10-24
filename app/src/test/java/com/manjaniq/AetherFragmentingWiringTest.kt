package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherFragmenting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Fragmenting, from the stored route down to what Aether is told.
 *
 * `aether_tunnel_start`'s payload (`ffi.rs:246-260`) has no fragmenting field, and
 * that is not how the values travel: Aether reads them from its environment
 * (`fragment.rs:32-61`) together with the carrier they need
 * (`masque_h2::enabled()` → `AETHER_MASQUE_HTTP2`, `masque_h2.rs:141-149`), which
 * `AetherService` has `AetherBridge.fragmentingApply` write before the scan starts.
 * `toAetherArgs()` carries the same decision as CLI flags (`--h2` / `--h3`,
 * `--fragment` / `--no-fragment` / `--fragment-size` / `--fragment-delay`,
 * `cli.rs:579-580, 602-605`) for the `aether_core_start` path.
 *
 * Both serialisations are pinned here, along with the rule that keeps them honest:
 * `fragment.rs` has exactly one consumer, `masque_h2.rs`, so fragmenting and the
 * HTTP/2 carrier travel together and no other transport may claim them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherFragmentingWiringTest {

    private val masque = AetherConfig.DEFAULT.copy(transport = "masque")
    private val filesDir = "/data/user/0/com.manjaniq/files"

    // ---- the argument list for the CLI/core path ----

    @Test
    fun enablingFragmentingSelectsTheCarrierThatCarriesTheFragments() {
        val args = masque.copy(fragmentEnabled = true).toAetherArgs(filesDir)
        assertTrue("the carrier must be selected too", args.contains("--h2"))
        assertTrue(args.contains("--fragment"))
        assertFalse(args.contains("--h3"))
        assertFalse(args.contains("--no-fragment"))
    }

    @Test
    fun disablingFragmentingSelectsQuicAndSaysNoFragments() {
        assertFalse("the stored default is off", AetherFragmenting.DEFAULT_ENABLED)
        val args = masque.toAetherArgs(filesDir)
        assertTrue(args.contains("--h3"))
        assertTrue(args.contains("--no-fragment"))
        assertFalse(args.contains("--h2"))
        assertFalse(args.contains("--fragment"))
    }

    @Test
    fun theCarrierFlagIsAlwaysEmittedForMasqueSoTheCliNeverPrompts() {
        // select_masque_transport asks interactively when AETHER_MASQUE_HTTP2 is unset
        // (lib.rs:3255-3268), and a service has no way to answer a prompt.
        listOf(true, false).forEach { enabled ->
            val args = masque.copy(fragmentEnabled = enabled).toAetherArgs(filesDir)
            assertTrue(
                "fragmentEnabled=$enabled must still name a carrier",
                args.contains("--h2") || args.contains("--h3")
            )
        }
    }

    @Test
    fun chunkSizeAndDelayTravelAsTheirOwnFlags() {
        val args = masque.copy(
            fragmentEnabled = true,
            fragmentSize = "24-48",
            fragmentDelay = "3-9"
        ).toAetherArgs(filesDir)
        assertEquals("24-48", args[args.indexOf("--fragment-size") + 1])
        assertEquals("3-9", args[args.indexOf("--fragment-delay") + 1])
    }

    @Test
    fun aBlankSizeOrDelayLeavesAethersOwnDefaultAlone() {
        val args = masque.copy(
            fragmentEnabled = true,
            fragmentSize = "",
            fragmentDelay = "   "
        ).toAetherArgs(filesDir)
        assertFalse(args.contains("--fragment-size"))
        assertFalse(args.contains("--fragment-delay"))
        assertTrue("fragmenting itself is still asked for", args.contains("--fragment"))
    }

    @Test
    fun wireGuardGetsNoCarrierFlagBecauseItHasNoHttp2Carrier() {
        val args = AetherConfig.DEFAULT.copy(transport = "wg").toAetherArgs(filesDir)
        assertFalse(args.contains("--h2"))
        assertFalse(args.contains("--h3"))
    }

    // ---- the pairing the engine requires ----

    @Test
    fun theCarrierFollowsFragmentingOnEveryProtocolThatHasOne() {
        assertTrue(masque.copy(fragmentEnabled = true).usesHttp2Carrier())
        assertFalse(masque.copy(fragmentEnabled = false).usesHttp2Carrier())
        // gool and mim run over the MASQUE carrier too, so fragments have somewhere
        // to go there: `--h2` on `--mim` is HTTP/2 in HTTP/2 (cli.rs:577).
        listOf("gool", "mim").forEach { id ->
            val args = AetherConfig.DEFAULT.copy(transport = id, fragmentEnabled = true)
                .toAetherArgs(filesDir)
            assertTrue("$id must select the carrier it fragments on: $args", args.contains("--h2"))
            assertFalse("$id must not also ask for QUIC", args.contains("--h3"))
        }
        assertFalse(
            "WireGuard has no HTTP/2 carrier to fragment on",
            AetherConfig.DEFAULT.copy(transport = "wg", fragmentEnabled = true).usesHttp2Carrier()
        )
    }

    // ---- validation and edge cases ----

    /**
     * Fragmenting is a setting, not a lock: it is stored with the route, exported
     * with it, and put on the command line whatever the transport is. Only WireGuard
     * has nowhere to put the fragments, and there the flags still travel - the engine
     * ignores values whose carrier never runs, and the screen says which carrier they
     * belong to instead of disabling the control.
     */
    @Test
    fun fragmentingIsStoredAndSentOnEveryTransport() {
        listOf("masque", "wg", "gool", "mim").forEach { id ->
            val cfg = AetherConfig.DEFAULT.copy(
                transport = id,
                fragmentEnabled = true,
                fragmentSize = "24-48"
            )
            assertNull("$id must validate", cfg.validate())
            val args = cfg.toAetherArgs(filesDir)
            assertTrue("$id must carry the fragmenting flag: $args", args.contains("--fragment"))
            assertEquals("24-48", args[args.indexOf("--fragment-size") + 1])
        }
        // Turning it off is explicit rather than "leave the environment alone".
        assertTrue(
            AetherConfig.DEFAULT.copy(transport = "wg").toAetherArgs(filesDir)
                .contains("--no-fragment")
        )
    }

    @Test
    fun unusableChunkSpecsAreRefusedRatherThanSentToAether() {
        // fragment::parse_range (fragment.rs:93-113) quietly falls back to a default
        // for anything it cannot parse, so an unusable spec has to be caught here
        // instead of becoming a value nobody chose.
        listOf("abc", "1-2-3", "16..32", "-5", "1-99999", "99999", "16-").forEach { spec ->
            val problem = masque.copy(fragmentEnabled = true, fragmentSize = spec).validate()
            assertNotNull("size '$spec' must be refused", problem)
            assertTrue("size '$spec': $problem", problem!!.startsWith("Fragment size:"))
        }
        listOf("abc", "1-2-3", "-5", "1-99999", "2-").forEach { spec ->
            val problem = masque.copy(fragmentEnabled = true, fragmentDelay = spec).validate()
            assertNotNull("delay '$spec' must be refused", problem)
            assertTrue("delay '$spec': $problem", problem!!.startsWith("Fragment delay:"))
        }
    }

    @Test
    fun usableChunkSpecsAreAccepted() {
        // A blank spec means "Aether's own default"; a reversed range is swapped by
        // fragment.rs, so it is accepted here too rather than rejected as a typo.
        listOf("", "   ", "16", "16-32", "32-16", "1-1500").forEach { spec ->
            assertNull("size '$spec' must be accepted", masque.copy(fragmentSize = spec).validate())
        }
        listOf("", "2", "2-10", "0-5000").forEach { spec ->
            assertNull("delay '$spec' must be accepted", masque.copy(fragmentDelay = spec).validate())
        }
    }

    @Test
    fun theUiLimitsMatchWhatTheBridgeAccepts() {
        // AetherFragmenting.MAX_* are the bounds the screen validates against, and
        // aether-wrapper/src/lib.rs refuses anything above the same two numbers, so a
        // value the UI accepts can never be rejected on the way into the engine.
        assertEquals(1500, AetherFragmenting.MAX_SIZE_BYTES)
        assertEquals(5_000, AetherFragmenting.MAX_DELAY_MS)
        assertNull(
            AetherFragmenting.validateRange(
                "${AetherFragmenting.MAX_SIZE_BYTES}",
                AetherFragmenting.MAX_SIZE_BYTES.toLong()
            )
        )
        assertNotNull(
            AetherFragmenting.validateRange(
                "${AetherFragmenting.MAX_SIZE_BYTES + 1}",
                AetherFragmenting.MAX_SIZE_BYTES.toLong()
            )
        )
    }

    @Test
    fun theSniSplitIsPartOfTheConfigurationThatTravels() {
        // Upstream has no --fragment-sni flag (fragment.rs:49 reads the variable and
        // cli.rs sets nothing), so the SNI split can only reach Aether through the
        // bridge; the argument list must not pretend otherwise.
        val args = masque.copy(fragmentEnabled = true, fragmentSniSplit = true).toAetherArgs(filesDir)
        assertFalse(args.any { it.contains("sni") })
        val roundTrip = AetherConfig.fromJson(
            masque.copy(fragmentSniSplit = true).toJson()
        )
        assertNotNull("the stored route must parse back", roundTrip)
        assertTrue(roundTrip!!.fragmentSniSplit)
    }

    // ---- the bridge must not pretend ----

    @Test
    fun withoutTheNativeLibraryTheBridgeReportsFailureNotSuccess() {
        // On the host JVM libaether.so is absent, which is the state a build without
        // the engine is in. Applying fragmenting must then fail loudly, because
        // AetherService refuses to start a tunnel it cannot fragment as asked rather
        // than quietly running an unfragmented one.
        assumeFalse("the native library is present, so this says nothing", AetherBridge.isNativeAvailable)
        val applied = AetherBridge.fragmentingApply(
            enabled = true,
            size = "16-32",
            delay = "2-10",
            sniSplit = true
        )
        assertTrue(applied.isFailure)
        assertNotNull(applied.exceptionOrNull()?.message)
    }
}
