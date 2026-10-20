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
    val quickReconnect: Boolean = true
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
        if (httpProxyBind.isNotEmpty()) {
            if (!httpProxyBind.startsWith("127.0.0.1:") && !httpProxyBind.startsWith("localhost:")) {
                return "HTTP proxy must be loopback if enabled"
            }
            val p = httpProxyBind.substringAfterLast(":").toIntOrNull()
            if (p == null || p !in 1024..65535) return "Invalid HTTP proxy port"
        }
        val validTransports = setOf("masque", "wg", "gool", "mim")
        if (transport !in validTransports) return "Invalid transport"
        val validScan = setOf("turbo", "balanced", "thorough", "verified", "ironclad")
        if (scanMode !in validScan) return "Invalid scan mode"
        return null
    }

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
    }.toString()

    fun toAetherArgs(contextFilesDir: String): List<String> {
        val args = mutableListOf<String>()
        // bind is required; Aether defaults to 127.0.0.1:1819 but we set explicitly
        args += "--bind"; args += socksBind.ifEmpty { "127.0.0.1:1819" }
        if (httpProxyBind.isNotEmpty()) {
            args += "--http-proxy"; args += httpProxyBind
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
        // noize
        if (noizeProfile.isNotEmpty() && noizeProfile != "off") {
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
                    quickReconnect = o.optBoolean("quickReconnect", true)
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
    if (aether.httpProxyBind.isNotEmpty()) {
        val httpPort = aether.httpProxyBind.substringAfterLast(":").toIntOrNull()
        if (httpPort != null && httpPort == rsta.listenPort) {
            return "Aether HTTP proxy port conflicts with RSTA port"
        }
        if (httpPort == aetherPort) return "Aether SOCKS and HTTP proxy ports must differ"
    }
    return null
}
