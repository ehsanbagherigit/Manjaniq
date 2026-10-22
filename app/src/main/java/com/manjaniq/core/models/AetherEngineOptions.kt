package com.manjaniq.core.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * Every engine option the vendored Aether core accepts, modelled one-to-one against
 * `third_party/Aether/aether/src/cli.rs`.
 *
 * `cli.rs:parse_args` is nothing but a translator: each flag writes one or more
 * `AETHER_*` variables (`set` at `cli.rs:648`, `append` at `:652`) and then
 * `run_with` (`lib.rs:77`) reads them. So the flag list *is* the engine's contract,
 * and this file mirrors it exactly rather than inventing names or semantics.
 *
 * Two rules govern the whole model:
 *
 *  1. **A default is not emitted.** Every field defaults to "leave the engine
 *     alone", and [toArgs] then omits that flag entirely, so the core keeps its own
 *     default instead of being handed a value nobody chose. This matters most for the
 *     security- and fingerprint-relevant knobs (`--tls-verify`, `--tls-ciphers`,
 *     `--tls-groups`, `--disable-grease`, `--no-data-check`), which are reachable but
 *     never set by default.
 *  2. **Only flags that exist are emitted.** `parse_args` ends in
 *     `unknown option '<x>'` (`cli.rs:641-644`) and aborts the run, so a made-up or
 *     renamed flag is a hard failure, not a warning. [AetherEngineOptionsTest] pins
 *     every literal here against the core's own list.
 *
 * Layers compose: `AETHER_PROTOCOL` picks masque/wg/gool/mim, while
 * `AETHER_TOR` and `AETHER_PSIPHON` are chain/reverse/only *on top of* that protocol
 * (`cli.rs:497-499`, `:514-516`). That is why tor and psiphon are options rather than
 * transports.
 */
