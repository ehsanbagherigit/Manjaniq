package com.manjaniq.core.models

import com.manjaniq.core.AetherHelpers
import org.json.JSONObject

/**
 * User-editable Aether configuration persisted via AetherConfigStore.
 * All fields map to Aether CLI env/flags or FFI payloads.
 *
 * Privacy: never log token, private keys, or team credentials.
 */
data class AetherConfig(
    // Identity storage path (relative to app files dir)
    val identityPath: String = "aether_identity",
    // Transport: masque (default), wg, gool, mim
    val transport: String = "masque",
    // Scan profile
    val scanMode: String = "balanced",
    // Noize profile: off, light, firewall (default masque), balanced (wg default), gfw, aggressive
    val noizeProfile: String = "firewall",
    // SOCKS bind address - must stay loopback per safety rule
    val socksBind: String = "127.0.0.1:1819",
    // HTTP proxy bind (optional, off by default)
    val httpProxyBind: String = "",
    // Peer override (empty = scan)
    val peer: String = "",
    // Upstream proxy (e.g. socks5://127.0.0.1:1080)
    val upstream: String = "",
    // ECH enabled
    val echEnabled: Boolean = false,
    // DNS list for inside tunnel
    val dns: String = "1.1.1.1,1.0.0.1",
    // IP version: v4 (default), v6, both
    val ipVersion: String = "v4",
    // Optional team credentials (ZeroTrust) - stored encrypted if present
    val teamName: String = "",
    // Quick reconnect
    val quickReconnect: Boolean = true,
    /**
     * TLS ClientHello fragmenting (Aether `aether/src/fragment.rs`).
     *
     * Aether applies it on the MASQUE-over-HTTP/2 carrier only, and reads it
     * exclusively from the process environment / CLI flags. The values are
     * persisted with the route and handed to the engine on every start.
     */
    val fragmentEnabled: Boolean = AetherFragmenting.DEFAULT_ENABLED,
    /** Chunk size in bytes: `n` or `a-b` (documented Aether default `16-32`). */
    val fragmentSize: String = AetherFragmenting.DEFAULT_SIZE,
    /** Delay between fragments in ms: `n` or `a-b` (Aether default `2-10`). */
    val fragmentDelay: String = AetherFragmenting.DEFAULT_DELAY,
    /**
     * Split inside the SNI host name instead of at a random offset
     * (`AETHER_MASQUE_H2_FRAGMENT_SNI`). Upstream has no `--fragment-sni` flag, so
     * [toAetherArgs] cannot express it; it reaches the engine through
     * `AetherBridge.fragmentingApply`, which writes the variable directly.
     */
    val fragmentSniSplit: Boolean = AetherFragmenting.DEFAULT_SNI_SPLIT,
    /**
     * Everything else the core accepts, modelled against `aether/src/cli.rs`.
     *
     * One field rather than a hundred: the families are grouped, each renders its own
     * flags, and a default renders nothing at all so the engine keeps its own value.
     * See [AetherEngineOptions].
     */
    val engine: AetherEngineOptions = AetherEngineOptions()
) {
    fun validate(): String? {
        // socksBind must be loopback
        val socks = socksBind.trim()
        if (!socks.startsWith("127.0.0.1:") && !socks.startsWith("localhost:") && socks.isNotEmpty()) {
            return "SOCKS must be loopback (127.0.0.1:port) for safety"
        }
        // Port conflict check done by caller vs RSTA
        if (socks.isNotEmpty()) {
            val port = socks.substringAfterLast(":").toIntOrNull()
            if (port == null || port !in 1024..65535) return "Invalid SOCKS port"
        }
        // Trimmed like socksBind above: an all-blank field means "no second
        // listener", not "a listener named ' '". AetherService forwards null in
        // that case, so validation must agree instead of rejecting it.
        val http = httpProxyBind.trim()
        if (http.isNotEmpty()) {
            if (!http.startsWith("127.0.0.1:") && !http.startsWith("localhost:")) {
                return "HTTP proxy must be loopback if enabled"
            }
            val p = http.substringAfterLast(":").toIntOrNull()
            if (p == null || p !in 1024..65535) return "Invalid HTTP proxy port"
        }
        if (socks.isNotEmpty() && http.isNotEmpty()) {
            val socksPort = socks.substringAfterLast(":").toIntOrNull()
            val httpPort = http.substringAfterLast(":").toIntOrNull()
            if (socksPort != null && socksPort == httpPort) {
                return "SOCKS and HTTP proxy must listen on different ports"
            }
        }
        // Every protocol in AetherTransportChoice is a real `cli.rs` protocol,
        // and the engine is always started through that command line, so each one
        // is accepted here.
        if (transport !in AetherTransportChoice.IDS) return "Invalid transport"
        if (scanMode !in AetherScanChoice.IDS) return "Invalid scan mode"
        // Empty means "let Aether pick its own default" and was accepted before
        // these two were checked at all; only a value Aether cannot parse is an error.
        if (noizeProfile.isNotEmpty() && noizeProfile !in AetherNoizeChoice.IDS) {
            return "Invalid obfuscation profile"
        }
        if (ipVersion.isNotEmpty() && ipVersion !in AetherIpChoice.IDS) return "Invalid IP version"
        AetherFragmenting.validateRange(fragmentSize, AetherFragmenting.MAX_SIZE_BYTES.toLong())
            ?.let { return "Fragment size: $it" }
        AetherFragmenting.validateRange(fragmentDelay, AetherFragmenting.MAX_DELAY_MS.toLong())
            ?.let { return "Fragment delay: $it" }
        // Fragmenting is not a per-transport lock: the values are stored with the
        // route and always put on the command line. fragment.rs has exactly one
        // consumer, masque_h2.rs, so Aether writes them only on the MASQUE-over-HTTP/2
        // carrier; a WireGuard route therefore carries the setting without using it,
        // and the screen says so instead of refusing the start.
        engine.validate()?.let { return it }
        // Psiphon and the Tor bridges each need a helper process. A packaged one
        // is resolved by AetherHelpers in the read-only native library directory
        // (scripts/ci/build_helpers.sh puts it there); without one, the start is
        // refused here rather than failing deep inside the engine with no
        // explanation. Naming a path is always accepted: the check is about a
        // missing binary, not about the feature.
        if (engine.psiphon.isEnabled() && engine.psiphon.bin.isBlank() && !AetherHelpers.hasPsiphon()) {
            return "Psiphon needs the ${AetherEngineOptions.HELPER_PSIPHON} executable and this " +
                "build does not ship it: set its path, or ship it as a native library (see " +
                "AETHER_CAPABILITIES.md §6)"
        }
        if (engine.tor.needsHelperBinary() &&
            engine.tor.pt.isEmpty() &&
            engine.tor.ptDir.isEmpty() &&
            !AetherHelpers.hasPluggableTransport()
        ) {
            return "These Tor bridges need a pluggable-transport executable such as " +
                "${AetherEngineOptions.HELPER_TOR_PT} and this build does not ship it: name one " +
                "with the PT or PT directory setting, or ship it as a native library (see " +
                "AETHER_CAPABILITIES.md §6)"
        }
        return null
    }

    /**
     * Whether this configuration asks for the HTTP/2 MASQUE carrier.
     *
     * Fragmenting is written into the ClientHello of that carrier and nowhere else
     * (`fragment.rs` → `masque_h2.rs:239, 284, 402`), so asking for fragments asks for
     * the carrier too. Every protocol that runs over the MASQUE carrier can carry
     * them - `masque`, and the two double-hop protocols, whose hop is MASQUE as well
     * (`--h2` on `--mim` means HTTP/2 in HTTP/2, `cli.rs:577`). WireGuard has no such
     * carrier, so it is untouched when this is false.
     */
    fun usesHttp2Carrier(): Boolean =
        fragmentEnabled && transport != AetherTransportChoice.WIREGUARD.id

    fun toJson(): String = JSONObject().apply {
        put("identityPath", identityPath)
        put("transport", transport)
        put("scanMode", scanMode)
        put("noizeProfile", noizeProfile)
        put("socksBind", socksBind)
        put("httpProxyBind", httpProxyBind)
        put("peer", peer)
        put("upstream", upstream)
        put("echEnabled", echEnabled)
        put("dns", dns)
        put("ipVersion", ipVersion)
        put("teamName", teamName)
        put("quickReconnect", quickReconnect)
        put("fragmentEnabled", fragmentEnabled)
        put("fragmentSize", fragmentSize)
        put("fragmentDelay", fragmentDelay)
        put("fragmentSniSplit", fragmentSniSplit)
        put("engine", engine.toJson())
    }.toString()

    /**
     * The Aether command line for this configuration, for `aether_core_start`
     * (`AetherBridge.coreStart`).
     *
     * This is the only start path the app has, so everything the settings screen
     * can express - gool/mim, the Tor and Psiphon layering, Zero Trust, routing,
     * `--upstream`, `--dns`, `--quick-reconnect`, `--stats`, `--mark` and
     * `--register` - travels through this list.
     *
     * Fragmenting also reaches the engine through
     * [AetherBridge.fragmentingApply] (`AetherService.applyFragmenting`), which
     * writes the SNI-split variable Aether reads but has no CLI flag for; these
     * flags carry the rest of the same decision so the two never disagree.
     *
     * [helpers] supplies the paths of the packaged child processes, resolved by
     * [com.manjaniq.core.AetherHelpers]. A setting the user left blank is filled
     * from it; a setting the user named always wins.
     */
    fun toAetherArgs(
        contextFilesDir: String,
        helpers: AetherHelperPaths = AetherHelperPaths.none
    ): List<String> {
        val args = mutableListOf<String>()
        // bind is required; Aether defaults to 127.0.0.1:1819 but we set explicitly
        args += "--bind"; args += socksBind.ifEmpty { "127.0.0.1:1819" }
        if (httpProxyBind.trim().isNotEmpty()) {
            args += "--http-proxy"; args += httpProxyBind.trim()
        }
        // protocol
        when (transport) {
            "masque" -> args += "--masque"
            "wg" -> args += "--wg"
            "gool" -> args += "--gool"
            "mim" -> args += "--mim"
        }
        // scan
        args += "--scan"; args += scanMode
        // ip version
        when (ipVersion) {
            "v4" -> args += "-4"
            "v6" -> args += "-6"
            "both" -> args += "--dual"
        }
        // noize: "off" is a real Aether value (--noize off disables it). Dropping
        // the flag instead would leave Aether on its own default (firewall for
        // MASQUE, balanced for WireGuard), which is not what the user picked.
        if (noizeProfile.isNotEmpty()) {
            args += "--noize"; args += noizeProfile
        }
        // peer if set
        if (peer.isNotEmpty()) {
            args += "--peer"; args += peer
        }
        // upstream
        if (upstream.isNotEmpty()) {
            args += "--upstream"; args += upstream
        }
        // ech. `echEnabled` keeps meaning what it always meant - ask for the key over
        // DoH - unless the engine options name one, in which case that more specific
        // setting owns the flag and this stays out of its way.
        if (echEnabled && engine.ech.value.isBlank()) {
            args += "--ech"; args += AetherEchOptions.AUTO
        }
        // dns
        if (dns.isNotEmpty()) {
            args += "--dns"; args += dns
        }
        // Carrier. Fragmenting lives only on the HTTP/2 MASQUE carrier, so the carrier
        // flag travels with it (--h2 / --h3 → AETHER_MASQUE_HTTP2, cli.rs:579-580).
        // It is emitted for every protocol that runs over the MASQUE carrier - masque,
        // and the two double-hop protocols that wrap it - on or off:
        // select_masque_transport asks interactively when the variable is unset
        // (lib.rs:3255-3268, called from lib.rs:234, :262 and :285), and a service has
        // no way to answer a prompt. WireGuard never uses that carrier.
        if (transport != AetherTransportChoice.WIREGUARD.id) {
            args += if (usesHttp2Carrier()) "--h2" else "--h3"
        }
        // TLS ClientHello fragmenting (MASQUE over HTTP/2 only).
        // --fragment / --no-fragment / --fragment-size / --fragment-delay exist in
        // aether/src/cli.rs:602-605. The SNI split has no CLI flag upstream
        // (AETHER_MASQUE_H2_FRAGMENT_SNI is read in fragment.rs:49 and set by nothing),
        // so on the FFI path it travels through AetherBridge.fragmentingApply, which the
        // JNI bridge turns into that variable; this argument list cannot express it.
        if (fragmentEnabled) {
            args += "--fragment"
            if (fragmentSize.trim().isNotEmpty()) {
                args += "--fragment-size"; args += fragmentSize.trim()
            }
            if (fragmentDelay.trim().isNotEmpty()) {
                args += "--fragment-delay"; args += fragmentDelay.trim()
            }
        } else {
            args += "--no-fragment"
        }
        // config path - Aether uses AETHER_CONFIG env, we set via --config if needed
        // Use peer's files dir for identity storage
        val fullPath = "$contextFilesDir/$identityPath"
        args += "--config"; args += fullPath
        // quick reconnect
        if (quickReconnect) args += "--quick-reconnect" else args += "--no-quick-reconnect"
        // Zero Trust team. The route's own field is the compact form of `--team`, and
        // the detailed editor's `AetherApiOptions.team` owns the same variable
        // (`cli.rs:612-613`), so the detailed one wins instead of the two being
        // written one after the other with the last one silently deciding.
        if (teamName.isNotBlank() && engine.api.team.isBlank()) {
            args += "--team"; args += teamName.trim()
        }
        // Every remaining family the core accepts (cli.rs:469-647), each emitting
        // nothing when it is left at the engine's own default.
        args += engine.toArgs()
        // The child processes. Both are spawned by the engine (psiphon by
        // psiphon.rs:749, the Tor transport by bridges.rs) and neither is on PATH
        // under Android, so the exact file is named here. Only fields the user
        // left blank are filled in; a path the user named always wins.
        if (engine.psiphon.isEnabled()) {
            if (engine.psiphon.bin.isBlank() && helpers.psiphonBinary.isNotEmpty()) {
                args += "--psiphon-bin"; args += helpers.psiphonBinary
            }
            if (engine.psiphon.dir.isBlank() && helpers.psiphonDirectory.isNotEmpty()) {
                args += "--psiphon-dir"; args += helpers.psiphonDirectory
            }
        }
        if (engine.tor.isEnabled()) {
            if (engine.tor.pt.isEmpty() && engine.tor.ptDir.isEmpty() && helpers.ptBinary.isNotEmpty()) {
                // One lyrebird binary serves every transport Aether asks for
                // (obfs4, snowflake, webtunnel), so it is named once per
                // transport rather than left to a lookup that cannot see it.
                listOf("obfs4", "snowflake", "webtunnel").forEach { name ->
                    args += "--tor-pt"; args += "$name=${helpers.ptBinary}"
                }
            }
            if (engine.tor.ptDir.isEmpty() && helpers.ptDirectory.isNotEmpty()) {
                args += "--tor-pt-dir"; args += helpers.ptDirectory
            }
            if (engine.tor.dir.isBlank() && helpers.torDirectory.isNotEmpty()) {
                args += "--tor-dir"; args += helpers.torDirectory
            }
        }
        return args
    }

    companion object {
        fun fromJson(json: String): AetherConfig? {
            return try {
                val o = JSONObject(json)
                AetherConfig(
                    identityPath = o.optString("identityPath", "aether_identity"),
                    transport = o.optString("transport", "masque"),
                    scanMode = o.optString("scanMode", "balanced"),
                    noizeProfile = o.optString("noizeProfile", "firewall"),
                    socksBind = o.optString("socksBind", "127.0.0.1:1819"),
                    httpProxyBind = o.optString("httpProxyBind", ""),
                    peer = o.optString("peer", ""),
                    upstream = o.optString("upstream", ""),
                    echEnabled = o.optBoolean("echEnabled", false),
                    dns = o.optString("dns", "1.1.1.1,1.0.0.1"),
                    ipVersion = o.optString("ipVersion", "v4"),
                    teamName = o.optString("teamName", ""),
                    quickReconnect = o.optBoolean("quickReconnect", true),
                    // Absent in configs written before fragmenting existed: fall
                    // back to the documented Aether defaults, never to a guess.
                    fragmentEnabled = o.optBoolean("fragmentEnabled", AetherFragmenting.DEFAULT_ENABLED),
                    fragmentSize = o.optString("fragmentSize", AetherFragmenting.DEFAULT_SIZE),
                    fragmentDelay = o.optString("fragmentDelay", AetherFragmenting.DEFAULT_DELAY),
                    fragmentSniSplit = o.optBoolean("fragmentSniSplit", AetherFragmenting.DEFAULT_SNI_SPLIT),
                    // A configuration written by an earlier build carries a
                    // "startMode" member. It is ignored: there is one engine and
                    // one way to start it, so the stored value cannot change what
                    // happens, and dropping the field is what forgets it.
                    engine = AetherEngineOptions.fromJson(o.optJSONObject("engine"))
                )
            } catch (e: Exception) { null }
        }

        val DEFAULT = AetherConfig()
    }
}

/** Detects port clash with RSTA config */
fun detectAetherPortConflict(aether: AetherConfig, rsta: RSTAConfig): String? {
    val aetherPort = aether.socksBind.substringAfterLast(":").toIntOrNull()
    if (aetherPort != null && aetherPort == rsta.listenPort) {
        return "Aether SOCKS port $aetherPort conflicts with RSTA listen port; change one"
    }
    if (aether.httpProxyBind.trim().isNotEmpty()) {
        val httpPort = aether.httpProxyBind.trim().substringAfterLast(":").toIntOrNull()
        if (httpPort != null && httpPort == rsta.listenPort) {
            return "Aether HTTP proxy port conflicts with RSTA port"
        }
        if (httpPort == aetherPort) return "Aether SOCKS and HTTP proxy ports must differ"
    }
    return null
}
