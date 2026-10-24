package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherHelpers
import com.manjaniq.core.models.AetherCapabilities
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherEngineOptions
import com.manjaniq.core.models.AetherHelperPaths
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig
import com.manjaniq.core.models.AetherPsiphonMode
import com.manjaniq.core.models.AetherPsiphonOptions
import com.manjaniq.core.models.AetherTorBridges
import com.manjaniq.core.models.AetherTorMode
import com.manjaniq.core.models.AetherTorOptions
import com.manjaniq.core.models.AetherTransportChoice
import com.manjaniq.core.models.getSectionsForProfile
import com.manjaniq.core.models.profileSupportsFragmenting
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * The app starts Aether one way: `aether_core_start` over the core's own command
 * line, and nothing else.
 *
 * Aether also exposes granular `identity_open` / `scan_start` / `tunnel_start`
 * calls (`third_party/Aether/aether/src/ffi.rs`), but their payload structs carry
 * only a handful of fields, so those settings could never be applied. They are
 * gone from this app - bindings, service path and UI - and these tests pin the
 * consequences: no mode is stored or selectable, every protocol validates, and
 * every configured feature reaches the argument list the engine is handed.
 *
 * Robolectric, because the configuration is stored as JSON (`org.json` is a stub on
 * the bare host JVM) and because the helper lookups read real files.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherSingleEngineTest {

    private val filesDir = "/data/user/0/com.manjaniq/files"

    // ------------------------------------------------- there is one start way ---

    @Test
    fun aConfigurationNoLongerCarriesAStartMode() {
        val json = JSONObject(AetherConfig.DEFAULT.toJson())
        assertFalse("the stored configuration must not name a start path", json.has("startMode"))
        val parsed = AetherConfig.fromJson("""{"startMode":"granular","transport":"gool"}""")
        assertNotNull("a hand-written import still loads", parsed)
        assertEquals("gool", parsed!!.transport)
    }

    @Test
    fun anOlderConfigurationStillLoadsAndIsStartedTheSameWay() {
        // A configuration written by a build that had two paths names one of them.
        // The value is ignored, not obeyed: there is nothing left to obey it with.
        val legacy = JSONObject(AetherConfig.DEFAULT.toJson()).apply {
            put("startMode", "full")
        }
        val restored = AetherConfig.fromJson(legacy.toString())
        assertNotNull(restored)
        assertNull(restored!!.validate())
        assertEquals(AetherConfig.DEFAULT.transport, restored.transport)
        assertTrue(
            "the argument list is built from the configuration, not from a mode",
            restored.toAetherArgs(filesDir).contains("--masque")
        )
    }

    // ------------------------------------------------------- every protocol ---

    @Test
    fun allFourProtocolsAreRealCliProtocolsAndAllFourAreAccepted() {
        assertEquals(listOf("masque", "wg", "gool", "mim"), AetherTransportChoice.IDS)
        AetherTransportChoice.ALL.forEach { choice ->
            assertNull(
                "${choice.id} must validate: cli.rs turns it into AETHER_PROTOCOL",
                AetherConfig.DEFAULT.copy(transport = choice.id).validate()
            )
            val args = AetherConfig.DEFAULT.copy(transport = choice.id).toAetherArgs(filesDir)
            assertTrue(args.toString(), args.contains("--${choice.id}"))
            // Exactly one protocol flag, so the CLI cannot be handed two.
            assertEquals(
                1,
                args.count { it == "--masque" || it == "--wg" || it == "--gool" || it == "--mim" }
            )
        }
        assertNotNull(
            "an unknown id is still refused",
            AetherConfig.DEFAULT.copy(transport = "quic").validate()
        )
    }

    // --------------------------------------------- everything is handed over ---

    @Test
    fun everyAdvancedFeatureReachesTheArgumentList() {
        val configured = AetherConfig.DEFAULT.copy(
            transport = "gool",
            upstream = "socks5://127.0.0.1:1080",
            dns = "9.9.9.9",
            quickReconnect = false,
            teamName = "example-team",
            engine = AetherEngineOptions(
                tor = AetherTorOptions(
                    mode = AetherTorMode.CHAIN,
                    pt = listOf("obfs4=/data/app/lib/arm64/liblyrebird.so")
                ),
                psiphon = AetherPsiphonOptions(
                    mode = AetherPsiphonMode.ONLY,
                    bin = "/data/app/lib/arm64/libpsiphon_tunnel_core.so"
                ),
                timing = com.manjaniq.core.models.AetherTimingOptions(mark = "255"),
                stats = com.manjaniq.core.models.AetherStatsOptions(enabled = true),
                routing = com.manjaniq.core.models.AetherRoutingOptions(
                    routesFile = "/data/user/0/com.manjaniq/files/aether/routes.txt",
                    block = "keyword:doubleclick"
                ),
                gateway = com.manjaniq.core.models.AetherGatewayOptions(enabled = true)
            )
        )
        assertNull("nothing about this route is refused any more", configured.validate())
        val args = configured.toAetherArgs(filesDir)

        fun valueOf(flag: String) = args.getOrNull(args.indexOf(flag) + 1)
        listOf(
            "--gool", "--tor", "--psiphon-only", "--gateway", "--team", "--stats", "--mark",
            "--routes", "--route-block", "--dns", "--upstream", "--no-quick-reconnect"
        ).forEach { flag ->
            assertTrue("$flag must reach aether_core_start: $args", args.contains(flag))
        }
        assertEquals("socks5://127.0.0.1:1080", valueOf("--upstream"))
        assertEquals("9.9.9.9", valueOf("--dns"))
        assertEquals("255", valueOf("--mark"))
        assertEquals("keyword:doubleclick", valueOf("--route-block"))
        assertEquals("example-team", valueOf("--team"))
    }

    /**
     * The route stores a compact Tor/Psiphon form next to the full engine options.
     * They are the same `AETHER_*` variables, so they have to travel with the
     * command line - a value on that screen that never reached the engine would be
     * exactly the silent mismatch this build removes.
     */
    @Test
    fun theRoutesOwnTorAndPsiphonValuesReachTheCommandLineToo() {
        val route = AetherProfileConfig(
            profileId = AetherProfile.TOR_PSIPHON.id,
            base = AetherConfig.DEFAULT.copy(transport = "masque"),
            psiphonEnabled = true,
            psiphonEndpoint = "127.0.0.1:1821",
            psiphonRegion = "NL",
            torSocks = "127.0.0.1:9050",
            torBridges = "obfs4 192.0.2.55:38114 cert=abc\nsnowflake 192.0.2.9:443",
            torUseBridges = true
        )
        val args = route.toAetherArgs(filesDir)
        assertTrue(args.toString(), args.contains("--psiphon"))
        assertEquals("NL", args[args.indexOf("--psiphon-region") + 1])
        assertEquals("127.0.0.1:1821", args[args.indexOf("--psiphon-bind") + 1])
        assertEquals("127.0.0.1:9050", args[args.indexOf("--tor-bind") + 1])
        assertEquals("both bridge lines are named", 2, args.count { it == "--tor-bridge" })
        // One value owns AETHER_TOR_BRIDGES (`tor.rs:121-150`), so the named lines
        // are never combined with the automatic fetch.
        assertFalse(args.contains("--tor-bridges"))
    }

    /**
     * Every flag those shortcut values turn into is one the core accepts. Read from
     * `cli.rs` here, because `parse_args` aborts the whole run on an unknown option
     * (`cli.rs:639-644`): a typo would look like a start that fails for no reason.
     */
    @Test
    fun theRouteShortcutFlagsExistInTheCoreCli() {
        val source = listOf(
            "../third_party/Aether/aether/src/cli.rs",
            "third_party/Aether/aether/src/cli.rs"
        ).map { File(it) }.firstOrNull { it.isFile }
        assumeTrue("the vendored CLI source is not present", source != null)
        val accepted = source!!.readLines()
            .filter { it.contains("=>") }
            .flatMap { line ->
                Regex("\"(-{1,2}[a-z0-9-]+)\"").findAll(line.substringBefore("=>"))
                    .map { it.groupValues[1] }.toList()
            }
            .toSet()
        listOf(
            "--psiphon", "--psiphon-region", "--psiphon-bind",
            "--tor-bind", "--tor-bridge", "--tor-bridges"
        ).forEach { flag ->
            assertTrue("cli.rs does not accept '$flag'", accepted.contains(flag))
        }
    }

    /** The detailed editor is the one that wins when both name the same flag. */
    @Test
    fun theDetailedEngineOptionsWinOverTheRoutesShortcutValues() {
        val route = AetherProfileConfig(
            profileId = AetherProfile.AETHER_PSIPHON.id,
            base = AetherConfig.DEFAULT.copy(
                engine = AetherEngineOptions(
                    psiphon = AetherPsiphonOptions(
                        mode = AetherPsiphonMode.ONLY,
                        region = "DE",
                        bind = "127.0.0.1:1900"
                    ),
                    tor = AetherTorOptions(mode = AetherTorMode.CHAIN, bridges = AetherTorBridges.AUTO)
                )
            ),
            psiphonEnabled = true,
            psiphonRegion = "NL",
            psiphonEndpoint = "127.0.0.1:1821",
            torSocks = "127.0.0.1:9050",
            torUseBridges = true
        )
        val args = route.toAetherArgs(filesDir)
        assertEquals(1, args.count { it == "--psiphon-only" })
        assertFalse("one Psiphon mode only", args.contains("--psiphon"))
        assertEquals("DE", args[args.indexOf("--psiphon-region") + 1])
        assertEquals("127.0.0.1:1900", args[args.indexOf("--psiphon-bind") + 1])
        assertEquals("127.0.0.1:9050", args[args.indexOf("--tor-bind") + 1])
        assertEquals(1, args.count { it == "--tor-bridges" })
    }

    /**
     * The route's own team field is the compact form of `--team`, and the detailed
     * editor's is the same variable. Whichever is set reaches the engine, and the
     * detailed one decides when both are - never both flags, which would make the
     * last one win by accident.
     */
    @Test
    fun theTeamFieldReachesTheTeamFlagExactlyOnce() {
        val fromRoute = AetherConfig.DEFAULT.copy(teamName = "route-team").toAetherArgs(filesDir)
        assertEquals("route-team", fromRoute[fromRoute.indexOf("--team") + 1])
        assertEquals(1, fromRoute.count { it == "--team" })

        val both = AetherConfig.DEFAULT.copy(
            teamName = "route-team",
            engine = AetherEngineOptions(api = com.manjaniq.core.models.AetherApiOptions(team = "editor-team"))
        ).toAetherArgs(filesDir)
        assertEquals(
            "the detailed editor owns the flag when both name a team",
            "editor-team",
            both[both.indexOf("--team") + 1]
        )
        assertEquals(1, both.count { it == "--team" })
    }

    @Test
    fun theScanAndCarrierChoicesAreCarriedToo() {
        val args = AetherConfig.DEFAULT
            .copy(scanMode = "ironclad", ipVersion = "both", noizeProfile = "gfw")
            .toAetherArgs(filesDir)
        assertEquals("ironclad", args[args.indexOf("--scan") + 1])
        assertTrue(args.contains("--dual"))
        assertEquals("gfw", args[args.indexOf("--noize") + 1])
        // A MASQUE start always names its carrier: `select_masque_transport` asks
        // interactively when AETHER_MASQUE_HTTP2 is unset (lib.rs:3255-3268), and a
        // service cannot answer a prompt.
        assertTrue(args.contains("--h2") || args.contains("--h3"))
    }

    // ------------------------------------------------------------- helpers ---

    private val helpers = AetherHelperPaths(
        psiphonBinary = "/data/app/lib/arm64/libpsiphon_tunnel_core.so",
        ptBinary = "/data/app/lib/arm64/liblyrebird.so",
        ptDirectory = "/data/app/lib/arm64",
        torDirectory = "/data/user/0/com.manjaniq/files/aether/tor",
        psiphonDirectory = "/data/user/0/com.manjaniq/files/aether/psiphon"
    )

    @Test
    fun thePackagedHelpersAreNamedOnTheCommandLine() {
        val configured = AetherConfig.DEFAULT.copy(
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.CHAIN),
                psiphon = AetherPsiphonOptions(mode = AetherPsiphonMode.ONLY)
            )
        )
        val args = configured.toAetherArgs(filesDir, helpers)
        assertTrue(args.contains("--tor"))
        assertTrue(args.contains("--psiphon-only"))
        assertEquals(helpers.psiphonBinary, args[args.indexOf("--psiphon-bin") + 1])
        assertEquals(helpers.psiphonDirectory, args[args.indexOf("--psiphon-dir") + 1])
        assertEquals(helpers.torDirectory, args[args.indexOf("--tor-dir") + 1])
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
        val configured = AetherConfig.DEFAULT.copy(
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.ONLY, pt = listOf("snowflake=/opt/snowflake")),
                psiphon = AetherPsiphonOptions(mode = AetherPsiphonMode.CHAIN, bin = "/opt/psiphon")
            )
        )
        val args = configured.toAetherArgs(filesDir, helpers)
        assertEquals("/opt/psiphon", args[args.indexOf("--psiphon-bin") + 1])
        // The transport is named in the value itself (`<name>=<path>`), which is
        // the shape bridges.rs parses; the path alone would be a bare binary.
        assertEquals("snowflake=/opt/snowflake", args[args.indexOf("--tor-pt") + 1])
        assertFalse(args.contains("obfs4=${helpers.ptBinary}"))
    }

    @Test
    fun aHelperOutsideTheLibraryDirectoryIsNeverAccepted() {
        val root = File(System.getProperty("java.io.tmpdir") ?: ".", "aether-helpers-${System.nanoTime()}")
        val library = File(root, "lib").apply { mkdirs() }
        val inside = File(library, "liblyrebird.so").apply { writeText("#\n") }
        val outside = File(root, "liblyrebird.so").apply { writeText("#\n") }
        val madeExecutable = listOf(inside, outside).all { it.setExecutable(true) }
        try {
            assertFalse(
                "a missing file is not executable",
                AetherHelpers.isExecutableLibraryFile(File(library, "absent.so"), library.absolutePath)
            )
            assertFalse(
                "a file outside the library directory is refused even when executable",
                AetherHelpers.isExecutableLibraryFile(outside, library.absolutePath)
            )
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

    // ------------------------------------------------- the settings inventory ---

    @Test
    fun everySettingARouteOffersCanBeRendered() {
        AetherProfile.values().forEach { profile ->
            val sections = getSectionsForProfile(profile)
            sections.flatMap { it.keys }.forEach { key ->
                assertTrue(
                    "$profile offers unknown setting '$key'",
                    AetherCapabilities.knows(key)
                )
            }
            val keys = sections.flatMap { it.keys }
            assertEquals("$profile lists a setting twice: $keys", keys.size, keys.distinct().size)
        }
    }

    /**
     * A route may only name a setting the screen can draw: `getSectionsForProfile`
     * refuses an unknown key outright, and this pins the other direction - every key
     * a route names really is in the inventory - so the two lists cannot drift.
     */
    @Test
    fun everyKeyARouteCanNameIsDrawn() {
        AetherProfile.values().forEach { profile ->
            getSectionsForProfile(profile).flatMap { it.keys }.forEach { key ->
                assertTrue("$profile names '$key'", AetherCapabilities.knows(key))
            }
        }
        assertFalse("a typo is refused", AetherCapabilities.knows("fragmening"))
    }

    @Test
    fun fragmentingIsOfferedExactlyWhereMasqueCanRun() {
        assertTrue(profileSupportsFragmenting(AetherProfile.WARP_DIRECT))
        assertTrue(profileSupportsFragmenting(AetherProfile.AETHER_PSIPHON))
        assertTrue(profileSupportsFragmenting(AetherProfile.AETHER_TOR))
        assertTrue(profileSupportsFragmenting(AetherProfile.TOR_AETHER))
        assertFalse(profileSupportsFragmenting(AetherProfile.TOR_DIRECT))
        assertFalse(profileSupportsFragmenting(AetherProfile.TOR_PSIPHON))
    }

    /**
     * The transport chips and the CLI's own protocol flags are one list, so a
     * protocol added in one place cannot be forgotten in the other.
     */
    @Test
    fun theProtocolListMatchesTheCli() {
        val cliProtocolFlags = listOf("--masque", "--wg", "--gool", "--mim")
        assertEquals(cliProtocolFlags, AetherTransportChoice.IDS.map { "--$it" })
        assertNull(AetherTransportChoice.byId("quic"))
    }
}