data class AetherEngineOptions(
    val ech: AetherEchOptions = AetherEchOptions(),
    val carrier: AetherCarrierOptions = AetherCarrierOptions(),
    val gool: AetherGoolOptions = AetherGoolOptions(),
    val mim: AetherMimOptions = AetherMimOptions(),
    val tor: AetherTorOptions = AetherTorOptions(),
    val psiphon: AetherPsiphonOptions = AetherPsiphonOptions(),
    val exitLocation: AetherExitLocationOptions = AetherExitLocationOptions(),
    val stats: AetherStatsOptions = AetherStatsOptions(),
    val tls: AetherTlsOptions = AetherTlsOptions(),
    val routing: AetherRoutingOptions = AetherRoutingOptions(),
    val timing: AetherTimingOptions = AetherTimingOptions(),
    val api: AetherApiOptions = AetherApiOptions(),
    val logging: AetherLoggingOptions = AetherLoggingOptions(),
    val gateway: AetherGatewayOptions = AetherGatewayOptions()
) {

    /** True when nothing here differs from the engine's own defaults. */
    fun isDefault(): Boolean = this == AetherEngineOptions()

    /**
     * The command line for these options, in a stable order, with defaults omitted.
     *
     * Feed this to [com.manjaniq.AetherBridge.coreStart] (→ `aether_core_start`,
     * `ffi.rs:492`), which runs `cli::parse_args` in-process and returns a pollable,
     * cancellable job. The protocol flag itself comes from
     * [AetherConfig.toAetherArgs], so this list never repeats it.
     */
    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        args += ech.toArgs()
        args += carrier.toArgs()
        args += gool.toArgs()
        args += mim.toArgs()
        args += tor.toArgs()
        args += psiphon.toArgs()
        args += exitLocation.toArgs()
        args += stats.toArgs()
        args += tls.toArgs()
        args += routing.toArgs()
        args += timing.toArgs()
        args += api.toArgs()
        args += logging.toArgs()
        args += gateway.toArgs()
        return args
    }

    /**
     * The first reason these options cannot be handed to Aether, or null.
     *
     * Checked before anything is written, because the engine's own parsers fall back
     * silently for values they do not understand (see `fragment::parse_range`) and an
     * unusable value would otherwise become a setting nobody chose.
     */
    fun validate(): String? {
        ech.validate()?.let { return it }
        carrier.validate()?.let { return it }
        gool.validate()?.let { return it }
        mim.validate()?.let { return it }
        tor.validate()?.let { return it }
        psiphon.validate()?.let { return it }
        exitLocation.validate()?.let { return it }
        stats.validate()?.let { return it }
        tls.validate()?.let { return it }
        routing.validate()?.let { return it }
        timing.validate()?.let { return it }
        api.validate()?.let { return it }
        logging.validate()?.let { return it }
        gateway.validate()?.let { return it }
        return null
    }

    /** Environment variables these options end up as, for the capability report. */
    fun envVars(): List<String> = toArgs().mapNotNull { flag -> ENV_OF[flag] }.distinct()

    fun toJson(): JSONObject = JSONObject().apply {
        put("ech", ech.toJson())
        put("carrier", carrier.toJson())
        put("gool", gool.toJson())
        put("mim", mim.toJson())
        put("tor", tor.toJson())
        put("psiphon", psiphon.toJson())
        put("exitLocation", exitLocation.toJson())
        put("stats", stats.toJson())
        put("tls", tls.toJson())
        put("routing", routing.toJson())
        put("timing", timing.toJson())
        put("api", api.toJson())
        put("logging", logging.toJson())
        put("gateway", gateway.toJson())
    }

    companion object {
        /**
         * Flag → the variable `cli.rs` writes for it, quoted from `parse_args`. Used
         * only for reporting; the engine does the real translation.
         */
        val ENV_OF: Map<String, String> = mapOf(
            "--gool-classic" to "AETHER_GOOL_MODE",
            "--gool-peer" to "AETHER_GOOL_INNER",
            "--gool-inner" to "AETHER_WIW_INNER_PEER",
            "--gool-outer" to "AETHER_WIW_OUTER_PEER",
            "--gool-peers" to "AETHER_WIW_PEERS",
            "--gool-scan" to "AETHER_WIW_PEERS",
            "--mim" to "AETHER_PROTOCOL",
            "--mim-inner" to "AETHER_MIM_INNER_PEER",
            "--mim-outer" to "AETHER_MIM_OUTER_PEER",
            "--mim-peers" to "AETHER_MIM_PEERS",
            "--mim-scan" to "AETHER_MIM_PEERS",
            "--tor" to "AETHER_TOR",
            "--tor-reverse" to "AETHER_TOR",
            "--tor-only" to "AETHER_TOR",
            "--tor-bridge" to "AETHER_TOR_BRIDGES",
            "--tor-bridges" to "AETHER_TOR_BRIDGES",
            "--no-tor-bridges" to "AETHER_TOR_BRIDGES",
            "--tor-bridge-file" to "AETHER_TOR_BRIDGE_FILE",
            "--tor-relays" to "AETHER_TOR_RELAYS",
            "--tor-relay-ports" to "AETHER_TOR_RELAY_PORTS",
            "--tor-pt" to "AETHER_TOR_PT",
            "--tor-pt-dir" to "AETHER_TOR_PT_DIR",
            "--tor-bind" to "AETHER_TOR_BIND",
            "--tor-http" to "AETHER_TOR_HTTP",
            "--tor-dir" to "AETHER_TOR_DIR",
            "--psiphon" to "AETHER_PSIPHON",
            "--psiphon-reverse" to "AETHER_PSIPHON",
            "--psiphon-only" to "AETHER_PSIPHON",
            "--psiphon-config" to "AETHER_PSIPHON_CONFIG",
            "--psiphon-bind" to "AETHER_PSIPHON_BIND",
            "--psiphon-http" to "AETHER_PSIPHON_HTTP",
            "--psiphon-region" to "AETHER_PSIPHON_REGION",
            "--psiphon-mode" to "AETHER_PSIPHON_MODE",
            "--psiphon-cdn-ips" to "AETHER_PSIPHON_CDN_IPS",
            "--psiphon-cdn-sni" to "AETHER_PSIPHON_CDN_SNI",
            "--psiphon-cdn-sets" to "AETHER_PSIPHON_CDN_SETS",
            "--psiphon-dir" to "AETHER_PSIPHON_DIR",
            "--psiphon-bin" to "AETHER_PSIPHON_BIN",
            "--psiphon-server-entries" to "AETHER_PSIPHON_SERVER_ENTRIES",
            "--exit-loc" to "AETHER_EXIT_LOC",
            "--exit-loc-secs" to "AETHER_EXIT_LOC_SECS",
            "--stats" to "AETHER_STATS",
            "--stats-secs" to "AETHER_STATS_SECS",
            "--tls-verify" to "AETHER_TLS_VERIFY",
            "--tls-ciphers" to "AETHER_TLS_CIPHERS",
            "--tls-groups" to "AETHER_TLS_GROUPS",
            "--disable-grease" to "AETHER_DISABLE_GREASE",
            "--no-quic-v2" to "AETHER_QUIC_V2",
            "--routes" to "AETHER_ROUTES_FILE",
            "--route-block" to "AETHER_ROUTE_BLOCK",
            "--route-direct" to "AETHER_ROUTE_DIRECT",
            "--startup-secs" to "AETHER_MASQUE_STARTUP_SECS",
            "--reconnect-secs" to "AETHER_MASQUE_RECONNECT_SECS + AETHER_WG_RECONNECT_SECS",
            "--validate-secs" to "AETHER_MASQUE_VALIDATE_SECS + AETHER_WG_VALIDATE_SECS",
            "--keepalive" to "AETHER_WG_KEEPALIVE",
            "--no-data-check" to "AETHER_MASQUE_NO_DATA_CHECK + AETHER_WG_NO_DATA_CHECK",
            "--no-profile-retry" to "AETHER_WG_NO_PROFILE_RETRY",
            "--mark" to "AETHER_MARK",
            "--api-fragment" to "AETHER_API_FRAGMENT",
            "--enroll-address" to "AETHER_ENROLL_ADDRESS",
            "--register" to "AETHER_REGISTER",
            "--team" to "AETHER_TEAM",
            "--access-id" to "AETHER_ACCESS_CLIENT_ID",
            "--access-secret" to "AETHER_ACCESS_CLIENT_SECRET",
            "--access-token" to "AETHER_ACCESS_TOKEN",
            "--access-email" to "AETHER_ACCESS_EMAIL",
            "--log-level" to "AETHER_LOG_LEVEL",
            "--verbose" to "AETHER_LOG_LEVEL",
            "--perf" to "AETHER_PERF_PROFILE",
            "--gateway" to "AETHER_GATEWAY",
            "--ech" to "AETHER_ECH",
            "--ech-dns" to "AETHER_ECH_DNS",
            "--ech-domain" to "AETHER_ECH_DOMAIN",
            "--h2-peer" to "AETHER_MASQUE_H2_PEER",
            "--masque-config" to "AETHER_MASQUE_CONFIG",
            "--wg-config" to "AETHER_WG_CONFIG",
            "--wg-peer" to "AETHER_WG_PEER"
        )

        /**
         * Options that need a helper executable the app does not ship.
         *
         * `psiphon.rs:749` spawns `psiphon-tunnel-core` and `bridges.rs:301` spawns a
         * Tor pluggable transport; both come from the reference package's `pt/`
         * directory. On targetSdk 35 an app cannot `execve` a file in its own writable
         * storage (Android 10's W^X rule), so a helper can only run from
         * `nativeLibraryDir`, which needs `packaging.jniLibs.useLegacyPackaging = true`
         * in the shared `app/build.gradle.kts`. Until that is approved these two are
         * reported honestly instead of being offered as controls that cannot work.
         */
        const val HELPER_PSIPHON = "psiphon-tunnel-core"
        const val HELPER_TOR_PT = "lyrebird"

        fun fromJson(json: JSONObject?): AetherEngineOptions {
            if (json == null) return AetherEngineOptions()
            return AetherEngineOptions(
                ech = AetherEchOptions.fromJson(json.optJSONObject("ech")),
                carrier = AetherCarrierOptions.fromJson(json.optJSONObject("carrier")),
                gool = AetherGoolOptions.fromJson(json.optJSONObject("gool")),
                mim = AetherMimOptions.fromJson(json.optJSONObject("mim")),
                tor = AetherTorOptions.fromJson(json.optJSONObject("tor")),
                psiphon = AetherPsiphonOptions.fromJson(json.optJSONObject("psiphon")),
                exitLocation = AetherExitLocationOptions.fromJson(json.optJSONObject("exitLocation")),
                stats = AetherStatsOptions.fromJson(json.optJSONObject("stats")),
                tls = AetherTlsOptions.fromJson(json.optJSONObject("tls")),
                routing = AetherRoutingOptions.fromJson(json.optJSONObject("routing")),
                timing = AetherTimingOptions.fromJson(json.optJSONObject("timing")),
                api = AetherApiOptions.fromJson(json.optJSONObject("api")),
                logging = AetherLoggingOptions.fromJson(json.optJSONObject("logging")),
                gateway = AetherGatewayOptions.fromJson(json.optJSONObject("gateway"))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// encrypted client hello (--ech, --ech-dns, --ech-domain)
// ---------------------------------------------------------------------------

/**
 * Encrypted Client Hello (`cli.rs:583-585`, help at `cli.rs:141-155`).
 *
 * Read the core's own wording before changing a default here: with a key asked for
 * and none BoringSSL can offer, the connection **refuses** rather than send the
 * server name in the clear (`tls.rs:527-531`, "stopping rather than send the server
 * name in the clear"). So enabling ECH can turn a working tunnel into a refused one,
 * and that refusal is the feature working, not a defect.
 *
 * [value] is what `--ech` takes: `auto` looks the key up over DoH, anything else is a
 * base64 ECHConfig, and blank leaves the engine's own default (off). [dns] is the
 * resolver `auto` asks (default `udp://1.1.1.1`, with optional `@address=` and
 * `@sni=` suffixes); [domain] is the name whose key `auto` takes (default
 * `cloudflare-ech.com`).
 */
data class AetherEchOptions(
    val value: String = "",
    val dns: String = "",
    val domain: String = ""
) {
    fun isDefault() = this == AetherEchOptions()

    fun isEnabled(): Boolean = value.isNotBlank()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        value.trim().let { if (it.isNotEmpty()) { args += "--ech"; args += it } }
        dns.trim().let { if (it.isNotEmpty()) { args += "--ech-dns"; args += it } }
        domain.trim().let { if (it.isNotEmpty()) { args += "--ech-domain"; args += it } }
        return args
    }

    fun validate(): String? {
        val wanted = value.trim()
        if (wanted.isNotEmpty() && wanted != AUTO && !looksLikeBase64(wanted)) {
            return "ECH must be '$AUTO' to look the key up, or a base64 ECHConfig"
        }
        val resolver = dns.trim()
        if (resolver.isNotEmpty()) {
            val url = resolver.substringBefore('@')
            if (!url.startsWith("udp://") && !url.startsWith("tcp://") && !url.startsWith("https://")) {
                return "ECH resolver must be udp://, tcp:// or https:// (for example udp://1.1.1.1)"
            }
            // Only the two suffixes the core documents (cli.rs:149-153).
            resolver.substringAfter('@', "").split('@').filter { it.isNotEmpty() }.forEach { part ->
                if (!part.startsWith("address=") && !part.startsWith("sni=")) {
                    return "ECH resolver suffixes are limited to @address=<ip|name> and @sni=<name>"
                }
            }
        }
        val name = domain.trim()
        if (name.isNotEmpty()) {
            if (name.any { it.isWhitespace() }) return "ECH domain must not contain spaces"
            if (name.contains("://")) return "ECH domain must be a name, not a URL"
            if (!name.contains('.')) return "ECH domain must be a fully qualified name"
        }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("value", value); put("dns", dns); put("domain", domain)
    }

    companion object {
        /** `--ech auto`: look the key up over DoH (`cli.rs:141-145`). */
        const val AUTO = "auto"

        /** The engine's own defaults, quoted from `cli.rs:146-155`. */
        const val DEFAULT_DNS = "udp://1.1.1.1"
        const val DEFAULT_DOMAIN = "cloudflare-ech.com"

        private fun looksLikeBase64(value: String): Boolean =
            value.length >= 16 && value.all {
                it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' ||
                    it == '=' || it == '-' || it == '_'
            }

        fun fromJson(o: JSONObject?) = if (o == null) AetherEchOptions() else AetherEchOptions(
            value = o.optString("value", ""),
            dns = o.optString("dns", ""),
            domain = o.optString("domain", "")
        )
    }
}

// ---------------------------------------------------------------------------
// carrier overrides (--h2-peer, --masque-config, --wg-config, --wg-peer)
// ---------------------------------------------------------------------------

/**
 * Per-carrier overrides that sit beside the protocol choice rather than inside it.
 *
 * `--peer` (`AetherConfig.peer`) is the general peer; these four are the
 * carrier-specific ones: `--h2-peer` (`cli.rs:582`), `--masque-config` (`:610`),
 * `--wg-config` (`:609`) and `--wg-peer` (`:542`).
 */
data class AetherCarrierOptions(
    val h2Peer: String = "",
    val masqueConfig: String = "",
    val wgConfig: String = "",
    val wgPeer: String = ""
) {
    fun isDefault() = this == AetherCarrierOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        h2Peer.trim().let { if (it.isNotEmpty()) { args += "--h2-peer"; args += it } }
        masqueConfig.trim().let { if (it.isNotEmpty()) { args += "--masque-config"; args += it } }
        wgConfig.trim().let { if (it.isNotEmpty()) { args += "--wg-config"; args += it } }
        wgPeer.trim().let { if (it.isNotEmpty()) { args += "--wg-peer"; args += it } }
        return args
    }

    fun validate(): String? {
        AetherValues.socketOrEmpty(h2Peer.trim(), "HTTP/2 peer")?.let { return it }
        AetherValues.socketOrEmpty(wgPeer.trim(), "WireGuard peer")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("h2Peer", h2Peer); put("masqueConfig", masqueConfig)
        put("wgConfig", wgConfig); put("wgPeer", wgPeer)
    }

    companion object {
        fun fromJson(o: JSONObject?) =
            if (o == null) AetherCarrierOptions() else AetherCarrierOptions(
                h2Peer = o.optString("h2Peer", ""),
                masqueConfig = o.optString("masqueConfig", ""),
                wgConfig = o.optString("wgConfig", ""),
                wgPeer = o.optString("wgPeer", "")
            )
    }
}

// ---------------------------------------------------------------------------
// gool (--gool, --gool-classic, --gool-peer, --gool-inner/-outer/-peers/-scan)
// ---------------------------------------------------------------------------

/**
 * Gool: an inner WireGuard WARP tunnel carried inside an outer MASQUE tunnel.
 *
 * `--gool` and `--wiw` are the same protocol (`cli.rs:557`), and the inner/outer
 * peer flags are aliases that all write the `AETHER_WIW_*` variables
 * (`cli.rs:544-552`), while `--gool-peer` also selects the protocol and writes
 * `AETHER_GOOL_INNER` (`cli.rs:558-561`). `--gool-classic` picks the classic mode
 * (`cli.rs:563-566`).
 */
data class AetherGoolOptions(
    /** Classic mode instead of the default: `--gool-classic` (`cli.rs:563`). */
    val classic: Boolean = false,
    /** Inner peer, also selects gool: `--gool-peer` (`cli.rs:558`). */
    val peer: String = "",
    /** `--gool-inner` / `--inner-peer` (`cli.rs:547`). */
    val innerPeer: String = "",
    /** `--gool-outer` / `--outer-peer` (`cli.rs:544`). */
    val outerPeer: String = "",
    /** Explicit peer list: `--gool-peers` (`cli.rs:550`). */
    val peers: String = "",
    /** Let the core scan for peers: `--gool-scan` (`cli.rs:551`). */
    val scanPeers: Boolean = false
) {
    fun isDefault() = this == AetherGoolOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        if (classic) args += "--gool-classic"
        peer.trim().let { if (it.isNotEmpty()) { args += "--gool-peer"; args += it } }
        innerPeer.trim().let { if (it.isNotEmpty()) { args += "--gool-inner"; args += it } }
        outerPeer.trim().let { if (it.isNotEmpty()) { args += "--gool-outer"; args += it } }
        // --gool-peers and --gool-scan write the same variable, so exactly one is
        // emitted: the scan form wins, because asking for a scan and naming peers at
        // the same time would silently drop one of them.
        if (scanPeers) {
            args += "--gool-scan"
        } else {
            peers.trim().let { if (it.isNotEmpty()) { args += "--gool-peers"; args += it } }
        }
        return args
    }

    fun validate(): String? {
        if (scanPeers && peers.isNotBlank()) {
            return "Gool peers: choose either an explicit peer list or an automatic scan, not both"
        }
        AetherValues.socketOrEmpty(peer.trim(), "Gool peer")?.let { return it }
        AetherValues.socketOrEmpty(innerPeer.trim(), "Gool inner peer")?.let { return it }
        AetherValues.socketOrEmpty(outerPeer.trim(), "Gool outer peer")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("classic", classic); put("peer", peer); put("innerPeer", innerPeer)
        put("outerPeer", outerPeer); put("peers", peers); put("scanPeers", scanPeers)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherGoolOptions() else AetherGoolOptions(
            classic = o.optBoolean("classic", false),
            peer = o.optString("peer", ""),
            innerPeer = o.optString("innerPeer", ""),
            outerPeer = o.optString("outerPeer", ""),
            peers = o.optString("peers", ""),
            scanPeers = o.optBoolean("scanPeers", false)
        )
    }
}

