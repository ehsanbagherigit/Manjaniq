package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherHelpers
import com.manjaniq.core.models.AetherCapabilities
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherHelperPaths
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig
import com.manjaniq.core.models.AetherTransportChoice
import com.manjaniq.core.models.getSectionsForProfile
import com.manjaniq.core.models.profileUsesAetherGateway
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
 * The app starts Aether one way: `aether_core_start` over the core's own command
 * line, and nothing else.
 *
 * Aether also exposes granular `identity_open` / `scan_start` / `tunnel_start`
 * calls (`third_party/Aether/aether/src/ffi.rs`), but their payload structs carry
 * only a handful of fields, so those settings could never be applied. They are
 * gone from this app - bindings, service path and UI - together with the
 * "ENGINE OPTIONS" screen, so these tests pin what is left: no mode is stored or
 * selectable, every protocol validates, and every setting a route still offers
 * reaches the argument list the engine is handed.
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
        assertEquals(
            "the removed engine-options block must not be written either",
            setOf(
                "identityPath", "transport", "scanMode", "noizeProfile", "socksBind",
                "httpProxyBind", "peer", "upstream", "echEnabled", "dns", "ipVersion",
                "teamName", "quickReconnect", "fragmentEnabled", "fragmentSize",
                "fragmentDelay", "fragmentSniSplit"
            ),
            json.keys().asSequence().toSet()
        )
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
            put("engine", JSONObject().put("tor", JSONObject().put("mode", "chain")))
        }
        val restored = AetherConfig.fromJson(legacy.toString())
        assertNotNull(restored)
        assertNull(restored!!.validate())
        assertEquals(AetherConfig.DEFAULT.transport, restored.transport)
        val args = restored.toAetherArgs(filesDir)
        assertTrue(
            "the argument list is built from the configuration, not from a mode",
            args.contains("--masque")
        )
        assertFalse("the removed Tor block must not be resurrected", args.contains("--tor"))
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

    /**
     * Every control the Aether screen still draws has a flag behind it.
     *
     * The engine-options group is gone, so the list is short - and that is the
     * point: a value the screen offers and the command line does not carry would be
     * exactly the silent mismatch this build removes.
     */
    @Test
    fun everySettingTheScreenOffersReachesTheArgumentList() {
        val configured = AetherConfig.DEFAULT.copy(
            transport = "gool",
            scanMode = "ironclad",
            ipVersion = "both",
            peer = "198.51.100.7:2408",
            socksBind = "127.0.0.1:1919",
            httpProxyBind = "127.0.0.1:1920",
            upstream = "socks5://127.0.0.1:1080",
            dns = "9.9.9.9",
            noizeProfile = "gfw",
            echEnabled = true,
            quickReconnect = false,
            teamName = "example-team",
            fragmentEnabled = true,
            fragmentSize = "24-48",
            fragmentDelay = "3-9"
        )
        assertNull("nothing about this route is refused", configured.validate())
        val args = configured.toAetherArgs(filesDir)

        fun valueOf(flag: String) = args.getOrNull(args.indexOf(flag) + 1)
        listOf(
            "--gool", "--scan", "--dual", "--peer", "--bind", "--http-proxy", "--upstream",
            "--dns", "--noize", "--ech", "--team", "--no-quick-reconnect", "--fragment",
            "--fragment-size", "--fragment-delay", "--config", "--h2"
        ).forEach { flag ->
            assertTrue("$flag must reach aether_core_start: $args", args.contains(flag))
        }
        assertEquals("ironclad", valueOf("--scan"))
        assertEquals("198.51.100.7:2408", valueOf("--peer"))
        assertEquals("127.0.0.1:1919", valueOf("--bind"))
        assertEquals("127.0.0.1:1920", valueOf("--http-proxy"))
        assertEquals("socks5://127.0.0.1:1080", valueOf("--upstream"))
        assertEquals("9.9.9.9", valueOf("--dns"))
        assertEquals("gfw", valueOf("--noize"))
        assertEquals("auto", valueOf("--ech"))
        assertEquals("example-team", valueOf("--team"))
        assertEquals("24-48", valueOf("--fragment-size"))
        assertEquals("3-9", valueOf("--fragment-delay"))
        assertEquals("$filesDir/${configured.identityPath}", valueOf("--config"))
    }

    // --------------------------------------------- the Psiphon route is left ---

    /**
     * The Psiphon route stores its own compact form of `--psiphon*`. Those values
     * have to travel with the command line, and the packaged child process has to
     * be named: it is not on `PATH` under Android, and API 29 refuses to execute
     * anything in the app's writable directory.
     */
    @Test
    fun thePsiphonRouteCarriesItsValuesAndNamesThePackagedHelper() {
        val route = AetherProfileConfig(
            profileId = AetherProfile.AETHER_PSIPHON.id,
            base = AetherConfig.DEFAULT.copy(transport = "masque"),
            psiphonEnabled = true,
            psiphonEndpoint = "127.0.0.1:1821",
            psiphonRegion = "NL"
        )
        val helpers = AetherHelperPaths(
            psiphonBinary = "/data/app/lib/arm64/libpsiphon_tunnel_core.so",
            psiphonDirectory = "$filesDir/aether/psiphon"
        )
        val args = route.toAetherArgs(filesDir, helpers)
        assertTrue(args.toString(), args.contains("--psiphon"))
        assertEquals("NL", args[args.indexOf("--psiphon-region") + 1])
        assertEquals("127.0.0.1:1821", args[args.indexOf("--psiphon-bind") + 1])
        assertEquals(
            "the helper must be named, not looked up on PATH",
            helpers.psiphonBinary,
            args[args.indexOf("--psiphon-bin") + 1]
        )
        assertEquals(
            helpers.psiphonDirectory,
            args[args.indexOf("--psiphon-dir") + 1]
        )
        // One value owns AETHER_PSIPHON, so the mode is never asked for twice.
        assertEquals(1, args.count { it == "--psiphon" })
        assertEquals(1, args.count { it == "--psiphon-bin" })
    }

    @Test
    fun aRouteWithoutPsiphonEmitsNoneOfItsFlags() {
        val args = AetherProfileConfig(profileId = AetherProfile.WARP_DIRECT.id)
            .toAetherArgs(filesDir, AetherHelperPaths(psiphonBinary = "/lib/libpsiphon_tunnel_core.so"))
        assertFalse(
            "an off toggle must not start the helper: $args",
            args.any { it.startsWith("--psiphon") }
        )
    }

    /**
     * Every flag the Psiphon route turns into is one the core accepts. Read from
     * `cli.rs` here, because `parse_args` aborts the whole run on an unknown option
     * (`cli.rs:639-644`): a typo would look like a start that fails for no reason.
     */
    @Test
    fun thePsiphonFlagsExistInTheCoreCli() {
        val source = listOf(
            "../third_party/Aether/aether/src/cli.rs",
            "third_party/Aether/aether/src/cli.rs"
        ).map { File(it) }.firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue("the vendored CLI source is not present", source != null)
        val accepted = source!!.readLines()
            .filter { it.contains("=>") }
            .flatMap { line ->
                Regex("\"(-{1,2}[a-z0-9-]+)\"").findAll(line.substringBefore("=>"))
                    .map { it.groupValues[1] }.toList()
            }
            .toSet()
        listOf("--psiphon", "--psiphon-region", "--psiphon-bind", "--psiphon-bin", "--psiphon-dir")
            .forEach { flag ->
                assertTrue("cli.rs does not accept '$flag'", accepted.contains(flag))
            }
    }

    @Test
    fun theTeamFieldReachesTheTeamFlagExactlyOnce() {
        val args = AetherConfig.DEFAULT.copy(teamName = "route-team").toAetherArgs(filesDir)
        assertEquals("route-team", args[args.indexOf("--team") + 1])
        assertEquals(1, args.count { it == "--team" })

        val blank = AetherConfig.DEFAULT.toAetherArgs(filesDir)
        assertFalse("a blank team writes no flag at all: $blank", blank.contains("--team"))
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

    @Test
    fun aHelperOutsideTheLibraryDirectoryIsNeverAccepted() {
        val root = File(System.getProperty("java.io.tmpdir") ?: ".", "aether-helpers-${System.nanoTime()}")
        val library = File(root, "lib").apply { mkdirs() }
        val inside = File(library, AetherHelpers.PSIPHON_FILE).apply { writeText("#\n") }
        val outside = File(root, AetherHelpers.PSIPHON_FILE).apply { writeText("#\n") }
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
            assertFalse(AetherHelpers.hasPsiphon())
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

    /** A setting the removed screens owned is no longer named by any route. */
    @Test
    fun theRemovedSettingsAreNotOfferedAnyMore() {
        listOf("torSocks", "torBridges", "torUseBridges").forEach { key ->
            assertFalse("'$key' was removed with the Tor section", AetherCapabilities.knows(key))
            AetherProfile.values().forEach { profile ->
                val keys = getSectionsForProfile(profile).flatMap { it.keys }
                assertFalse("$profile still offers '$key'", keys.contains(key))
            }
        }
        assertFalse("a typo is still refused", AetherCapabilities.knows("fragmening"))
    }

    @Test
    fun onlyRoutesWithAGatewayReportFragmenting() {
        assertTrue(profileUsesAetherGateway(AetherProfile.WARP_DIRECT))
        assertTrue(profileUsesAetherGateway(AetherProfile.AETHER_PSIPHON))
        assertEquals(
            "the Tor routes were removed with the Tor configuration",
            listOf("warp_direct", "aether_psiphon"),
            AetherProfile.ALL.map { it.id }
        )
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
