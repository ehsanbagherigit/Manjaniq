package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.AetherApiOptions
import com.manjaniq.core.models.AetherCarrierOptions
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherEchOptions
import com.manjaniq.core.models.AetherEngineOptions
import com.manjaniq.core.models.AetherExitLocationOptions
import com.manjaniq.core.models.AetherGatewayOptions
import com.manjaniq.core.models.AetherGoolOptions
import com.manjaniq.core.models.AetherLogLevel
import com.manjaniq.core.models.AetherLoggingOptions
import com.manjaniq.core.models.AetherMimOptions
import com.manjaniq.core.models.AetherPsiphonMode
import com.manjaniq.core.models.AetherPsiphonOptions
import com.manjaniq.core.models.AetherRoutingOptions
import com.manjaniq.core.models.AetherStatsOptions
import com.manjaniq.core.models.AetherTlsOptions
import com.manjaniq.core.models.AetherTimingOptions
import com.manjaniq.core.models.AetherTorBridges
import com.manjaniq.core.models.AetherTorMode
import com.manjaniq.core.models.AetherTorOptions
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
 * The engine option model against the core's own command line.
 *
 * `cli.rs:parse_args` (`third_party/Aether/aether/src/cli.rs:469-647`) is the
 * contract: every flag writes an `AETHER_*` variable and an unknown flag aborts the
 * run with "unknown option". These tests pin the model to that contract instead of to
 * a remembered list, and [everyFlagThisModelEmitsExistsInTheCoreCli] re-reads the
 * source so a renamed or removed flag upstream fails here rather than at runtime.
 *
 * Robolectric, because `org.json` is a stub on the host JVM.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherEngineOptionsTest {

    private val filesDir = "/data/user/0/com.manjaniq/files"

    /** The core's CLI source, found from either the module or the repository root. */
    private fun cliSource(): File? = listOf(
        "../third_party/Aether/aether/src/cli.rs",
        "third_party/Aether/aether/src/cli.rs"
    ).map { File(it) }.firstOrNull { it.isFile }

    // ---- defaults must never override the engine ----

    @Test
    fun defaultOptionsEmitNoFlagsAtAll() {
        assertTrue(AetherEngineOptions().isDefault())
        assertEquals(emptyList<String>(), AetherEngineOptions().toArgs())
        assertNull(AetherEngineOptions().validate())

        // So a route nobody touched hands Aether exactly what it handed before these
        // options existed: no fingerprint, timing, layer or logging override.
        val args = AetherConfig.DEFAULT.toAetherArgs(filesDir)
        listOf(
            "--ech-dns", "--ech-domain",
            "--tor", "--tor-reverse", "--tor-only", "--tor-bridges", "--no-tor-bridges",
            "--psiphon", "--psiphon-reverse", "--psiphon-only", "--gool-classic",
            "--mim-inner", "--exit-loc", "--stats", "--tls-verify", "--tls-ciphers",
            "--tls-groups", "--disable-grease", "--no-quic-v2", "--routes",
            "--route-block", "--route-direct", "--startup-secs", "--reconnect-secs",
            "--validate-secs", "--keepalive", "--no-data-check", "--no-profile-retry",
            "--mark", "--api-fragment", "--enroll-address", "--register", "--team",
            "--access-id", "--access-secret", "--access-token", "--access-email",
            "--log-level", "--verbose", "--perf", "--gateway", "--h2-peer",
            "--masque-config", "--wg-config", "--wg-peer", "--tor-pt", "--tor-pt-dir"
        ).forEach { flag ->
            assertFalse("a default must not emit $flag: $args", args.contains(flag))
        }
    }

    /**
     * Every flag literal this model can emit, taken from all of its branches rather
     * than from one instance, so an either/or (an automatic scan versus an explicit
     * peer list, `--verbose` versus a named level) is covered on both sides.
     */
    private fun everyEmittableFlag(): Set<String> {
        val tor = listOf(
            AetherTorOptions(mode = AetherTorMode.CHAIN),
            AetherTorOptions(mode = AetherTorMode.REVERSE),
            AetherTorOptions(mode = AetherTorMode.ONLY),
            AetherTorOptions(bridges = AetherTorBridges.AUTO),
            AetherTorOptions(bridges = AetherTorBridges.OFF),
            AetherTorOptions(
                bridges = AetherTorBridges.CUSTOM,
                bridgeLines = listOf("obfs4 198.51.100.30:443 ABCD cert=xyz iat-mode=0")
            ),
            AetherTorOptions(bridgeFile = "$filesDir/bridges.txt"),
            AetherTorOptions(relays = "3", relayPorts = "443,80"),
            AetherTorOptions(pt = listOf("obfs4"), ptDir = listOf("$filesDir/pt")),
            AetherTorOptions(bind = "127.0.0.1:9050", http = "127.0.0.1:8118", dir = "$filesDir/tor")
        )
        val psiphon = listOf(
            AetherPsiphonOptions(mode = AetherPsiphonMode.CHAIN),
            AetherPsiphonOptions(mode = AetherPsiphonMode.REVERSE),
            AetherPsiphonOptions(mode = AetherPsiphonMode.ONLY),
            AetherPsiphonOptions(region = "NL", cdnMode = "fronting", cdnSets = "2"),
            AetherPsiphonOptions(cdnIps = "104.16.0.1", cdnSni = "cdn.example"),
            AetherPsiphonOptions(config = "$filesDir/p.config", serverEntries = "$filesDir/entries.dat"),
            AetherPsiphonOptions(bind = "127.0.0.1:14000", http = "127.0.0.1:14001"),
            AetherPsiphonOptions(dir = "$filesDir/psiphon", bin = "$filesDir/psiphon-tunnel-core")
        )
        val ech = listOf(
            AetherEchOptions(value = AetherEchOptions.AUTO),
            AetherEchOptions(value = "AE+DQAQYACAAGABeACkAJ1A4hB4aE/kiQ2C4G3cXhHgQ="),
            AetherEchOptions(dns = "https://cloudflare-dns.com/dns-query@address=1.1.1.1@sni=example.com"),
            AetherEchOptions(domain = AetherEchOptions.DEFAULT_DOMAIN)
        )
        val flags = mutableSetOf<String>()
        ech.forEach { flags += it.toArgs() }
        tor.forEach { flags += it.toArgs() }
        psiphon.forEach { flags += it.toArgs() }
        listOf(
            AetherGoolOptions(classic = true).toArgs(),
            AetherGoolOptions(peer = "198.51.100.9:2408").toArgs(),
            AetherGoolOptions(innerPeer = "198.51.100.10:2408", outerPeer = "198.51.100.11:443").toArgs(),
            AetherGoolOptions(peers = "198.51.100.12:443").toArgs(),
            AetherGoolOptions(scanPeers = true).toArgs(),
            AetherMimOptions(innerPeer = "198.51.100.20:2408", outerPeer = "198.51.100.21:443").toArgs(),
            AetherMimOptions(peers = "198.51.100.22:443").toArgs(),
            AetherMimOptions(scanPeers = true).toArgs(),
            AetherCarrierOptions(h2Peer = "198.51.100.50:443", masqueConfig = "$filesDir/m.json").toArgs(),
            AetherCarrierOptions(wgConfig = "$filesDir/w.conf", wgPeer = "198.51.100.51:2408").toArgs(),
            AetherExitLocationOptions(filter = "DE,NL", verificationSecs = "600").toArgs(),
            AetherStatsOptions(enabled = true, intervalSecs = "30").toArgs(),
            AetherTlsOptions(verify = true).toArgs(),
            AetherTlsOptions(ciphers = "TLS_AES_128_GCM_SHA256", groups = "X25519").toArgs(),
            AetherTlsOptions(disableGrease = true, noQuicV2 = true).toArgs(),
            AetherRoutingOptions(routesFile = "$filesDir/routes.txt").toArgs(),
            AetherRoutingOptions(block = "10.0.0.0/8", direct = "169.254.0.0/16").toArgs(),
            AetherTimingOptions(startupSecs = "45", reconnectSecs = "120", validateSecs = "300").toArgs(),
            AetherTimingOptions(keepaliveSecs = "25", noDataCheck = true, noProfileRetry = true).toArgs(),
            AetherTimingOptions(mark = "1234").toArgs(),
            AetherApiOptions(fragment = true, enrollAddress = "198.51.100.40:443").toArgs(),
            AetherApiOptions(register = "auto", team = "example-team").toArgs(),
            AetherApiOptions(
                accessId = "id", accessSecret = "secret",
                accessToken = "token", accessEmail = "ops@example.com"
            ).toArgs(),
            AetherLoggingOptions(level = "trace").toArgs(),
            AetherLoggingOptions(verbose = true).toArgs(),
            AetherLoggingOptions(perfProfile = "balanced").toArgs(),
            AetherGatewayOptions(enabled = true).toArgs()
        ).forEach { flags += it }
        // The route configuration renders the other half of the surface.
        listOf("masque", "wg", "gool", "mim").forEach { transport ->
            listOf("v4", "v6", "both").forEach { ip ->
                listOf(true, false).forEach { fragment ->
                    listOf(true, false).forEach { quick ->
                        flags += AetherConfig.DEFAULT.copy(
                            transport = transport,
                            ipVersion = ip,
                            httpProxyBind = if (quick) "127.0.0.1:1820" else "",
                            peer = if (fragment) "198.51.100.7:2408" else "",
                            upstream = "socks5://127.0.0.1:1080",
                            echEnabled = fragment,
                            quickReconnect = quick,
                            fragmentEnabled = fragment
                        ).toAetherArgs(filesDir)
                    }
                }
            }
        }
        return flags.filter { it.startsWith("-") }.toSet()
    }

    /**
     * Names the core accepts that nothing renders, on purpose: `--help` and
     * `--version` are not settings, and `parse_args` deliberately accepts several
     * spellings for one variable (`--wiw-inner` = `--gool-inner` = `--inner-peer` at
     * `cli.rs:547`, `--http2` = `--h2` at `:579`, `--stealth` = `--verified` at
     * `:574`). The model renders one name per variable, so the alternatives are
     * listed here instead of being reported as missing.
     */
    private val notRenderedByName = setOf(
        "--help", "--version", "-h", "-v",
        // cli.rs:568: --protocol writes the same AETHER_PROTOCOL that --masque, --wg,
        // --gool and --mim write, and the configuration renders those four.
        "--protocol",
        "--http2", "--quic", "--wireguard", "--warp", "--wiw", "--masque-in-masque",
        "--organization", "--outer-peer", "--inner-peer", "--wiw-outer", "--wiw-inner",
        "--wiw-peers", "--wiw-scan", "--verified", "--stealth", "--turbo", "--balanced",
        "--thorough", "--ironclad", "--ip"
    )

    /** Every flag name `parse_args` matches on, including each `a | b | c` alternative. */
    private fun cliFlags(source: File): Set<String> = source.readLines()
        .filter { it.contains("=>") }
        .flatMap { line ->
            val left = line.substringBefore("=>")
            Regex("\"(-{1,2}[a-z0-9-]+)\"").findAll(left).map { it.groupValues[1] }.toList()
        }
        .toSet()

    @Test
    fun everyFlagThisModelEmitsExistsInTheCoreCli() {
        val source = cliSource()
        assumeTrue("the vendored CLI source is not present, so drift cannot be checked", source != null)
        val known = cliFlags(source!!)
        assertTrue("no flags were extracted from cli.rs - the pattern needs updating", known.size > 50)

        val emitted = everyEmittableFlag()
        assertTrue("the fixture should exercise the whole surface: ${emitted.size}", emitted.size > 60)
        emitted.forEach { flag ->
            assertTrue(
                "the model emits '$flag', which cli.rs does not accept - the core would abort the run",
                known.contains(flag)
            )
        }
        // The reporting map names variables for flags only, so it cannot drift either.
        AetherEngineOptions.ENV_OF.keys.forEach { flag ->
            assertTrue("ENV_OF reports '$flag', which cli.rs does not accept", known.contains(flag))
        }
    }

    @Test
    fun everyFlagTheCoreAcceptsIsReachableFromTheApp() {
        val source = cliSource()
        assumeTrue("the vendored CLI source is not present", source != null)
        val unreachable = cliFlags(source!!) - everyEmittableFlag() - notRenderedByName
        assertEquals(
            "flags the core accepts that nothing in the app can express: $unreachable",
            emptySet<String>(),
            unreachable
        )
    }

    // ---- families ----

    @Test
    fun goolRendersItsOwnFlagsAndRefusesContradictoryPeerSourcing() {
        val gool = AetherGoolOptions(
            classic = true,
            peer = "198.51.100.9:2408",
            innerPeer = "198.51.100.10:2408",
            outerPeer = "198.51.100.11:443",
            scanPeers = true
        )
        assertEquals(
            listOf(
                "--gool-classic",
                "--gool-peer", "198.51.100.9:2408",
                "--gool-inner", "198.51.100.10:2408",
                "--gool-outer", "198.51.100.11:443",
                // --gool-peers and --gool-scan both write AETHER_WIW_PEERS, so only
                // the scan form is emitted rather than letting one overwrite the other.
                "--gool-scan"
            ),
            gool.toArgs()
        )
        assertNull(gool.validate())

        val both = gool.copy(scanPeers = true, peers = "198.51.100.12:443")
        assertNotNull(both.validate())
        assertTrue(both.validate()!!.contains("not both"))
        // An explicit list without the scan flag is fine.
        val listed = gool.copy(scanPeers = false, peers = "198.51.100.12:443")
        assertNull(listed.validate())
        assertEquals(
            listOf("--gool-peers", "198.51.100.12:443"),
            listed.toArgs().subList(listed.toArgs().size - 2, listed.toArgs().size)
        )
    }

    @Test
    fun mimUsesItsOwnPeerVariables() {
        val mim = AetherMimOptions(
            innerPeer = "198.51.100.20:2408",
            outerPeer = "198.51.100.21:443",
            peers = "198.51.100.22:443"
        )
        assertEquals(
            listOf(
                "--mim-inner", "198.51.100.20:2408",
                "--mim-outer", "198.51.100.21:443",
                "--mim-peers", "198.51.100.22:443"
            ),
            mim.toArgs()
        )
        assertNull(mim.validate())
        assertNotNull(mim.copy(scanPeers = true).validate())
        // A fresh instance: copy() would keep the inner and outer peers, and the point
        // here is that the scan form replaces the explicit list, not the whole model.
        assertEquals(listOf("--mim-scan"), AetherMimOptions(scanPeers = true).toArgs())
        assertEquals(
            listOf(
                "--mim-inner", "198.51.100.20:2408",
                "--mim-outer", "198.51.100.21:443",
                "--mim-scan"
            ),
            mim.copy(scanPeers = true, peers = "").toArgs()
        )
    }

    @Test
    fun torModesAreLayersNotTransports() {
        // cli.rs:497-499 writes AETHER_TOR = chain | reverse | only, so Tor combines
        // with whichever protocol the route already selected.
        assertEquals(listOf("--tor"), AetherTorOptions(mode = AetherTorMode.CHAIN).toArgs())
        assertEquals(listOf("--tor-reverse"), AetherTorOptions(mode = AetherTorMode.REVERSE).toArgs())
        assertEquals(listOf("--tor-only"), AetherTorOptions(mode = AetherTorMode.ONLY).toArgs())
        assertEquals(emptyList<String>(), AetherTorOptions(mode = AetherTorMode.OFF).toArgs())
        assertFalse(AetherTorOptions(mode = AetherTorMode.CHAIN).isDefault())
        assertTrue(AetherTorOptions(mode = AetherTorMode.CHAIN).isEnabled())
    }

    @Test
    fun torBridgeSourcingRendersExactlyOneForm() {
        assertEquals(
            listOf("--tor", "--tor-bridges"),
            AetherTorOptions(mode = AetherTorMode.CHAIN, bridges = AetherTorBridges.AUTO).toArgs()
        )
        assertEquals(
            listOf("--tor", "--no-tor-bridges"),
            AetherTorOptions(mode = AetherTorMode.CHAIN, bridges = AetherTorBridges.OFF).toArgs()
        )
        // --tor-bridge appends (cli.rs:501, :652), so each line is its own flag.
        val custom = AetherTorOptions(
            mode = AetherTorMode.CHAIN,
            bridges = AetherTorBridges.CUSTOM,
            bridgeLines = listOf(
                "obfs4 198.51.100.30:443 ABCD cert=xyz iat-mode=0",
                "  ",
                "obfs4 198.51.100.31:443 EFGH cert=xyz iat-mode=1"
            )
        )
        assertEquals(
            listOf(
                "--tor",
                "--tor-bridge", "obfs4 198.51.100.30:443 ABCD cert=xyz iat-mode=0",
                "--tor-bridge", "obfs4 198.51.100.31:443 EFGH cert=xyz iat-mode=1"
            ),
            custom.toArgs()
        )
        assertNull(custom.validate())
        // A custom list with nothing in it is a mistake, not a default.
        assertNotNull(
            AetherTorOptions(bridges = AetherTorBridges.CUSTOM).validate()
        )
        // Lines are only used when custom was actually chosen.
        assertNotNull(
            AetherTorOptions(
                bridges = AetherTorBridges.DEFAULT,
                bridgeLines = listOf("obfs4 198.51.100.30:443 ABCD")
            ).validate()
        )
    }

    @Test
    fun torHelperNeedIsDecidedByWhatTheBridgesActuallyUse() {
        // bridges.rs:19-33 embeds snowflake and obfs4 lines for AETHER_TOR_BRIDGES=auto,
        // and bridges.rs:38-46 maps lyrebird onto those transports, so auto needs it.
        assertTrue(AetherTorOptions(bridges = AetherTorBridges.AUTO).needsHelperBinary())
        // A custom obfs4 line needs it too...
        assertTrue(
            AetherTorOptions(
                bridges = AetherTorBridges.CUSTOM,
                bridgeLines = listOf("obfs4 198.51.100.30:443 ABCD cert=xyz iat-mode=0")
            ).needsHelperBinary()
        )
        // ...a plain bridge line does not, because no transport has to be spawned.
        assertFalse(
            AetherTorOptions(
                bridges = AetherTorBridges.CUSTOM,
                bridgeLines = listOf("198.51.100.30:443 ABCD")
            ).needsHelperBinary()
        )
        // Naming a transport or a directory asks for one explicitly.
        assertTrue(AetherTorOptions(pt = listOf("obfs4")).needsHelperBinary())
        assertTrue(AetherTorOptions(ptDir = listOf("/data/local/tmp/pt")).needsHelperBinary())
        // A bridge file's contents are unknown here, so it is treated as needing one -
        // and it is emitted whatever the bridge mode is, so the two must agree.
        listOf(AetherTorBridges.DEFAULT, AetherTorBridges.AUTO, AetherTorBridges.OFF).forEach { mode ->
            val withFile = AetherTorOptions(bridges = mode, bridgeFile = "/sdcard/bridges.txt")
            assertTrue(
                "a bridge file with bridges=$mode still sends --tor-bridge-file: ${withFile.toArgs()}",
                withFile.toArgs().contains("--tor-bridge-file")
            )
            assertTrue("...so it must be reported as needing a helper", withFile.needsHelperBinary())
        }
        // No bridges at all means plain relays, which the core fetches itself.
        assertFalse(AetherTorOptions(mode = AetherTorMode.CHAIN).needsHelperBinary())
        assertFalse(AetherTorOptions().needsHelperBinary())
    }

    @Test
    fun torListenersMustStayLoopbackAndItsNumbersMustBeUsable() {
        val good = AetherTorOptions(
            mode = AetherTorMode.ONLY,
            bind = "127.0.0.1:9050",
            http = "127.0.0.1:8118",
            relays = "3",
            relayPorts = "443,80,8080-8443",
            dir = "/data/user/0/com.manjaniq/files/tor"
        )
        assertNull(good.validate())
        assertEquals(
            listOf("--tor-only", "--tor-relays", "3", "--tor-relay-ports", "443,80,8080-8443",
                "--tor-bind", "127.0.0.1:9050", "--tor-http", "127.0.0.1:8118",
                "--tor-dir", "/data/user/0/com.manjaniq/files/tor"),
            good.toArgs()
        )
        assertTrue(good.validate() == null)
        assertNotNull(AetherTorOptions(bind = "0.0.0.0:9050").validate())
        assertNotNull(AetherTorOptions(bind = "127.0.0.1:80").validate())
        assertNotNull(AetherTorOptions(http = "10.0.0.5:8118").validate())
        assertNotNull(AetherTorOptions(relays = "many").validate())
        assertNotNull(AetherTorOptions(relays = "0").validate())
        assertNotNull(AetherTorOptions(relayPorts = "443,,80").validate())
        assertNotNull(AetherTorOptions(relayPorts = "70000").validate())
        assertNotNull(AetherTorOptions(mode = "sideways").validate())
        assertNotNull(AetherTorOptions(bridges = "some").validate())
    }

    @Test
    fun psiphonModesRegionsAndListenersAreCheckedBeforeTheyReachTheHelper() {
        val good = AetherPsiphonOptions(
            mode = AetherPsiphonMode.CHAIN,
            region = "NL",
            cdnMode = "fronting",
            cdnIps = "104.16.0.1,104.16.0.2",
            cdnSni = "cdn.example",
            cdnSets = "2",
            bind = "127.0.0.1:14000",
            http = "127.0.0.1:14001",
            dir = "/data/user/0/com.manjaniq/files/psiphon",
            bin = "/data/app/lib/arm64/libpsiphon_tunnel_core.so"
        )
        assertNull(good.validate())
        assertTrue(good.isEnabled())
        assertTrue(good.needsHelperBinary())
        assertEquals(
            listOf(
                "--psiphon", "--psiphon-region", "NL", "--psiphon-mode", "fronting",
                "--psiphon-cdn-ips", "104.16.0.1,104.16.0.2", "--psiphon-cdn-sni", "cdn.example",
                "--psiphon-cdn-sets", "2", "--psiphon-bind", "127.0.0.1:14000",
                "--psiphon-http", "127.0.0.1:14001",
                "--psiphon-dir", "/data/user/0/com.manjaniq/files/psiphon",
                "--psiphon-bin", "/data/app/lib/arm64/libpsiphon_tunnel_core.so"
            ),
            good.toArgs()
        )
        assertEquals(
            listOf("--psiphon-reverse"),
            AetherPsiphonOptions(mode = AetherPsiphonMode.REVERSE).toArgs()
        )
        assertEquals(
            listOf("--psiphon-only"),
            AetherPsiphonOptions(mode = AetherPsiphonMode.ONLY).toArgs()
        )
        assertFalse(AetherPsiphonOptions(mode = AetherPsiphonMode.OFF).isEnabled())
        assertFalse(AetherPsiphonOptions().needsHelperBinary())

        assertNotNull(AetherPsiphonOptions(mode = "tunnel").validate())
        assertNotNull(AetherPsiphonOptions(region = "Netherlands").validate())
        assertNotNull(AetherPsiphonOptions(region = "N1").validate())
        assertNull(AetherPsiphonOptions(region = "").validate())
        assertNotNull(AetherPsiphonOptions(bind = "0.0.0.0:14000").validate())
        assertNotNull(AetherPsiphonOptions(cdnIps = "not-an-ip").validate())
        assertNotNull(AetherPsiphonOptions(cdnIps = "104.16.0.999").validate())
    }

    @Test
    fun exitLocationAndStatsRenderAndValidate() {
        val exit = AetherExitLocationOptions(filter = "DE,NL", verificationSecs = "600")
        assertEquals(listOf("--exit-loc", "DE,NL", "--exit-loc-secs", "600"), exit.toArgs())
        assertNull(exit.validate())
        assertNotNull(AetherExitLocationOptions(verificationSecs = "soon").validate())
        assertNotNull(AetherExitLocationOptions(verificationSecs = "-5").validate())

        val stats = AetherStatsOptions(enabled = true, intervalSecs = "30")
        assertEquals(listOf("--stats", "--stats-secs", "30"), stats.toArgs())
        assertNull(stats.validate())
        assertNotNull(AetherStatsOptions(intervalSecs = "0").validate())
        // An interval with stats off is still forwarded: the core reads both
        // variables independently (cli.rs:533-534).
        assertEquals(listOf("--stats-secs", "30"), AetherStatsOptions(intervalSecs = "30").toArgs())
    }

    @Test
    fun echOwnsItsFlagOnceTheConfigurationNamesAKey() {
        assertEquals(listOf("--ech", "auto"), AetherEchOptions(value = "auto").toArgs())
        assertEquals(emptyList<String>(), AetherEchOptions().toArgs())
        assertFalse(AetherEchOptions().isEnabled())
        assertTrue(AetherEchOptions(value = "auto").isEnabled())

        val full = AetherEchOptions(
            value = "auto",
            dns = "https://cloudflare-dns.com/dns-query@address=1.1.1.1@sni=example.com",
            domain = "cloudflare-ech.com"
        )
        assertNull(full.validate())
        assertEquals(
            listOf(
                "--ech", "auto",
                "--ech-dns", "https://cloudflare-dns.com/dns-query@address=1.1.1.1@sni=example.com",
                "--ech-domain", "cloudflare-ech.com"
            ),
            full.toArgs()
        )

        // The engine's own defaults, quoted from cli.rs:146-155, are pinned so a
        // screen can say what "leave it blank" means.
        assertEquals("udp://1.1.1.1", AetherEchOptions.DEFAULT_DNS)
        assertEquals("cloudflare-ech.com", AetherEchOptions.DEFAULT_DOMAIN)

        // A value that is neither auto nor base64 is refused here rather than being
        // handed to BoringSSL, which would fail with NO_ECH_KEY mid-connection.
        assertNotNull(AetherEchOptions(value = "please").validate())
        assertNotNull(AetherEchOptions(dns = "1.1.1.1").validate())
        assertNotNull(AetherEchOptions(dns = "udp://1.1.1.1@proxy=1").validate())
        assertNull(AetherEchOptions(dns = "tcp://1.1.1.1:53").validate())
        assertNotNull(AetherEchOptions(domain = "cloudflare-ech dot com").validate())
        assertNotNull(AetherEchOptions(domain = "https://example.com").validate())
        assertNotNull(AetherEchOptions(domain = "localhost").validate())

        // --ech is emitted once: echEnabled keeps its meaning, and a named key wins.
        val enabledOnly = AetherConfig.DEFAULT.copy(echEnabled = true).toAetherArgs(filesDir)
        assertEquals(1, enabledOnly.count { it == "--ech" })
        assertEquals("auto", enabledOnly[enabledOnly.indexOf("--ech") + 1])

        val named = AetherConfig.DEFAULT.copy(
            echEnabled = true,
            engine = AetherEngineOptions(ech = AetherEchOptions(value = "AE+DQAQYACAAGABeACkAJ1A4"))
        ).toAetherArgs(filesDir)
        assertEquals("a named key must not be overwritten by the boolean: $named",
            1, named.count { it == "--ech" })
        assertEquals("AE+DQAQYACAAGABeACkAJ1A4", named[named.indexOf("--ech") + 1])

        val withoutEither = AetherConfig.DEFAULT.toAetherArgs(filesDir)
        assertFalse(withoutEither.contains("--ech"))
    }

    @Test
    fun tlsOptionsCanOnlyAddVerificationAndSayWhenTheyChangeTheFingerprint() {
        // tls.rs:241-245: verify_enabled() is false unless AETHER_TLS_VERIFY is set,
        // so --tls-verify turns pinning ON. Nothing in this model can turn a check off.
        assertEquals(listOf("--tls-verify"), AetherTlsOptions(verify = true).toArgs())
        assertEquals(emptyList<String>(), AetherTlsOptions().toArgs())
        assertNull(AetherTlsOptions().caution())

        val overridden = AetherTlsOptions(
            ciphers = "TLS_AES_128_GCM_SHA256:TLS_CHACHA20_POLY1305_SHA256",
            groups = "X25519:P-256",
            disableGrease = true,
            noQuicV2 = true
        )
        assertEquals(
            listOf(
                "--tls-ciphers", "TLS_AES_128_GCM_SHA256:TLS_CHACHA20_POLY1305_SHA256",
                "--tls-groups", "X25519:P-256", "--disable-grease", "--no-quic-v2"
            ),
            overridden.toArgs()
        )
        assertNull(overridden.validate())
        val caution = overridden.caution()
        assertNotNull("a fingerprint override must be reported, not applied silently", caution)
        assertTrue(caution!!.contains("cipher list"))
        assertTrue(caution.contains("GREASE disabled"))
        assertTrue(caution.contains("QUIC v2 disabled"))
        // Verification alone is not a fingerprint override.
        assertNull(AetherTlsOptions(verify = true).caution())

        assertNotNull(AetherTlsOptions(ciphers = "A: :B").validate())
        assertNotNull(AetherTlsOptions(ciphers = "TLS AES").validate())
        assertNotNull(AetherTlsOptions(groups = "X25519, P-256").validate())
    }

    @Test
    fun routingRulesAreNetworksNotNames() {
        val routing = AetherRoutingOptions(
            routesFile = "/data/user/0/com.manjaniq/files/routes.txt",
            block = "10.0.0.0/8,192.168.0.0/16",
            direct = "169.254.0.0/16"
        )
        assertEquals(
            listOf(
                "--routes", "/data/user/0/com.manjaniq/files/routes.txt",
                "--route-block", "10.0.0.0/8,192.168.0.0/16",
                "--route-direct", "169.254.0.0/16"
            ),
            routing.toArgs()
        )
        assertNull(routing.validate())
        assertNotNull(AetherRoutingOptions(block = "example.com").validate())
        assertNotNull(AetherRoutingOptions(block = "10.0.0.0/200").validate())
        assertNotNull(AetherRoutingOptions(direct = "10.0.0.999/8").validate())
    }

    @Test
    fun timingKnobsThatWriteTwoVariablesStaySingleFields() {
        val timing = AetherTimingOptions(
            startupSecs = "45",
            reconnectSecs = "120",
            validateSecs = "300",
            keepaliveSecs = "25",
            noDataCheck = true,
            noProfileRetry = true,
            mark = "1234"
        )
        assertEquals(
            listOf(
                "--startup-secs", "45", "--reconnect-secs", "120", "--validate-secs", "300",
                "--keepalive", "25", "--no-data-check", "--no-profile-retry", "--mark", "1234"
            ),
            timing.toArgs()
        )
        assertNull(timing.validate())
        // cli.rs:586-599: these three write both the MASQUE and the WireGuard
        // variable, which is why they are one field each rather than per-protocol.
        assertEquals(
            "AETHER_MASQUE_RECONNECT_SECS + AETHER_WG_RECONNECT_SECS",
            AetherEngineOptions.ENV_OF["--reconnect-secs"]
        )
        assertEquals(
            "AETHER_MASQUE_VALIDATE_SECS + AETHER_WG_VALIDATE_SECS",
            AetherEngineOptions.ENV_OF["--validate-secs"]
        )
        assertEquals(
            "AETHER_MASQUE_NO_DATA_CHECK + AETHER_WG_NO_DATA_CHECK",
            AetherEngineOptions.ENV_OF["--no-data-check"]
        )
        assertNotNull(AetherTimingOptions(startupSecs = "fast").validate())
        assertNotNull(AetherTimingOptions(keepaliveSecs = "-1").validate())
        assertNotNull(AetherTimingOptions(mark = "0x10").validate())
    }

    @Test
    fun apiOptionsCarryCredentialsAndAreRecognisedAsSecrets() {
        val api = AetherApiOptions(
            fragment = true,
            enrollAddress = "198.51.100.40:443",
            register = "auto",
            team = "example-team",
            accessId = "id-123",
            accessSecret = "secret-456",
            accessToken = "token-789",
            accessEmail = "ops@example.com"
        )
        assertEquals(
            listOf(
                "--api-fragment", "--enroll-address", "198.51.100.40:443",
                "--register", "auto", "--team", "example-team",
                "--access-id", "id-123", "--access-secret", "secret-456",
                "--access-token", "token-789", "--access-email", "ops@example.com"
            ),
            api.toArgs()
        )
        assertNull(api.validate())
        assertTrue("credentials must be identifiable so the log can redact them", api.hasSecrets())
        assertFalse(AetherApiOptions().hasSecrets())
        assertTrue(AetherApiOptions(accessToken = "t").hasSecrets())
        assertNotNull(AetherApiOptions(accessEmail = "not-an-email").validate())
        assertNotNull(AetherApiOptions(enrollAddress = "198.51.100.40:99999").validate())
    }

    @Test
    fun loggingAndGatewayRenderOneFlagEach() {
        assertEquals(listOf("--verbose"), AetherLoggingOptions(verbose = true).toArgs())
        assertEquals(
            listOf("--log-level", "trace"),
            AetherLoggingOptions(level = "trace").toArgs()
        )
        // --verbose is log-level debug (cli.rs:634), so only one is emitted.
        assertEquals(
            listOf("--verbose", "--perf", "balanced"),
            AetherLoggingOptions(verbose = true, level = "trace", perfProfile = "balanced").toArgs()
        )
        assertNotNull(AetherLoggingOptions(level = "shout").validate())
        assertNull(AetherLoggingOptions(level = AetherLogLevel.DEFAULT).validate())

        assertEquals(listOf("--gateway"), AetherGatewayOptions(enabled = true).toArgs())
        assertEquals(emptyList<String>(), AetherGatewayOptions().toArgs())
        assertNull(AetherGatewayOptions(enabled = true).validate())
    }

    @Test
    fun carrierOverridesStayBesideTheProtocolChoice() {
        val carrier = AetherCarrierOptions(
            h2Peer = "198.51.100.50:443",
            masqueConfig = "/data/user/0/com.manjaniq/files/masque.json",
            wgConfig = "/data/user/0/com.manjaniq/files/wg.conf",
            wgPeer = "198.51.100.51:2408"
        )
        assertEquals(
            listOf(
                "--h2-peer", "198.51.100.50:443",
                "--masque-config", "/data/user/0/com.manjaniq/files/masque.json",
                "--wg-config", "/data/user/0/com.manjaniq/files/wg.conf",
                "--wg-peer", "198.51.100.51:2408"
            ),
            carrier.toArgs()
        )
        assertNull(carrier.validate())
        assertNotNull(AetherCarrierOptions(wgPeer = "198.51.100.51:99999").validate())
        assertNotNull(AetherCarrierOptions(h2Peer = "2001:db8::1:443").validate())
    }

    // ---- composition with the configuration that already exists ----

    @Test
    fun engineOptionsAreAppendedToTheRouteCommandLine() {
        val base = AetherConfig.DEFAULT.toAetherArgs(filesDir)
        val withTor = AetherConfig.DEFAULT.copy(
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.CHAIN, relays = "3"),
                stats = AetherStatsOptions(enabled = true),
                exitLocation = AetherExitLocationOptions(filter = "DE")
            )
        ).toAetherArgs(filesDir)
        // Everything the route already produced is untouched and in the same order.
        assertEquals(base, withTor.subList(0, base.size))
        assertEquals(
            listOf("--tor", "--tor-relays", "3", "--exit-loc", "DE", "--stats"),
            withTor.subList(base.size, withTor.size)
        )
    }

    @Test
    fun aConfigurationThatNeedsAMissingHelperIsRefusedWithTheReason() {
        val psiphon = AetherConfig.DEFAULT.copy(
            engine = AetherEngineOptions(psiphon = AetherPsiphonOptions(mode = AetherPsiphonMode.CHAIN))
        )
        val problem = psiphon.validate()
        assertNotNull(problem)
        assertTrue(problem!!, problem.contains(AetherEngineOptions.HELPER_PSIPHON))
        // Naming an executable is accepted: the refusal is about the missing binary.
        assertNull(
            psiphon.copy(
                engine = psiphon.engine.copy(
                    psiphon = psiphon.engine.psiphon.copy(bin = "/data/app/lib/arm64/libpsiphon.so")
                )
            ).validate()
        )

        val bridges = AetherConfig.DEFAULT.copy(
            engine = AetherEngineOptions(
                tor = AetherTorOptions(mode = AetherTorMode.CHAIN, bridges = AetherTorBridges.AUTO)
            )
        )
        val bridgeProblem = bridges.validate()
        assertNotNull(bridgeProblem)
        assertTrue(bridgeProblem!!, bridgeProblem.contains(AetherEngineOptions.HELPER_TOR_PT))
        // A PT directory supplied by the user lifts it.
        assertNull(
            bridges.copy(
                engine = bridges.engine.copy(
                    tor = bridges.engine.tor.copy(ptDir = listOf("/data/local/tmp/pt"))
                )
            ).validate()
        )
        // Plain relays need no helper and stay available.
        assertNull(
            AetherConfig.DEFAULT.copy(
                engine = AetherEngineOptions(tor = AetherTorOptions(mode = AetherTorMode.CHAIN))
            ).validate()
        )
    }

    // ---- persistence ----

    @Test
    fun engineOptionsSurviveARoundTripExactly() {
        val options = populated()
        assertEquals(options, AetherEngineOptions.fromJson(options.toJson()))

        val config = AetherConfig.DEFAULT.copy(engine = options)
        assertEquals(config, AetherConfig.fromJson(config.toJson()))
    }

    @Test
    fun aConfigurationStoredBeforeTheseOptionsExistedStillLoads() {
        val legacy = """
            {"identityPath":"aether_identity","transport":"masque","scanMode":"balanced",
             "noizeProfile":"firewall","socksBind":"127.0.0.1:1819","httpProxyBind":"",
             "peer":"","upstream":"","echEnabled":false,"dns":"1.1.1.1,1.0.0.1",
             "ipVersion":"v4","teamName":"","quickReconnect":true,
             "fragmentEnabled":false,"fragmentSize":"16-32","fragmentDelay":"2-10",
             "fragmentSniSplit":false}
        """.trimIndent()
        val parsed = AetherConfig.fromJson(legacy)
        assertNotNull(parsed)
        assertEquals(AetherEngineOptions(), parsed!!.engine)
        assertTrue(parsed.engine.isDefault())
        assertNull(parsed.validate())
        // And an empty engine object is the same as no engine object at all.
        assertEquals(AetherEngineOptions(), AetherEngineOptions.fromJson(null))
    }

    @Test
    fun envVarsReportsWhatTheOptionsBecome() {
        val vars = AetherEngineOptions(
            tor = AetherTorOptions(mode = AetherTorMode.CHAIN),
            stats = AetherStatsOptions(enabled = true, intervalSecs = "30"),
            tls = AetherTlsOptions(verify = true)
        ).envVars()
        assertEquals(listOf("AETHER_TOR", "AETHER_STATS", "AETHER_STATS_SECS", "AETHER_TLS_VERIFY"), vars)
        assertEquals(emptyList<String>(), AetherEngineOptions().envVars())
    }

    /** Every family populated, for the drift and round-trip tests. */
    private fun populated() = AetherEngineOptions(
        ech = AetherEchOptions(
            value = AetherEchOptions.AUTO,
            dns = "udp://1.0.0.1",
            domain = AetherEchOptions.DEFAULT_DOMAIN
        ),
        carrier = AetherCarrierOptions(
            h2Peer = "198.51.100.50:443",
            masqueConfig = "$filesDir/masque.json",
            wgConfig = "$filesDir/wg.conf",
            wgPeer = "198.51.100.51:2408"
        ),
        gool = AetherGoolOptions(
            classic = true,
            peer = "198.51.100.9:2408",
            innerPeer = "198.51.100.10:2408",
            outerPeer = "198.51.100.11:443",
            peers = "198.51.100.12:443"
        ),
        mim = AetherMimOptions(
            innerPeer = "198.51.100.20:2408",
            outerPeer = "198.51.100.21:443",
            peers = "198.51.100.22:443"
        ),
        tor = AetherTorOptions(
            mode = AetherTorMode.CHAIN,
            bridges = AetherTorBridges.CUSTOM,
            bridgeLines = listOf("obfs4 198.51.100.30:443 ABCD cert=xyz iat-mode=0"),
            bridgeFile = "$filesDir/bridges.txt",
            relays = "3",
            relayPorts = "443,80",
            pt = listOf("obfs4"),
            ptDir = listOf("$filesDir/pt"),
            bind = "127.0.0.1:9050",
            http = "127.0.0.1:8118",
            dir = "$filesDir/tor"
        ),
        psiphon = AetherPsiphonOptions(
            mode = AetherPsiphonMode.CHAIN,
            region = "NL",
            cdnMode = "fronting",
            cdnIps = "104.16.0.1",
            cdnSni = "cdn.example",
            cdnSets = "2",
            config = "$filesDir/psiphon.config",
            serverEntries = "$filesDir/server_entries.dat",
            bind = "127.0.0.1:14000",
            http = "127.0.0.1:14001",
            dir = "$filesDir/psiphon",
            bin = "$filesDir/psiphon-tunnel-core"
        ),
        exitLocation = AetherExitLocationOptions(filter = "DE,NL", verificationSecs = "600"),
        stats = AetherStatsOptions(enabled = true, intervalSecs = "30"),
        tls = AetherTlsOptions(
            verify = true,
            ciphers = "TLS_AES_128_GCM_SHA256",
            groups = "X25519",
            disableGrease = true,
            noQuicV2 = true
        ),
        routing = AetherRoutingOptions(
            routesFile = "$filesDir/routes.txt",
            block = "10.0.0.0/8",
            direct = "169.254.0.0/16"
        ),
        timing = AetherTimingOptions(
            startupSecs = "45",
            reconnectSecs = "120",
            validateSecs = "300",
            keepaliveSecs = "25",
            noDataCheck = true,
            noProfileRetry = true,
            mark = "1234"
        ),
        api = AetherApiOptions(
            fragment = true,
            enrollAddress = "198.51.100.40:443",
            register = "auto",
            team = "example-team",
            accessId = "id-123",
            accessSecret = "secret-456",
            accessToken = "token-789",
            accessEmail = "ops@example.com"
        ),
        logging = AetherLoggingOptions(level = "trace", perfProfile = "balanced"),
        gateway = AetherGatewayOptions(enabled = true)
    )
}