// ---------------------------------------------------------------------------
// mim (--mim, --mim-inner/-outer/-peers/-scan)
// ---------------------------------------------------------------------------

/** MASQUE-in-MASQUE: `--mim` and `--masque-in-masque` are aliases (`cli.rs:567`). */
data class AetherMimOptions(
    val innerPeer: String = "",
    val outerPeer: String = "",
    val peers: String = "",
    val scanPeers: Boolean = false
) {
    fun isDefault() = this == AetherMimOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        innerPeer.trim().let { if (it.isNotEmpty()) { args += "--mim-inner"; args += it } }
        outerPeer.trim().let { if (it.isNotEmpty()) { args += "--mim-outer"; args += it } }
        // Same variable for both forms (cli.rs:570-571), so only one is emitted.
        if (scanPeers) {
            args += "--mim-scan"
        } else {
            peers.trim().let { if (it.isNotEmpty()) { args += "--mim-peers"; args += it } }
        }
        return args
    }

    fun validate(): String? {
        if (scanPeers && peers.isNotBlank()) {
            return "MASQUE-in-MASQUE peers: choose either an explicit peer list or an automatic scan, not both"
        }
        AetherValues.socketOrEmpty(innerPeer.trim(), "MASQUE-in-MASQUE inner peer")?.let { return it }
        AetherValues.socketOrEmpty(outerPeer.trim(), "MASQUE-in-MASQUE outer peer")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("innerPeer", innerPeer); put("outerPeer", outerPeer)
        put("peers", peers); put("scanPeers", scanPeers)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherMimOptions() else AetherMimOptions(
            innerPeer = o.optString("innerPeer", ""),
            outerPeer = o.optString("outerPeer", ""),
            peers = o.optString("peers", ""),
            scanPeers = o.optBoolean("scanPeers", false)
        )
    }
}

