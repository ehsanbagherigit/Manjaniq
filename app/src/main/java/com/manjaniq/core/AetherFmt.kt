package com.manjaniq.core

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object AetherFmt {

    const val PROTOCOL_SCHEME = "aether://"

    enum class Problem {
        INVALID_MASQUE_SNI,
        INVALID_PEER,
        INVALID_HOP,
        SHARED_HOP,
        INVALID_FRAGMENT,
        INVALID_DNS,
        INVALID_EXIT_LOC,
        INVALID_ECH_DNS,
        INVALID_ECH_DOMAIN,
        LISTEN_PORT_TAKEN,
        LISTEN_PORT_SHARED,
        LISTEN_PORT_INVALID,
        LISTEN_ADDRESS_INVALID,
        LISTEN_ADDRESS_UNREACHABLE,
        PSIPHON_NEEDS_MASQUE,
        NEXT_PORT_TAKEN,
        TOR_NEEDS_MASQUE,
        TOR_PSIPHON_CONFLICT,
        TOR_BRIDGES_MISSING,
        INVALID_COMMAND,
    }

    fun parse(str: String): ProfileItem? {
        val raw = str.trim().let {
            if (it.startsWith(PROTOCOL_SCHEME, ignoreCase = true)) it.substring(PROTOCOL_SCHEME.length) else it
        }
        val fixed = PROTOCOL_SCHEME + raw.replace(" ", "%20")
        val uri = try {
            URI(fixed)
        } catch (_: Exception) {
            return null
        }

        val config = ProfileItem.create()
        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
        val protocol = AetherProtocol.fromString(queryParam["protocol"])

        config.remarks = decodeComponent(uri.rawFragment.orEmpty()).ifEmpty { "Aether" }
        config.aetherProtocol = protocol.type
        config.aetherTransport = AetherTransport.fromString(queryParam["transport"]).type
        config.aetherMasqueSni = queryParam["sni"]?.trim()?.takeIf { protocol.overMasque && isMasqueSni(it) }
        config.aetherScanMode = AetherScanMode.fromString(queryParam["scan"]).type
        config.aetherObfuscation = AetherObfuscation.fromString(queryParam["noize"]).type
        config.aetherIpVersion = AetherIpVersion.fromString(queryParam["ip"]).type
        config.aetherFragment = queryParam["fragment"] == "1"
        config.aetherFragmentSize = AetherRange.parse(queryParam["fragment_size"], AetherRange.FRAGMENT_SIZE)?.toString()
        config.aetherFragmentDelay = AetherRange.parse(queryParam["fragment_delay"], AetherRange.FRAGMENT_DELAY)?.toString()
        config.aetherEch = queryParam["ech"] == "1"
        config.aetherEchDns = queryParam["ech_dns"]?.trim()?.takeIf { config.aetherEch == true && isEchDns(it) }
        config.aetherEchDomain = queryParam["ech_domain"]?.trim()?.takeIf { config.aetherEch == true && isEchDomain(it) }
        config.aetherFingerprint = queryParam["fingerprint"]?.let { AetherFingerprint.fromString(it).type }
        config.aetherDns = queryParam["dns"]
        config.aetherExitLoc = queryParam["exit_loc"]
        config.aetherPsiphon = AetherPsiphon.fromString(queryParam["psiphon"]).type.takeUnless { it == AetherPsiphon.OFF.type }
        config.aetherPsiphonMode = queryParam["psiphon_mode"]?.let { AetherPsiphonMode.fromString(it).type }
        config.aetherPsiphonCdnIps = queryParam["cdn_ips"]
        config.aetherPsiphonCdnSni = queryParam["cdn_sni"]
        config.aetherPsiphonCdnSets = queryParam["cdn_sets"]
        config.aetherPsiphonRegion = queryParam["region"]
        config.aetherPsiphonBundledList = if (queryParam["psiphon_bundled"] == "0") false else null
        config.aetherTor = AetherTor.fromString(queryParam["tor"]).type.takeUnless { it == AetherTor.OFF.type }
        config.aetherTorBridges = queryParam["tor_bridges"]?.let { AetherTorBridges.fromString(it).type }
        config.aetherTorBridgeLines = queryParam["bridges"]?.split(';')?.joinToString("\n")
        config.aetherTorRelays = queryParam["tor_relays"]?.let { AetherTorRelays.fromString(it).type }
        config.aetherFinalMask = queryParam["fm"]
        config.aetherDialMode = queryParam["dialMode"]

        if (protocol.twoHops) {
            val outer = AetherEndpoint.parse(queryParam["outer"])
            val inner = AetherEndpoint.parse(queryParam["inner"])?.takeUnless { protocol.distinctHops && it.host == outer?.host }
            config.aetherWiwOuter = outer?.toString()
            config.aetherWiwInner = inner?.toString()
        } else {
            val host = uri.host?.let { runCatching { IDN.toASCII(it) }.getOrDefault(it) }
            val endpoint = AetherEndpoint.of(host, uri.port.takeIf { it > 0 }?.toString())
            config.server = endpoint?.host
            config.serverPort = endpoint?.port?.toString()
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val protocol = AetherProtocol.fromString(config.aetherProtocol)
        val query = linkedMapOf(
            "protocol" to protocol.type,
            "scan" to AetherScanMode.fromString(config.aetherScanMode).type,
        )
        val overHttp2 = AetherCoreManager.masqueOverHttp2(
            protocol,
            AetherTransport.fromString(config.aetherTransport),
            AetherTor.fromString(config.aetherTor),
            AetherPsiphon.fromString(config.aetherPsiphon),
        )
        AetherObfuscation.fromString(config.aetherObfuscation)
            .takeUnless { it == AetherObfuscation.AUTO || overHttp2 }
            ?.let { query["noize"] = it.type }
        query["ip"] = AetherIpVersion.fromString(config.aetherIpVersion).type
        config.aetherDns?.takeIf { it.isNotBlank() }?.let { query["dns"] = it }
        config.aetherExitLoc?.takeIf { it.isNotBlank() }?.let { query["exit_loc"] = it }
        if (protocol.overMasque) {
            query["transport"] = AetherTransport.fromString(config.aetherTransport).type
            config.aetherMasqueSni?.trim()?.takeIf { it != AetherCoreManager.AETHER_MASQUE_SNI && isMasqueSni(it) }
                ?.let { query["sni"] = it }
            if (config.aetherFragment == true) {
                query["fragment"] = "1"
                AetherRange.parse(config.aetherFragmentSize, AetherRange.FRAGMENT_SIZE)
                    ?.let { query["fragment_size"] = it.toString() }
                AetherRange.parse(config.aetherFragmentDelay, AetherRange.FRAGMENT_DELAY)
                    ?.let { query["fragment_delay"] = it.toString() }
            }
            val warpUsed = AetherPsiphon.fromString(config.aetherPsiphon) != AetherPsiphon.ONLY &&
                AetherTor.fromString(config.aetherTor) != AetherTor.ONLY
            if (config.aetherEch == true && warpUsed) {
                query["ech"] = "1"
                config.aetherEchDns?.trim()?.takeIf { it.isNotEmpty() && isEchDns(it) }?.let { query["ech_dns"] = it }
                config.aetherEchDomain?.trim()?.takeIf { it.isNotEmpty() && isEchDomain(it) }?.let { query["ech_domain"] = it }
            }
            AetherFingerprint.fromString(config.aetherFingerprint).takeUnless { it == AetherFingerprint.CHROME }
                ?.let { query["fingerprint"] = it.type }
        }
        if (protocol.twoHops) {
            AetherEndpoint.parse(config.aetherWiwOuter)?.let { query["outer"] = it.toString() }
            AetherEndpoint.parse(config.aetherWiwInner)?.let { query["inner"] = it.toString() }
        }
        val psiphon = AetherPsiphon.fromString(config.aetherPsiphon)
        if (psiphon != AetherPsiphon.OFF) {
            query["psiphon"] = psiphon.type
            query["psiphon_mode"] = AetherPsiphonMode.fromString(config.aetherPsiphonMode).type
            config.aetherPsiphonCdnIps?.takeIf { it.isNotBlank() }?.let { query["cdn_ips"] = it }
            config.aetherPsiphonCdnSni?.takeIf { it.isNotBlank() }?.let { query["cdn_sni"] = it }
            config.aetherPsiphonCdnSets?.takeIf { it.isNotBlank() }?.let { query["cdn_sets"] = it }
            config.aetherPsiphonRegion?.takeIf { it.isNotBlank() }?.let { query["region"] = it }
            if (config.aetherPsiphonBundledList == false) query["psiphon_bundled"] = "0"
        }
        val tor = AetherTor.fromString(config.aetherTor)
        if (tor != AetherTor.OFF) {
            query["tor"] = tor.type
            query["tor_bridges"] = AetherTorBridges.fromString(config.aetherTorBridges).type
            query["tor_relays"] = AetherTorRelays.fromString(config.aetherTorRelays).type
            bridgeLines(config.aetherTorBridgeLines).takeIf { it.isNotEmpty() }?.let { query["bridges"] = it.joinToString(";") }
        }
        config.aetherFinalMask?.takeIf { it.isNotBlank() }?.let { query["fm"] = it }
        config.aetherDialMode?.takeIf { it.isNotBlank() }?.let { query["dialMode"] = it }
        val endpoint = AetherEndpoint.of(config.server, config.serverPort).takeUnless { protocol.twoHops }

        val queryText = query.entries.joinToString("&") { "${it.key}=${encodeComponent(it.value)}" }
        return "${endpoint ?: ""}?$queryText#${encodeComponent(config.remarks)}"
    }

    fun builtCommand(
        config: ProfileItem,
        listenPort: Int = AetherCoreManager.socksPort,
    ): String = AetherCore.formatCommand(
        AetherCoreManager.buildArguments(config, listenPort, scan = false),
    )

    fun isCustomCommand(
        config: ProfileItem,
        listenPort: Int = AetherCoreManager.socksPort,
    ): Boolean {
        val written = config.aetherCommand?.trim().orEmpty()
        if (written.isEmpty()) return false
        return written != builtCommand(config, listenPort)
    }

    fun effectiveProtocol(
        config: ProfileItem,
        listenPort: Int = AetherCoreManager.socksPort,
    ): AetherProtocol {
        val core = AetherCore.of(config, listenPort)
        return AetherCoreManager.protocolOf(core.arguments)
    }

    fun normalize(
        config: ProfileItem,
        takenPorts: Set<Int> = emptySet(),
        listenPort: Int = AetherCoreManager.socksPort,
    ): Problem? =
        normalizeMasqueSni(config)
            ?: normalizeFragment(config)
            ?: normalizeEndpoints(config)
            ?: normalizeDns(config)
            ?: normalizeExitLoc(config)
            ?: normalizeEch(config)
            ?: normalizePsiphon(config)
            ?: normalizeTor(config)
            ?: normalizeListenPort(config, takenPorts, listenPort)
            ?: normalizeCommand(config, takenPorts, listenPort)

    fun takenListenPort(
        config: ProfileItem,
        takenPorts: Set<Int>,
        listenPort: Int = AetherCoreManager.socksPort,
    ): Int? {
        val command = config.aetherCommand?.trim()
        if (command.isNullOrEmpty()) return listenPort.takeIf { it in takenPorts }
        return AetherCore.ofCommand(command, listenPort)?.ports?.firstOrNull { it in takenPorts }
    }

    private fun normalizeListenPort(config: ProfileItem, takenPorts: Set<Int>, listen: Int): Problem? {
        if (!config.aetherCommand.isNullOrBlank()) return null
        if (listen in takenPorts) return Problem.LISTEN_PORT_TAKEN
        val tor = AetherTor.fromString(config.aetherTor)
        val more = listOf(
            AetherPsiphon.fromString(config.aetherPsiphon) == AetherPsiphon.CHAIN,
            tor == AetherTor.CHAIN,
            tor == AetherTor.REVERSE,
        ).count { it }
        if ((1..more).any { listen + it in takenPorts }) return Problem.NEXT_PORT_TAKEN
        return null
    }

    private fun normalizeDns(config: ProfileItem): Problem? {
        val resolvers = config.aetherDns.orEmpty().split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }
        if (resolvers.any { AetherEndpoint.parse(it) == null && AetherEndpoint.of(it, "53") == null }) return Problem.INVALID_DNS
        config.aetherDns = resolvers.joinToString(",").ifEmpty { null }
        return null
    }

    private fun normalizeExitLoc(config: ProfileItem): Problem? {
        val rule = config.aetherExitLoc.orEmpty().filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
        if (rule.isEmpty()) {
            config.aetherExitLoc = null
            return null
        }
        if (!exitRule.matches(rule)) return Problem.INVALID_EXIT_LOC
        config.aetherExitLoc = rule
        return null
    }

    private val exitRule = Regex("!?[A-Z]{2}(,[A-Z]{2})*")

    private fun normalizeEch(config: ProfileItem): Problem? {
        val dns = config.aetherEchDns?.trim().orEmpty()
        val domain = config.aetherEchDomain?.trim().orEmpty()
        val ech = config.aetherEch == true
        val inUse = ech &&
            AetherProtocol.fromString(config.aetherProtocol).overMasque &&
            AetherPsiphon.fromString(config.aetherPsiphon) != AetherPsiphon.ONLY &&
            AetherTor.fromString(config.aetherTor) != AetherTor.ONLY
        if (inUse && dns.isNotEmpty() && !isEchDns(dns)) return Problem.INVALID_ECH_DNS
        if (inUse && domain.isNotEmpty() && !isEchDomain(domain)) return Problem.INVALID_ECH_DOMAIN
        config.aetherEchDns = dns.takeUnless { it.isEmpty() || it == AetherCoreManager.AETHER_ECH_DNS }
        config.aetherEchDomain = domain.takeUnless { it.isEmpty() || it == AetherCoreManager.AETHER_ECH_DOMAIN }
        return null
    }

    internal fun isEchDns(value: String): Boolean {
        if (value.any { it.isWhitespace() || it == '"' || it == '\'' }) return false
        val scheme = value.substringBefore("://", "").lowercase(Locale.ROOT)
        val rest = value.substringAfter("://", "")
        return when (scheme) {
            "https" -> isDohEndpoint(rest)
            "udp", "tcp" -> rest.trimEnd('/').let { address ->
                address.all { it.code < 0x80 } &&
                    !(address.startsWith('[') && ':' !in address.substringBefore(']')) &&
                    (AetherEndpoint.parse(address) ?: AetherEndpoint.of(address, "53")) != null
            }
            else -> false
        }
    }

    private fun isDohEndpoint(rest: String): Boolean {
        val pieces = rest.split('@')
        if (pieces.first().takeWhile { it !in "/?#" }.isEmpty()) return false
        val named = mutableSetOf<String>()
        for (piece in pieces.drop(1)) {
            if ('=' !in piece) return false
            val name = piece.substringBefore('=').lowercase(Locale.ROOT)
            val setting = piece.substringAfter('=')
            val isAddress = AetherEndpoint.of(setting, "443") != null
            val fits = when (name) {
                "address" -> isAddress || isEchDomain(setting)
                "sni" -> !isAddress && isEchDomain(setting)
                else -> false
            }
            if (!fits || !named.add(name)) return false
        }
        return true
    }

    internal fun isEchDomain(value: String): Boolean {
        val name = value.removeSuffix(".")
        return name.isNotEmpty() && name.length <= 253 && name.split('.').all { echDomainLabel.matches(it) }
    }

    private val echDomainLabel = Regex("[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?")

    private fun normalizeMasqueSni(config: ProfileItem): Problem? {
        val name = config.aetherMasqueSni?.trim().orEmpty()
        val inUse = AetherProtocol.fromString(config.aetherProtocol).overMasque &&
            AetherPsiphon.fromString(config.aetherPsiphon) != AetherPsiphon.ONLY &&
            AetherTor.fromString(config.aetherTor) != AetherTor.ONLY
        if (inUse && name.isNotEmpty() && !isMasqueSni(name)) return Problem.INVALID_MASQUE_SNI
        config.aetherMasqueSni = name.takeUnless { it.isEmpty() || it == AetherCoreManager.AETHER_MASQUE_SNI }
        return null
    }

    internal fun isMasqueSni(value: String): Boolean {
        val name = value.removeSuffix(".")
        return !name.endsWith('.') && AetherEndpoint.of(name, "443") == null && isEchDomain(name)
    }

    private fun normalizePsiphon(config: ProfileItem): Problem? {
        val psiphon = AetherPsiphon.fromString(config.aetherPsiphon)
        if (psiphon == AetherPsiphon.OFF) {
            config.aetherPsiphon = null
            config.aetherPsiphonMode = null
            config.aetherPsiphonCdnIps = null
            config.aetherPsiphonCdnSni = null
            config.aetherPsiphonCdnSets = null
            config.aetherPsiphonRegion = null
            config.aetherPsiphonBundledList = null
            return null
        }
        if (psiphon == AetherPsiphon.REVERSE && !AetherProtocol.fromString(config.aetherProtocol).overMasque) {
            return Problem.PSIPHON_NEEDS_MASQUE
        }
        config.aetherPsiphon = psiphon.type
        config.aetherPsiphonMode = AetherPsiphonMode.fromString(config.aetherPsiphonMode).type
        config.aetherPsiphonCdnIps = commaList(config.aetherPsiphonCdnIps)
        config.aetherPsiphonCdnSni = commaList(config.aetherPsiphonCdnSni)
        config.aetherPsiphonCdnSets = AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(config.aetherPsiphonCdnSets))
        config.aetherPsiphonRegion = config.aetherPsiphonRegion?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null }
        config.aetherPsiphonBundledList = config.aetherPsiphonBundledList?.takeUnless { it }
        return null
    }

    private fun normalizeTor(config: ProfileItem): Problem? {
        val tor = AetherTor.fromString(config.aetherTor)
        if (tor == AetherTor.OFF) {
            config.aetherTor = null
            config.aetherTorBridges = null
            config.aetherTorBridgeLines = null
            config.aetherTorRelays = null
            return null
        }
        if (tor == AetherTor.REVERSE && !AetherProtocol.fromString(config.aetherProtocol).overMasque) {
            return Problem.TOR_NEEDS_MASQUE
        }
        val psiphon = AetherPsiphon.fromString(config.aetherPsiphon)
        val nested = psiphon == AetherPsiphon.OFF ||
            (tor == AetherTor.CHAIN && psiphon == AetherPsiphon.REVERSE) ||
            (tor == AetherTor.REVERSE && psiphon == AetherPsiphon.CHAIN)
        if (!nested) return Problem.TOR_PSIPHON_CONFLICT
        val bridges = AetherTorBridges.fromString(config.aetherTorBridges)
        val lines = bridgeLines(config.aetherTorBridgeLines)
        if (bridges == AetherTorBridges.OWN && lines.isEmpty()) return Problem.TOR_BRIDGES_MISSING
        config.aetherTor = tor.type
        config.aetherTorBridges = bridges.type
        config.aetherTorBridgeLines = lines.takeIf { bridges == AetherTorBridges.OWN }?.joinToString("\n")
        config.aetherTorRelays = AetherTorRelays.fromString(config.aetherTorRelays).type
        return null
    }

    fun bridgeLines(text: String?): List<String> =
        text.orEmpty().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.removePrefix("Bridge ").removePrefix("bridge ").trim() }
            .filter { it.isNotEmpty() }

    private fun commaList(text: String?): String? =
        text?.split(Regex("[,\\s]+"))?.filter { it.isNotEmpty() }?.joinToString(",")?.ifEmpty { null }

    private fun normalizeCommand(config: ProfileItem, takenPorts: Set<Int>, listenPort: Int): Problem? {
        val text = config.aetherCommand?.trim().orEmpty()
        config.aetherCommand = text.ifEmpty { null }
        if (text.isEmpty()) return null
        val core = AetherCore.ofCommand(text, listenPort) ?: return Problem.INVALID_COMMAND
        if (AetherCoreManager.invalidPort(core.ports) != null) return Problem.LISTEN_PORT_INVALID
        if (core.invalidListener != null) return Problem.LISTEN_ADDRESS_INVALID
        if (core.unreachableListener != null) return Problem.LISTEN_ADDRESS_UNREACHABLE
        if (core.sharedPort != null) return Problem.LISTEN_PORT_SHARED
        return if (core.ports.any { it in takenPorts }) Problem.LISTEN_PORT_TAKEN else null
    }

    fun sharedListenPort(config: ProfileItem, listenPort: Int = AetherCoreManager.socksPort): Int? =
        commandCore(config, listenPort)?.sharedPort

    fun invalidListenPort(config: ProfileItem, listenPort: Int = AetherCoreManager.socksPort): Int? =
        commandCore(config, listenPort)?.let { AetherCoreManager.invalidPort(it.ports) }

    fun invalidListener(config: ProfileItem, listenPort: Int = AetherCoreManager.socksPort): String? =
        commandCore(config, listenPort)?.invalidListener

    fun unreachableListener(config: ProfileItem, listenPort: Int = AetherCoreManager.socksPort): String? =
        commandCore(config, listenPort)?.unreachableListener

    private fun commandCore(config: ProfileItem, listenPort: Int): AetherCore? =
        config.aetherCommand?.trim()?.takeIf { it.isNotEmpty() }?.let { AetherCore.ofCommand(it, listenPort) }

    private fun normalizeFragment(config: ProfileItem): Problem? {
        val inUse = AetherProtocol.fromString(config.aetherProtocol).overMasque &&
            AetherTransport.fromString(config.aetherTransport) == AetherTransport.HTTP2 &&
            config.aetherFragment == true
        val sizeText = config.aetherFragmentSize?.trim().orEmpty()
        val delayText = config.aetherFragmentDelay?.trim().orEmpty()
        val size = AetherRange.parse(sizeText, AetherRange.FRAGMENT_SIZE)
        val delay = AetherRange.parse(delayText, AetherRange.FRAGMENT_DELAY)
        if (inUse && (sizeText.isNotEmpty() && size == null || delayText.isNotEmpty() && delay == null)) {
            return Problem.INVALID_FRAGMENT
        }
        config.aetherFragmentSize = size?.toString()
        config.aetherFragmentDelay = delay?.toString()
        return null
    }

    private fun normalizeEndpoints(config: ProfileItem): Problem? {
        val protocol = AetherProtocol.fromString(config.aetherProtocol)
        if (protocol.twoHops) {
            val outerText = config.aetherWiwOuter?.trim().orEmpty()
            val innerText = config.aetherWiwInner?.trim().orEmpty()
            val outer = AetherEndpoint.parse(outerText)
            val inner = AetherEndpoint.parse(innerText)
            if (outerText.isNotEmpty() && outer == null || innerText.isNotEmpty() && inner == null) {
                return Problem.INVALID_HOP
            }
            if (protocol.distinctHops && outer != null && inner != null && outer.host == inner.host) {
                return Problem.SHARED_HOP
            }
            config.aetherWiwOuter = outer?.toString()
            config.aetherWiwInner = inner?.toString()
            config.server = null
            config.serverPort = null
            return null
        }

        val address = config.server?.trim().orEmpty()
        val endpoint = AetherEndpoint.of(address, config.serverPort)
        if (address.isNotEmpty() && endpoint == null) {
            return Problem.INVALID_PEER
        }
        config.server = endpoint?.host
        config.serverPort = endpoint?.port?.toString()
        config.aetherWiwOuter = null
        config.aetherWiwInner = null
        return null
    }

    private fun getQueryParam(uri: URI): Map<String, String> =
        uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else decodeComponent(pair.substring(0, idx)) to decodeComponent(pair.substring(idx + 1))
        }.toMap()

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun decodeComponent(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
