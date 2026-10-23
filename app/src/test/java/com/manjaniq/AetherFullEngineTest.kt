package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherHelpers
import com.manjaniq.core.models.AetherCapabilities
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherEngineOptions
import com.manjaniq.core.models.AetherHelperPaths
import com.manjaniq.core.models.AetherPsiphonMode
import com.manjaniq.core.models.AetherPsiphonOptions
import com.manjaniq.core.models.AetherReach
import com.manjaniq.core.models.AetherStartMode
import com.manjaniq.core.models.AetherTorMode
import com.manjaniq.core.models.AetherTorOptions
import com.manjaniq.core.models.AetherTransportChoice
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * The full-engine start mode, pinned as executable documentation.
 *
 * Aether exposes two entry points in `third_party/Aether/aether/src/ffi.rs`: the
 * granular `identity_open` / `scan_start` / `tunnel_start` calls, whose payload
 * structs carry a fixed set of fields (`ffi.rs:215-260`), and
 * `aether_core_start` (`ffi.rs:492`), which runs `cli::parse_args` and therefore
 * the core's entire surface. Everything asserted here is about that difference:
 * which protocols each path may start, which settings only the command line can
 * apply, and the helper executables the command line has to be told about.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherFullEngineTest {

    private val full = AetherConfig.DEFAULT.copy(startMode = AetherStartMode.FULL_ENGINE.id)

    // ------------------------------------------------------------ the mode ---

    @Test
    fun theGranularPathIsTheDefaultAndStaysWhatItWas() {
        assertEquals(AetherStartMode.GRANULAR.id, AetherConfig.DEFAULT.startMode)
        assertFalse(AetherConfig.DEFAULT.fullEngine())
        assertTrue(full.fullEngine())
        assertEquals(listOf("granular", "full"), AetherStartMode.IDS)
        assertEquals(AetherStartMode.FULL_ENGINE, AetherStartMode.byId("full"))
        assertEquals(AetherStartMode.GRANULAR, AetherStartMode.byId("granular"))
        assertNull(AetherStartMode.byId("sideways"))
        // An unknown id is never the full engine: the safe reading of a value
        // nobody recognises is the path that reports every step.
        assertFalse(AetherStartMode.isFullEngine("sideways"))
    }

    @Test
    fun anUnknownStartModeIsRefused() {
        val message = AetherConfig.DEFAULT.copy(startMode = "sideways").validate()
        assertNotNull(message)
        assertTrue("names the accepted ids: $message", message!!.contains("granular"))
    }

    @Test
    fun withFullEngineSwitchesTheRouteAndIsIdempotent() {
        assertEquals(full.startMode, AetherConfig.DEFAULT.withFullEngine().startMode)
        assertEquals(full.startMode, full.withFullEngine().startMode)
        // Nothing else about the configuration changes.
        assertEquals(AetherConfig.DEFAULT.transport, AetherConfig.DEFAULT.withFullEngine().transport)
        assertEquals(AetherConfig.DEFAULT.engine, AetherConfig.DEFAULT.withFullEngine().engine)
    }

    // ------------------------------------------------------- the protocols ---

    @Test
    fun goolAndMimAreStartableOnlyThroughTheCommandLine() {
        // api::Transport has only Masque and WireGuard (api.rs:13-16) and its
        // parse() maps every other id to Masque, so on the granular path these
        // would run as plain MASQUE.
        listOf("gool", "mim").forEach { id ->
            val granular = AetherConfig.DEFAULT.copy(transport = id).validate()
            assertNotNull("$id must not validate on the granular path", granular)
            assertTrue("$granular names $id", granular!!.contains(id))
            assertTrue("$granular names the mode", granular.contains(AetherStartMode.FULL_ENGINE.id))

            assertNull(
                "$id must validate on the full-engine path",
                AetherConfig.DEFAULT.copy(transport = id, startMode = AetherStartMode.FULL_ENGINE.id).validate()
            )
            val args = AetherConfig.DEFAULT
                .copy(transport = id, startMode = AetherStartMode.FULL_ENGINE.id)
                .toAetherArgs("/data/user/0/com.manjaniq/files")
            assertTrue(args.toString(), args.contains("--$id"))
            // Exactly one protocol flag, so the CLI cannot be handed two.
            assertEquals(1, args.count { it == "--masque" || it == "--wg" || it == "--gool" || it == "--mim" })
        }
        // The selection rule follows the mode, and the unparameterised overload
        // keeps describing the granular path.
        AetherTransportChoice.ALL.forEach { choice ->
            assertTrue(choice.id, AetherTransportChoice.isSelectable(choice.id, true))
            assertEquals(choice.id, choice.selectable, AetherTransportChoice.isSelectable(choice.id))
        }
        assertFalse(AetherTransportChoice.isSelectable("quic", true))
    }

    // --------------------------------------------------------- what applies ---

    @Test
    fun theCommandLineOnlyFeaturesAreNamedOnTheGranularPath() {
        val configured = AetherConfig.DEFAULT.copy(
            upstream = "socks5://127.0.0.1:1080",
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.CHAIN),
                psiphon = AetherPsiphonOptions(mode = AetherPsiphonMode.ONLY),
                timing = com.manjaniq.core.models.AetherTimingOptions(mark = "255"),
                stats = com.manjaniq.core.models.AetherStatsOptions(enabled = true),
                routing = com.manjaniq.core.models.AetherRoutingOptions(block = "keyword:doubleclick"),
                gateway = com.manjaniq.core.models.AetherGatewayOptions(enabled = true)
            )
        )
        val named = configured.commandLineOnlyFeatures()
        listOf("Tor", "Psiphon", "upstream", "statistics", "routing", "mark", "gateway").forEach { needle ->
            assertTrue("the list must name $needle: $named", named.any { it.contains(needle) })
        }
        // The same configuration on the full-engine path drops nothing, so the
        // warning list is empty rather than being shown and ignored.
        assertTrue(configured.withFullEngine().commandLineOnlyFeatures().isEmpty())
        // The default configuration applies everything it asks for on either path.
        assertTrue(AetherConfig.DEFAULT.commandLineOnlyFeatures().isEmpty())
    }

    @Test
    fun theFullEngineReachTableIsAppliedExceptWhereAChildProcessIsNeeded() {
        listOf(
            "transport", "scanMode", "ipVersion", "socksBind", "httpProxyBind", "peer",
            "identityPath", "noizeProfile", "echEnabled", "teamName", "dns", "upstream",
            "quickReconnect", "fragmentEnabled", "fragmentSize", "fragmentDelay"
        ).forEach { key ->
            assertEquals(
                key,
                AetherReach.Applied,
                AetherCapabilities.reachOf(key, AetherStartMode.FULL_ENGINE)
            )
            assertTrue(key, AetherCapabilities.isForwarded(key, AetherStartMode.FULL_ENGINE))
        }
        // Partial: the SNI split has no CLI flag (the bridge writes the variable),
        // and the Psiphon/Tor bridge settings need a helper this build may not ship.
        listOf("fragmentSniSplit", "psiphonEnabled", "psiphonRegion", "torBridges", "torUseBridges")
            .forEach { key ->
                assertEquals(
                    key,
                    AetherReach.Partial,
                    AetherCapabilities.reachOf(key, AetherStartMode.FULL_ENGINE)
                )
            }
        // The granular table is untouched by all of the above.
        assertEquals(AetherReach.NotForwarded, AetherCapabilities.reachOf("dns"))
        assertEquals(
            AetherReach.NotForwarded,
            AetherCapabilities.reachOf("dns", AetherStartMode.GRANULAR)
        )
        // Every key has a reason on both paths.
        AetherCapabilities.ALL.forEach { info ->
            assertTrue(info.key, AetherCapabilities.reasonResFor(info.key, AetherStartMode.GRANULAR) != 0)
            assertTrue(info.key, AetherCapabilities.reasonResFor(info.key, AetherStartMode.FULL_ENGINE) != 0)
        }
    }

    // ----------------------------------------------------------- helpers ---

    private val helpers = AetherHelperPaths(
        psiphonBinary = "/data/app/lib/arm64/libpsiphon_tunnel_core.so",
        ptBinary = "/data/app/lib/arm64/liblyrebird.so",
        ptDirectory = "/data/app/lib/arm64",
        torDirectory = "/data/user/0/com.manjaniq/files/aether/tor",
        psiphonDirectory = "/data/user/0/com.manjaniq/files/aether/psiphon"
    )

    @Test
    fun thePackagedHelpersAreNamedOnTheCommandLine() {
        val configured = full.copy(
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.CHAIN),
                psiphon = AetherPsiphonOptions(mode = AetherPsiphonMode.ONLY)
            )
        )
        val args = configured.toAetherArgs("/data/user/0/com.manjaniq/files", helpers)
        assertTrue(args.contains("--tor"))
        assertTrue(args.contains("--psiphon-only"))
        assertEquals(
            helpers.psiphonBinary,
            args[args.indexOf("--psiphon-bin") + 1]
        )
        assertEquals(helpers.psiphonDirectory, args[args.indexOf("--psiphon-dir") + 1])
        assertEquals("/data/user/0/com.manjaniq/files/aether/tor", args[args.indexOf("--tor-dir") + 1])
        assertEquals(helpers.ptDirectory, args[args.indexOf("--tor-pt-dir") + 1])
        // One binary serves every transport Aether may ask for, and each is named
        // separately because bridges.rs resolves the transport by name.
        val pts = args.withIndex().filter { it.value == "--tor-pt" }.map { args[it.index + 1] }
        assertTrue(pts.toString(), pts.contains("obfs4=${helpers.ptBinary}"))
        assertTrue(pts.toString(), pts.contains("snowflake=${helpers.ptBinary}"))
        assertTrue(pts.toString(), pts.contains("webtunnel=${helpers.ptBinary}"))
    }

    @Test
    fun aPathTheUserNamedWinsOverThePackagedOne() {
        val configured = full.copy(
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.ONLY, pt = listOf("snowflake=/opt/snowflake")),
                psiphon = AetherPsiphonOptions(mode = AetherPsiphonMode.CHAIN, bin = "/opt/psiphon")
            )
        )
        val args = configured.toAetherArgs("/data/user/0/com.manjaniq/files", helpers)
        assertEquals("/opt/psiphon", args[args.indexOf("--psiphon-bin") + 1])
        // The transport is named in the value itself (`<name>=<path>`), which is the
        // shape bridges.rs parses; the path alone would be taken as a bare binary.
        assertEquals("snowflake=/opt/snowflake", args[args.indexOf("--tor-pt") + 1])
        // A user-named PT means the packaged one is not also listed.
        assertFalse(args.contains("obfs4=${helpers.ptBinary}"))
    }

    @Test
    fun theGranularPathNeverNamesAHelper() {
        val args = AetherConfig.DEFAULT
            .copy(engine = AetherEngineOptions(tor = AetherTorOptions(mode = AetherTorMode.CHAIN)))
            .toAetherArgs("/data/user/0/com.manjaniq/files", helpers)
        assertFalse(args.toString(), args.contains("--tor-pt"))
        assertFalse(args.toString(), args.contains("--psiphon-bin"))
        assertFalse(args.toString(), args.contains("--tor-dir"))
    }

    @Test
    fun aHelperOutsideTheLibraryDirectoryIsNeverAccepted() {
        val root = File(System.getProperty("java.io.tmpdir") ?: ".", "aether-helpers-${System.nanoTime()}")
        val library = File(root, "lib").apply { mkdirs() }
        val inside = File(library, "liblyrebird.so").apply { writeText("#\n") }
        val outside = File(root, "liblyrebird.so").apply { writeText("#\n") }
        val madeExecutable = listOf(inside, outside).all { it.setExecutable(true) }
        try {
            assertFalse("a missing file is not executable",
                AetherHelpers.isExecutableLibraryFile(File(library, "absent.so"), library.absolutePath))
            assertFalse("a file outside the library directory is refused even when executable",
                AetherHelpers.isExecutableLibraryFile(outside, library.absolutePath))
            if (madeExecutable) {
                assertTrue(AetherHelpers.isExecutableLibraryFile(inside, library.absolutePath))
            }
            // Nothing configured means nothing claimed: AetherHelpers never
            // guesses a path it has not seen.
            assertNull(AetherHelpers.psiphonBinary())
            assertNull(AetherHelpers.lyrebirdBinary())
            assertFalse(AetherHelpers.hasPsiphon())
            assertFalse(AetherHelpers.hasPluggableTransport())
        } finally {
            inside.delete()
            outside.delete()
            library.delete()
            root.delete()
        }
    }

    // --------------------------------------------------------- persistence ---

    @Test
    fun theStartModeSurvivesJsonAndOlderConfigsKeepTheGranularPath() {
        val round = AetherConfig.fromJson(full.toJson())
        assertNotNull(round)
        assertEquals(AetherStartMode.FULL_ENGINE.id, round!!.startMode)

        // A configuration written before the mode existed meant the granular
        // path, because that is the only one earlier builds had.
        val legacy = JSONObject(AetherConfig.DEFAULT.toJson()).apply { remove("startMode") }
        val restored = AetherConfig.fromJson(legacy.toString())
        assertNotNull(restored)
        assertEquals(AetherStartMode.GRANULAR.id, restored!!.startMode)
    }
}