// ---------------------------------------------------------------------------
// tor (--tor, --tor-reverse, --tor-only, bridges, relays, PT, listeners)
// ---------------------------------------------------------------------------

/** How Tor is layered on top of the selected protocol (`cli.rs:497-499`). */
object AetherTorMode {
    const val OFF = "off"
    const val CHAIN = "chain"
    const val REVERSE = "reverse"
    const val ONLY = "only"
    val IDS = listOf(OFF, CHAIN, REVERSE, ONLY)
}

/** Bridge sourcing (`cli.rs:500-504`). */
object AetherTorBridges {
    /** Emit nothing: the engine's own default. */
    const val DEFAULT = "default"
    /** `--tor-bridges` → `AETHER_TOR_BRIDGES=auto`, fetched via onionoo. */
    const val AUTO = "auto"
    /** `--no-tor-bridges` → `AETHER_TOR_BRIDGES=off`. */
    const val OFF = "off"
    /** One or more `--tor-bridge <line>` entries, appended (`cli.rs:652`). */
    const val CUSTOM = "custom"
    val IDS = listOf(DEFAULT, AUTO, OFF, CUSTOM)
}

/**
 * Tor. Plain relays are in-process; **bridges need a pluggable-transport helper**
 * (`bridges.rs:301` spawns it with the `TOR_PT_*` handshake), which is why
 * [AetherTorOptions.needsHelperBinary] exists.
 */
