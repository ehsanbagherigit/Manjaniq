package com.manjaniq.core.models

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
     * exclusively from the process environment / CLI flags. See
     * [AetherFragmenting] and [AetherCapabilities] for what this build can and
     * cannot forward; the values are persisted either way so nothing is lost
     * when the forwarding gap is closed.
     */
    val fragmentEnabled: Boolean = AetherFragmenting.DEFAULT_ENABLED,
    /** Chunk size in bytes: `n` or `a-b` (documented Aether default `16-32`). */
    val fragmentSize: String = AetherFragmenting.DEFAULT_SIZE,
    /** Delay between fragments in ms: `n` or `a-b` (Aether default `2-10`). */
    val fragmentDelay: String = AetherFragmenting.DEFAULT_DELAY,
    /**
     * Split inside the SNI host name instead of at a random offset
     * (`AETHER_MASQUE_H2_FRAGMENT_SNI`). Environment-only upstream: there is no
     * `--fragment-sni` CLI flag, so [toAetherArgs] cannot express it.
     */
    val fragmentSniSplit: Boolean = AetherFragmenting.DEFAULT_SNI_SPLIT
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
        if (transport !in AetherTransportChoice.IDS) return "Invalid transport"
        // `aether_tunnel_start` parses only "wg" and "masque", and
        // `api::Transport::parse` (api.rs) maps every other string to Masque.
        // Forwarding a transport this build cannot dispatch would therefore run
        // plain MASQUE while the stored configuration claims something else - a
        // silent fallback. Refuse instead, with the ids that do work.
        if (!AetherTransportChoice.isSelectable(transport)) {
            return "Transport '$transport' cannot be started by this build (Aether would " +
                "run MASQUE instead); choose " +
                AetherTransportChoice.SELECTABLE_IDS.joinToString(" or ")
        }
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
        // fragment.rs has exactly one consumer, masque_h2.rs, so a transport other
        // than MASQUE would ignore fragmenting entirely. Refusing says so up front
        // instead of starting a tunnel that quietly does something else.
        if (fragmentEnabled && transport != AetherTransportChoice.MASQUE.id) {
            return "Fragmenting needs the MASQUE transport: Aether applies it only on the " +
                "MASQUE-over-HTTP/2 carrier, so '$transport' would ignore it"
        }
        return null
    }

    /**
     * Whether this configuration asks for the HTTP/2 MASQUE carrier.
     *
     * Fragmenting is written into the ClientHello of that carrier and nowhere else
     * (`fragment.rs` → `masque_h2.rs:239, 284, 402`), so asking for fragments asks for
     * the carrier too. QUIC MASQUE and WireGuard are untouched when this is false.
     */
    fun usesHttp2Carrier(): Boolean = fragmentEnabled && transport == AetherTransportChoice.MASQUE.id

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
    }.toString()

    /**
     * The Aether command line for this configuration, for `aether_core_start`
     * (`AetherBridge.coreStart`).
     *
     * NOTE: `AetherService` currently drives Aether through the granular
     * `identity_open` / `scan_start` / `tunnel_start` FFI calls instead, so this
     * mapping is not what runs today. It is kept complete and correct because it
     * is the only path that can express gool, mim, DNS, upstream and quick
     * reconnect. Fragmenting reaches the engine today through
     * [AetherBridge.fragmentingApply] (`AetherService.applyFragmenting`), which
     * writes the variables Aether reads; these flags carry the same decision for
     * the `aether_core_start` path so the two never disagree. See
     * AETHER_CAPABILITIES.md §3.
     */
    fun toAetherArgs(contextFilesDir: String): List<String> {
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
        // ech
        if (echEnabled) {
            args += "--ech"; args += "auto"
        }
        // dns
        if (dns.isNotEmpty()) {
            args += "--dns"; args += dns
        }
        // Carrier. Fragmenting lives only on the HTTP/2 MASQUE carrier, so the carrier
        // flag travels with it (--h2 / --h3 → AETHER_MASQUE_HTTP2, cli.rs:579-580).
        // It is emitted for every MASQUE start, on or off: select_masque_transport asks
        // interactively when the variable is unset (lib.rs:3255-3268), and a service has
        // no way to answer a prompt.
        if (transport == AetherTransportChoice.MASQUE.id) {
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
                    fragmentSniSplit = o.optBoolean("fragmentSniSplit", AetherFragmenting.DEFAULT_SNI_SPLIT)
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
