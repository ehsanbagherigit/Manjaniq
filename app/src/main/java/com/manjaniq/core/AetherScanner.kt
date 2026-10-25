package com.manjaniq.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AetherScanResult(
    val endpoint: AetherEndpoint,
    val innerHop: AetherEndpoint? = null,
) {
    val primary: AetherEndpoint
        get() = endpoint

    val secondary: AetherEndpoint?
        get() = innerHop

    fun format(): String = if (innerHop != null) {
        "${endpoint.format()} -> ${innerHop.format()}"
    } else {
        endpoint.format()
    }
}

object AetherScanner {

    private const val SCAN_TIMEOUT_MS = 8 * 60_000L

    private val masqueGateway = Regex("""selected MASQUE gateway (\S+)""")
    private val wireguardEndpoint = Regex("""selected WireGuard endpoint (\S+)""")
    private val goolHops = Regex("""using cloudflare edge (\S+) \(outer\) and (\S+) \(inner\)""")
    private val mimHops = Regex("""masque-in-masque ready: (\S+) \(outer\) and (\S+) \(inner\)""")
    private val goolOverMasqueReady = Regex("""gool ready: masque (\S+) carries wireguard (\S+)""")
    private val exitAccepted = Regex("""exit location \S+ accepted""")
    private val exitRejected = Regex("""exit location \S+ rejected""")

    suspend fun scan(
        context: Context,
        profile: ProfileItem,
        onOutput: (String) -> Unit = {},
    ): AetherScanResult? {
        val protocol = AetherProtocol.fromString(profile.aetherProtocol)
        val port = withContext(Dispatchers.IO) { AetherCoreManager.scanPort(profile) }
        return AetherCoreManager.runUntil(
            context = context,
            arguments = AetherCoreManager.buildArguments(profile, port, scan = true),
            exit = AetherExit.of(profile),
            timeoutMs = SCAN_TIMEOUT_MS,
            source = "aether-scan",
            onOutput = onOutput,
            matcher = matcher(protocol, exitRuled = !profile.aetherExitLoc.isNullOrBlank()),
        )
    }

    internal fun matcher(protocol: AetherProtocol, exitRuled: Boolean): (String) -> AetherScanResult? {
        if (!exitRuled || protocol == AetherProtocol.MIM || protocol == AetherProtocol.WG_OVER_MASQUE) {
            return { line -> parse(protocol, line) }
        }
        var named: AetherScanResult? = null
        return { line ->
            val found = parse(protocol, line)
            when {
                found != null -> {
                    named = found
                    null
                }
                exitAccepted.containsMatchIn(line) -> named
                exitRejected.containsMatchIn(line) -> {
                    named = null
                    null
                }
                else -> null
            }
        }
    }

    fun parse(protocol: AetherProtocol, line: String): AetherScanResult? = when (protocol) {
        AetherProtocol.GOOL -> hopsOf(goolHops, line)
        AetherProtocol.MIM -> hopsOf(mimHops, line)
        AetherProtocol.MASQUE -> masqueGateway.find(line)?.let { endpointOf(it.groupValues[1]) }?.let(::AetherScanResult)
        AetherProtocol.WIREGUARD -> wireguardEndpoint.find(line)?.let { endpointOf(it.groupValues[1]) }?.let(::AetherScanResult)
        AetherProtocol.WG_OVER_MASQUE -> goolOverMasqueReady.find(line)?.let { endpointOf(it.groupValues[1]) }?.let(::AetherScanResult)
    }

    private fun hopsOf(hops: Regex, line: String): AetherScanResult? = hops.find(line)?.let { match ->
        val outer = endpointOf(match.groupValues[1])
        val inner = endpointOf(match.groupValues[2])
        if (outer != null && inner != null) AetherScanResult(outer, inner) else null
    }

    private fun endpointOf(text: String): AetherEndpoint? {
        AetherEndpoint.parse(text)?.let { return it }
        val separator = text.lastIndexOf(':')
        if (separator <= 0) return null
        return AetherEndpoint.of(text.substring(0, separator), text.substring(separator + 1))
    }
}