data class AetherTorOptions(
    val mode: String = AetherTorMode.OFF,
    val bridges: String = AetherTorBridges.DEFAULT,
    val bridgeLines: List<String> = emptyList(),
    val bridgeFile: String = "",
    /** Onionoo relay fetch: `--tor-relays` (`cli.rs:503`). */
    val relays: String = "",
    val relayPorts: String = "",
    /** Pluggable transports, appended: `--tor-pt` (`cli.rs:505`). */
    val pt: List<String> = emptyList(),
    val ptDir: List<String> = emptyList(),
    val bind: String = "",
    val http: String = "",
    val dir: String = ""
) {
    fun isDefault() = this == AetherTorOptions()

    fun isEnabled(): Boolean = mode != AetherTorMode.OFF

    /**
     * True when these options need a pluggable-transport executable on disk.
     *
     * Evidence, not inference: `AETHER_TOR_BRIDGES=auto` selects from the embedded
     * bridge list in `bridges.rs:19-33`, which is snowflake and obfs4 lines, and
     * `bridges.rs:38-46` maps the `lyrebird` binary onto
     * obfs4/snowflake/webtunnel/meek_lite/obfs3/scramblesuit. A custom list needs a
     * helper only when one of its lines names such a transport; a plain
     * `bridge host:port` line does not. A bridge *file*'s contents are unknown here,
     * so it is treated as needing one.
     */
    fun needsHelperBinary(): Boolean {
        if (pt.isNotEmpty() || ptDir.isNotEmpty()) return true
        // A bridge file is handed to the engine whatever the bridge mode is
        // (`toArgs` emits --tor-bridge-file on its own), and its contents are unknown
        // here, so it is treated as needing one. Reporting "no helper needed" while
        // still sending the file would let a start through that then fails inside Tor.
        if (bridgeFile.isNotBlank()) return true
        if (bridges == AetherTorBridges.AUTO) return true
        if (bridges == AetherTorBridges.CUSTOM) {
            return bridgeLines.any { line -> line.trim().substringBefore(' ') in PLUGGABLE_TRANSPORTS }
        }
        return false
    }

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        when (mode) {
            AetherTorMode.CHAIN -> args += "--tor"
            AetherTorMode.REVERSE -> args += "--tor-reverse"
            AetherTorMode.ONLY -> args += "--tor-only"
        }
        when (bridges) {
            AetherTorBridges.AUTO -> args += "--tor-bridges"
            AetherTorBridges.OFF -> args += "--no-tor-bridges"
            AetherTorBridges.CUSTOM -> bridgeLines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) { args += "--tor-bridge"; args += trimmed }
            }
        }
        bridgeFile.trim().let { if (it.isNotEmpty()) { args += "--tor-bridge-file"; args += it } }
        relays.trim().let { if (it.isNotEmpty()) { args += "--tor-relays"; args += it } }
        relayPorts.trim().let { if (it.isNotEmpty()) { args += "--tor-relay-ports"; args += it } }
        pt.forEach { name ->
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) { args += "--tor-pt"; args += trimmed }
        }
        ptDir.forEach { path ->
            val trimmed = path.trim()
            if (trimmed.isNotEmpty()) { args += "--tor-pt-dir"; args += trimmed }
        }
        bind.trim().let { if (it.isNotEmpty()) { args += "--tor-bind"; args += it } }
        http.trim().let { if (it.isNotEmpty()) { args += "--tor-http"; args += it } }
        dir.trim().let { if (it.isNotEmpty()) { args += "--tor-dir"; args += it } }
        return args
    }

    fun validate(): String? {
        if (mode !in AetherTorMode.IDS) return "Tor mode must be one of ${AetherTorMode.IDS.joinToString(", ")}"
        if (bridges !in AetherTorBridges.IDS) {
            return "Tor bridges must be one of ${AetherTorBridges.IDS.joinToString(", ")}"
        }
        if (bridges != AetherTorBridges.CUSTOM && bridgeLines.isNotEmpty()) {
            return "Tor bridge lines are only used when bridges are set to a custom list"
        }
        if (bridges == AetherTorBridges.CUSTOM && bridgeLines.all { it.isBlank() } && bridgeFile.isBlank()) {
            return "Tor bridges are set to a custom list but no bridge line or bridge file was given"
        }
        AetherValues.positiveIntOrEmpty(relays.trim(), "Tor relays")?.let { return it }
        AetherValues.portListOrEmpty(relayPorts.trim(), "Tor relay ports")?.let { return it }
        AetherValues.loopbackOrEmpty(bind.trim(), "Tor SOCKS bind")?.let { return it }
        AetherValues.loopbackOrEmpty(http.trim(), "Tor HTTP proxy bind")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("mode", mode); put("bridges", bridges)
        put("bridgeLines", JSONArray(bridgeLines)); put("bridgeFile", bridgeFile)
        put("relays", relays); put("relayPorts", relayPorts)
        put("pt", JSONArray(pt)); put("ptDir", JSONArray(ptDir))
        put("bind", bind); put("http", http); put("dir", dir)
    }

    companion object {
        /**
         * Transport names that need a helper binary, from `bridges.rs:38-46`
         * (`lyrebird` serves all of them) and the preference order at `bridges.rs:16`.
         */
        val PLUGGABLE_TRANSPORTS = listOf(
            "obfs4", "obfs3", "snowflake", "webtunnel", "meek_lite", "meek", "scramblesuit"
        )

        fun fromJson(o: JSONObject?) = if (o == null) AetherTorOptions() else AetherTorOptions(
            mode = o.optString("mode", AetherTorMode.OFF),
            bridges = o.optString("bridges", AetherTorBridges.DEFAULT),
            bridgeLines = o.optJSONArray("bridgeLines").toStringList(),
            bridgeFile = o.optString("bridgeFile", ""),
            relays = o.optString("relays", ""),
            relayPorts = o.optString("relayPorts", ""),
            pt = o.optJSONArray("pt").toStringList(),
            ptDir = o.optJSONArray("ptDir").toStringList(),
            bind = o.optString("bind", ""),
            http = o.optString("http", ""),
            dir = o.optString("dir", "")
        )
    }
}

// ---------------------------------------------------------------------------
// psiphon (--psiphon, -reverse, -only, region, mode, cdn, config, listeners)
// ---------------------------------------------------------------------------

/** How Psiphon is layered (`cli.rs:514-516`). */
object AetherPsiphonMode {
    const val OFF = "off"
    const val CHAIN = "chain"
    const val REVERSE = "reverse"
    const val ONLY = "only"
    val IDS = listOf(OFF, CHAIN, REVERSE, ONLY)
}

/**
 * Psiphon. Every mode needs the `psiphon-tunnel-core` helper: `psiphon.rs:749`
 * spawns it with piped stdout/stderr and `kill_on_drop(true)`, and passes the
 * Android CA store it finds on its own (`psiphon.rs:756-759`).
 */
data class AetherPsiphonOptions(
    val mode: String = AetherPsiphonMode.OFF,
    val region: String = "",
    /** CDN fronting mode: `--psiphon-mode` (`cli.rs:522`). */
    val cdnMode: String = "",
    val cdnIps: String = "",
    val cdnSni: String = "",
    val cdnSets: String = "",
    val config: String = "",
    val serverEntries: String = "",
    val bind: String = "",
    val http: String = "",
    val dir: String = "",
    /** Path to `psiphon-tunnel-core`: `--psiphon-bin` (`cli.rs:527`). */
    val bin: String = ""
) {
    fun isDefault() = this == AetherPsiphonOptions()

    fun isEnabled(): Boolean = mode != AetherPsiphonMode.OFF

    /** Psiphon always needs its helper executable (`psiphon.rs:749`). */
    fun needsHelperBinary(): Boolean = isEnabled()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        when (mode) {
            AetherPsiphonMode.CHAIN -> args += "--psiphon"
            AetherPsiphonMode.REVERSE -> args += "--psiphon-reverse"
            AetherPsiphonMode.ONLY -> args += "--psiphon-only"
        }
        region.trim().let { if (it.isNotEmpty()) { args += "--psiphon-region"; args += it } }
        cdnMode.trim().let { if (it.isNotEmpty()) { args += "--psiphon-mode"; args += it } }
        cdnIps.trim().let { if (it.isNotEmpty()) { args += "--psiphon-cdn-ips"; args += it } }
        cdnSni.trim().let { if (it.isNotEmpty()) { args += "--psiphon-cdn-sni"; args += it } }
        cdnSets.trim().let { if (it.isNotEmpty()) { args += "--psiphon-cdn-sets"; args += it } }
        config.trim().let { if (it.isNotEmpty()) { args += "--psiphon-config"; args += it } }
        serverEntries.trim().let { if (it.isNotEmpty()) { args += "--psiphon-server-entries"; args += it } }
        bind.trim().let { if (it.isNotEmpty()) { args += "--psiphon-bind"; args += it } }
        http.trim().let { if (it.isNotEmpty()) { args += "--psiphon-http"; args += it } }
        dir.trim().let { if (it.isNotEmpty()) { args += "--psiphon-dir"; args += it } }
        bin.trim().let { if (it.isNotEmpty()) { args += "--psiphon-bin"; args += it } }
        return args
    }

    fun validate(): String? {
        if (mode !in AetherPsiphonMode.IDS) {
            return "Psiphon mode must be one of ${AetherPsiphonMode.IDS.joinToString(", ")}"
        }
        // Two-letter country codes are what the core forwards to Psiphon; anything
        // else is reported here rather than failing inside the helper.
        val regionTrimmed = region.trim()
        if (regionTrimmed.isNotEmpty() &&
            (regionTrimmed.length != 2 || !regionTrimmed.all { it.isLetter() })
        ) {
            return "Psiphon region must be a two-letter country code (for example NL), or empty for automatic"
        }
        AetherValues.loopbackOrEmpty(bind.trim(), "Psiphon SOCKS bind")?.let { return it }
        AetherValues.loopbackOrEmpty(http.trim(), "Psiphon HTTP proxy bind")?.let { return it }
        AetherValues.ipListOrEmpty(cdnIps.trim(), "Psiphon CDN IPs")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("mode", mode); put("region", region); put("cdnMode", cdnMode)
        put("cdnIps", cdnIps); put("cdnSni", cdnSni); put("cdnSets", cdnSets)
        put("config", config); put("serverEntries", serverEntries)
        put("bind", bind); put("http", http); put("dir", dir); put("bin", bin)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherPsiphonOptions() else AetherPsiphonOptions(
            mode = o.optString("mode", AetherPsiphonMode.OFF),
            region = o.optString("region", ""),
            cdnMode = o.optString("cdnMode", ""),
            cdnIps = o.optString("cdnIps", ""),
            cdnSni = o.optString("cdnSni", ""),
            cdnSets = o.optString("cdnSets", ""),
            config = o.optString("config", ""),
            serverEntries = o.optString("serverEntries", ""),
            bind = o.optString("bind", ""),
            http = o.optString("http", ""),
            dir = o.optString("dir", ""),
            bin = o.optString("bin", "")
        )
    }
}

// ---------------------------------------------------------------------------
// exit location, stats
// ---------------------------------------------------------------------------

/**
 * Exit-location filtering and its background verification
 * (`cli.rs:531-532` → `AETHER_EXIT_LOC`, `AETHER_EXIT_LOC_SECS`).
 */
data class AetherExitLocationOptions(
    val filter: String = "",
    val verificationSecs: String = ""
) {
    fun isDefault() = this == AetherExitLocationOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        filter.trim().let { if (it.isNotEmpty()) { args += "--exit-loc"; args += it } }
        verificationSecs.trim().let { if (it.isNotEmpty()) { args += "--exit-loc-secs"; args += it } }
        return args
    }

    fun validate(): String? {
        AetherValues.positiveIntOrEmpty(verificationSecs.trim(), "Exit-location verification seconds")
            ?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("filter", filter); put("verificationSecs", verificationSecs)
    }

    companion object {
        fun fromJson(o: JSONObject?) =
            if (o == null) AetherExitLocationOptions() else AetherExitLocationOptions(
                filter = o.optString("filter", ""),
                verificationSecs = o.optString("verificationSecs", "")
            )
    }
}

/** Periodic stats reporting (`cli.rs:533-534` → `AETHER_STATS`, `AETHER_STATS_SECS`). */
data class AetherStatsOptions(
    val enabled: Boolean = false,
    val intervalSecs: String = ""
) {
    fun isDefault() = this == AetherStatsOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        if (enabled) args += "--stats"
        intervalSecs.trim().let { if (it.isNotEmpty()) { args += "--stats-secs"; args += it } }
        return args
    }

    fun validate(): String? =
        AetherValues.positiveIntOrEmpty(intervalSecs.trim(), "Stats interval seconds")

    fun toJson() = JSONObject().apply {
        put("enabled", enabled); put("intervalSecs", intervalSecs)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherStatsOptions() else AetherStatsOptions(
            enabled = o.optBoolean("enabled", false),
            intervalSecs = o.optString("intervalSecs", "")
        )
    }
}

// ---------------------------------------------------------------------------
// tls / fingerprint
// ---------------------------------------------------------------------------

/**
 * TLS and fingerprint knobs (`cli.rs:626-631`).
 *
 * Read `tls.rs:239-299` before changing any default here: `verify_enabled()` is
 * **false unless `AETHER_TLS_VERIFY` is set**, in which case the core installs
 * SPKI pinning; with the variable unset the engine runs `SslVerifyMode::NONE` and
 * announces "tls verification: disabled (default; --tls-verify enables pinning)".
 * So [verify] can only ever *add* a certificate check - it never removes one - and
 * it stays off by default because that is the engine's own default and turning it on
 * without pins configured changes nothing but the log line (`tls.rs:291-297`).
 *
 * [ciphers], [groups] and [disableGrease] alter the ClientHello fingerprint. They are
 * exposed because the core exposes them, and they are left unset by default so the
 * engine's own anti-DPI profile is what runs.
 */
data class AetherTlsOptions(
    val verify: Boolean = false,
    val ciphers: String = "",
    val groups: String = "",
    val disableGrease: Boolean = false,
    val noQuicV2: Boolean = false
) {
    fun isDefault() = this == AetherTlsOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        if (verify) args += "--tls-verify"
        ciphers.trim().let { if (it.isNotEmpty()) { args += "--tls-ciphers"; args += it } }
        groups.trim().let { if (it.isNotEmpty()) { args += "--tls-groups"; args += it } }
        if (disableGrease) args += "--disable-grease"
        if (noQuicV2) args += "--no-quic-v2"
        return args
    }

    /**
     * A warning, not a refusal: these change the fingerprint the engine presents, so
     * the log says so instead of letting it happen silently.
     */
    fun caution(): String? {
        val changed = mutableListOf<String>()
        if (ciphers.isNotBlank()) changed += "cipher list"
        if (groups.isNotBlank()) changed += "group list"
        if (disableGrease) changed += "GREASE disabled"
        if (noQuicV2) changed += "QUIC v2 disabled"
        if (changed.isEmpty()) return null
        return "TLS fingerprint overrides in effect: ${changed.joinToString(", ")}. " +
            "These change what the engine presents to the network; leave them empty to use Aether's own profile."
    }

    fun validate(): String? {
        // A cipher or group list is passed straight to BoringSSL, so only the shape is
        // checked here: colon-separated identifiers, no whitespace inside a name.
        listOf("TLS cipher list" to ciphers, "TLS group list" to groups).forEach { (label, value) ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return@forEach
            trimmed.split(':').forEach { part ->
                val name = part.trim()
                if (name.isEmpty()) return "$label must not contain an empty entry"
                if (name.any { it.isWhitespace() }) return "$label entries must not contain spaces"
            }
        }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("verify", verify); put("ciphers", ciphers); put("groups", groups)
        put("disableGrease", disableGrease); put("noQuicV2", noQuicV2)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherTlsOptions() else AetherTlsOptions(
            verify = o.optBoolean("verify", false),
            ciphers = o.optString("ciphers", ""),
            groups = o.optString("groups", ""),
            disableGrease = o.optBoolean("disableGrease", false),
            noQuicV2 = o.optBoolean("noQuicV2", false)
        )
    }
}

// ---------------------------------------------------------------------------
// split routing
// ---------------------------------------------------------------------------

/** Route rules (`cli.rs:619-621` → `AETHER_ROUTES_FILE`, `_ROUTE_BLOCK`, `_ROUTE_DIRECT`). */
data class AetherRoutingOptions(
    val routesFile: String = "",
    val block: String = "",
    val direct: String = ""
) {
    fun isDefault() = this == AetherRoutingOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        routesFile.trim().let { if (it.isNotEmpty()) { args += "--routes"; args += it } }
        block.trim().let { if (it.isNotEmpty()) { args += "--route-block"; args += it } }
        direct.trim().let { if (it.isNotEmpty()) { args += "--route-direct"; args += it } }
        return args
    }

    fun validate(): String? {
        AetherValues.cidrListOrEmpty(block.trim(), "Blocked routes")?.let { return it }
        AetherValues.cidrListOrEmpty(direct.trim(), "Direct routes")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("routesFile", routesFile); put("block", block); put("direct", direct)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherRoutingOptions() else AetherRoutingOptions(
            routesFile = o.optString("routesFile", ""),
            block = o.optString("block", ""),
            direct = o.optString("direct", "")
        )
    }
}

// ---------------------------------------------------------------------------
// timing and kernel mark
// ---------------------------------------------------------------------------

/**
 * Timeouts and keepalives (`cli.rs:586-600`). `--reconnect-secs`, `--validate-secs`
 * and `--no-data-check` each write **two** variables (MASQUE and WireGuard), which is
 * why they are single fields rather than per-protocol ones.
 */
data class AetherTimingOptions(
    val startupSecs: String = "",
    val reconnectSecs: String = "",
    val validateSecs: String = "",
    val keepaliveSecs: String = "",
    val noDataCheck: Boolean = false,
    val noProfileRetry: Boolean = false,
    /** Firewall mark for the engine's own sockets: `--mark` (`cli.rs:530`). */
    val mark: String = ""
) {
    fun isDefault() = this == AetherTimingOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        startupSecs.trim().let { if (it.isNotEmpty()) { args += "--startup-secs"; args += it } }
        reconnectSecs.trim().let { if (it.isNotEmpty()) { args += "--reconnect-secs"; args += it } }
        validateSecs.trim().let { if (it.isNotEmpty()) { args += "--validate-secs"; args += it } }
        keepaliveSecs.trim().let { if (it.isNotEmpty()) { args += "--keepalive"; args += it } }
        if (noDataCheck) args += "--no-data-check"
        if (noProfileRetry) args += "--no-profile-retry"
        mark.trim().let { if (it.isNotEmpty()) { args += "--mark"; args += it } }
        return args
    }

    fun validate(): String? {
        AetherValues.positiveIntOrEmpty(startupSecs.trim(), "Startup seconds")?.let { return it }
        AetherValues.positiveIntOrEmpty(reconnectSecs.trim(), "Reconnect seconds")?.let { return it }
        AetherValues.positiveIntOrEmpty(validateSecs.trim(), "Validate seconds")?.let { return it }
        AetherValues.positiveIntOrEmpty(keepaliveSecs.trim(), "Keepalive seconds")?.let { return it }
        AetherValues.wholeNumberOrEmpty(mark.trim(), "Socket mark")?.let { return it }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("startupSecs", startupSecs); put("reconnectSecs", reconnectSecs)
        put("validateSecs", validateSecs); put("keepaliveSecs", keepaliveSecs)
        put("noDataCheck", noDataCheck); put("noProfileRetry", noProfileRetry)
        put("mark", mark)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherTimingOptions() else AetherTimingOptions(
            startupSecs = o.optString("startupSecs", ""),
            reconnectSecs = o.optString("reconnectSecs", ""),
            validateSecs = o.optString("validateSecs", ""),
            keepaliveSecs = o.optString("keepaliveSecs", ""),
            noDataCheck = o.optBoolean("noDataCheck", false),
            noProfileRetry = o.optBoolean("noProfileRetry", false),
            mark = o.optString("mark", "")
        )
    }
}

// ---------------------------------------------------------------------------
// api, registration, zero trust
// ---------------------------------------------------------------------------

/**
 * API and account options (`cli.rs:611-618`).
 *
 * [accessId], [accessSecret], [accessToken] and [accessEmail] are Zero Trust
 * credentials. They are stored with the route like every other secret in this model
 * and are redacted before they reach the log by `AetherLogSanitizer`.
 */
data class AetherApiOptions(
    /** Reach the WARP API only over the fragmented route: `--api-fragment` (`cli.rs:562`). */
    val fragment: Boolean = false,
    val enrollAddress: String = "",
    val register: String = "",
    val team: String = "",
    val accessId: String = "",
    val accessSecret: String = "",
    val accessToken: String = "",
    val accessEmail: String = ""
) {
    fun isDefault() = this == AetherApiOptions()

    /** True when any Zero Trust credential is present, for redaction decisions. */
    fun hasSecrets(): Boolean =
        accessId.isNotBlank() || accessSecret.isNotBlank() ||
            accessToken.isNotBlank() || accessEmail.isNotBlank()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        if (fragment) args += "--api-fragment"
        enrollAddress.trim().let { if (it.isNotEmpty()) { args += "--enroll-address"; args += it } }
        register.trim().let { if (it.isNotEmpty()) { args += "--register"; args += it } }
        team.trim().let { if (it.isNotEmpty()) { args += "--team"; args += it } }
        accessId.trim().let { if (it.isNotEmpty()) { args += "--access-id"; args += it } }
        accessSecret.trim().let { if (it.isNotEmpty()) { args += "--access-secret"; args += it } }
        accessToken.trim().let { if (it.isNotEmpty()) { args += "--access-token"; args += it } }
        accessEmail.trim().let { if (it.isNotEmpty()) { args += "--access-email"; args += it } }
        return args
    }

    fun validate(): String? {
        AetherValues.hostOrSocketOrEmpty(enrollAddress.trim(), "Enroll address")?.let { return it }
        if (accessEmail.isNotBlank() && !accessEmail.contains("@")) {
            return "Zero Trust access email must look like an email address"
        }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("fragment", fragment); put("enrollAddress", enrollAddress)
        put("register", register); put("team", team); put("accessId", accessId)
        put("accessSecret", accessSecret); put("accessToken", accessToken)
        put("accessEmail", accessEmail)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherApiOptions() else AetherApiOptions(
            fragment = o.optBoolean("fragment", false),
            enrollAddress = o.optString("enrollAddress", ""),
            register = o.optString("register", ""),
            team = o.optString("team", ""),
            accessId = o.optString("accessId", ""),
            accessSecret = o.optString("accessSecret", ""),
            accessToken = o.optString("accessToken", ""),
            accessEmail = o.optString("accessEmail", "")
        )
    }
}

// ---------------------------------------------------------------------------
// logging
// ---------------------------------------------------------------------------

/** Engine log level (`cli.rs:632-634`); `--verbose` is `debug`. */
object AetherLogLevel {
    /** Emit nothing: `run_with` defaults to info (`lib.rs:82-91`). */
    const val DEFAULT = ""
    val IDS = listOf("", "error", "warn", "info", "debug", "trace")
}

data class AetherLoggingOptions(
    val level: String = AetherLogLevel.DEFAULT,
    val verbose: Boolean = false,
    val perfProfile: String = ""
) {
    fun isDefault() = this == AetherLoggingOptions()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()
        // --verbose is just log-level debug (cli.rs:634), so only one is emitted.
        if (verbose) {
            args += "--verbose"
        } else {
            level.trim().let { if (it.isNotEmpty()) { args += "--log-level"; args += it } }
        }
        perfProfile.trim().let { if (it.isNotEmpty()) { args += "--perf"; args += it } }
        return args
    }

    fun validate(): String? {
        val trimmed = level.trim()
        if (trimmed.isNotEmpty() && trimmed !in AetherLogLevel.IDS) {
            return "Log level must be one of error, warn, info, debug or trace"
        }
        return null
    }

    fun toJson() = JSONObject().apply {
        put("level", level); put("verbose", verbose); put("perfProfile", perfProfile)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) AetherLoggingOptions() else AetherLoggingOptions(
            level = o.optString("level", ""),
            verbose = o.optBoolean("verbose", false),
            perfProfile = o.optString("perfProfile", "")
        )
    }
}

// ---------------------------------------------------------------------------
// gateway
// ---------------------------------------------------------------------------

/**
 * Run this device as an Aether gateway for other clients (`--gateway`,
 * `cli.rs:618` → `AETHER_GATEWAY=1`).
 *
 * Off by default and deliberately separate from the route's own connection: it makes
 * the device a server, which is a different product decision from connecting out.
 */
data class AetherGatewayOptions(
    val enabled: Boolean = false
) {
    fun isDefault() = this == AetherGatewayOptions()

    fun toArgs(): List<String> = if (enabled) listOf("--gateway") else emptyList()

    fun validate(): String? = null

    fun toJson() = JSONObject().apply { put("enabled", enabled) }

    companion object {
        fun fromJson(o: JSONObject?) =
            if (o == null) AetherGatewayOptions() else AetherGatewayOptions(
                enabled = o.optBoolean("enabled", false)
            )
    }
}

// ---------------------------------------------------------------------------
// shared value checks
// ---------------------------------------------------------------------------

/**
 * Value checks for the option fields, kept in one place so every family reports the
 * same way and the same rule is never written twice.
 *
 * Each returns null when the value is empty, because empty means "leave the engine's
 * default alone" throughout this model.
 */
internal object AetherValues {

    fun positiveIntOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        val number = value.toLongOrNull()
        if (number == null || number <= 0) return "$label must be a whole number of seconds greater than zero"
        return null
    }

    fun wholeNumberOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        if (value.toLongOrNull() == null) return "$label must be a whole number"
        return null
    }

    /** `host:port`, `[ipv6]:port` or a bare host, or empty. */
    fun socketOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        if (value.any { it.isWhitespace() }) return "$label must not contain spaces"
        if (value.count { it == ':' } > 1 && !value.startsWith("[")) {
            return "$label must bracket an IPv6 literal: [2001:db8::1]:443"
        }
        // Everything after the last colon is the port, if there was one at all.
        val port = value.substringAfterLast(':', "")
        if (port.isNotEmpty()) {
            val number = port.toIntOrNull()
            if (number == null || number !in 1..65535) return "$label must use a port in 1-65535"
        }
        return null
    }

    fun hostOrSocketOrEmpty(value: String, label: String): String? = socketOrEmpty(value, label)

    /** Loopback listener only, matching the rule `AetherConfig.validate` already applies. */
    fun loopbackOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        if (!value.startsWith("127.0.0.1:") && !value.startsWith("localhost:") &&
            !value.startsWith("[::1]:")
        ) {
            return "$label must be loopback (127.0.0.1:port) for safety"
        }
        val port = value.substringAfterLast(':').removeSuffix("]")
        val number = port.toIntOrNull()
        if (number == null || number !in 1024..65535) return "$label must use a port in 1024-65535"
        return null
    }

    fun portListOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        value.split(',', ';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return "$label must not contain an empty entry"
            val range = trimmed.split('-')
            if (range.size > 2) return "$label entries must be a port or a from-to range"
            range.forEach { end ->
                val number = end.trim().toIntOrNull()
                if (number == null || number !in 1..65535) return "$label must use ports in 1-65535"
            }
        }
        return null
    }

    fun ipListOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        value.split(',', ';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return "$label must not contain an empty entry"
            if (trimmed.any { it.isWhitespace() }) return "$label entries must not contain spaces"
            val groups = trimmed.split('.')
            if (groups.size == 4) {
                groups.forEach { group ->
                    val number = group.toIntOrNull()
                    if (number == null || number !in 0..255) return "$label must hold dotted-quad addresses"
                }
            } else if (!trimmed.contains(':')) {
                return "$label must hold IP addresses, not names"
            }
        }
        return null
    }

    fun cidrListOrEmpty(value: String, label: String): String? {
        if (value.isEmpty()) return null
        value.split(',', ';', ' ').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val address = trimmed.substringBefore('/')
            val prefix = trimmed.substringAfter('/', "")
            if (prefix.isNotEmpty()) {
                val number = prefix.toIntOrNull()
                if (number == null || number !in 0..128) return "$label must use a prefix length in 0-128"
            }
            if (address.split('.').size == 4) {
                address.split('.').forEach { group ->
                    val number = group.toIntOrNull()
                    if (number == null || number !in 0..255) return "$label must hold dotted-quad addresses"
                }
            } else if (!address.contains(':')) {
                return "$label must hold IP networks, not names"
            }
        }
        return null
    }
}

/** `JSONArray` → `List<String>`, tolerating a missing array. */
internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }
}
