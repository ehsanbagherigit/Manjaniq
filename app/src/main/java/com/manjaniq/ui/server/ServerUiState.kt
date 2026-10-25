package com.manjaniq.ui.server

import androidx.annotation.StringRes
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherEndpoint
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.AetherIpVersion
import com.manjaniq.core.AetherObfuscation
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherPsiphon
import com.manjaniq.core.AetherPsiphonCdnSet
import com.manjaniq.core.AetherPsiphonMode
import com.manjaniq.core.AetherRange
import com.manjaniq.core.AetherScanMode
import com.manjaniq.core.AetherTor
import com.manjaniq.core.AetherTorBridges
import com.manjaniq.core.AetherTorRelays
import com.manjaniq.core.AetherTransport
import com.manjaniq.core.ProfileItem

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(@StringRes val messageRes: Int, val formatArg: String? = null) : ValidationResult
    data class Duplicate(val name: String) : ValidationResult
    data class Warning(@StringRes val messageRes: Int, val formatArg: String? = null) : ValidationResult
}

sealed interface ServerUiEvent {
    data class ShowToast(
        @StringRes val messageRes: Int = 0,
        val formatArg: String? = null,
        val text: String? = null,
    ) : ServerUiEvent

    data object SaveSuccess : ServerUiEvent
}

sealed interface AetherDialog {
    data class MissingKeys(val scan: Boolean, val profile: ProfileItem) : AetherDialog
    data object DiscardChanges : AetherDialog
    data object DeleteConfirm : AetherDialog
}

data class AetherUiState(
    val protocol: AetherProtocol = AetherProtocol.MASQUE,
    val transport: AetherTransport = AetherTransport.HTTP2,
    val scanMode: AetherScanMode = AetherScanMode.AGGRESSIVE,
    val obfuscation: AetherObfuscation = AetherObfuscation.AUTO,
    val ipVersion: AetherIpVersion = AetherIpVersion.V4,
    val psiphon: AetherPsiphon = AetherPsiphon.OFF,
    val psiphonMode: AetherPsiphonMode = AetherPsiphonMode.AUTO,
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val psiphonCdnSets: Set<AetherPsiphonCdnSet> = AetherPsiphonCdnSet.ALL,
    val psiphonRegion: String = "",
    val psiphonBundledList: Boolean = true,
    val tor: AetherTor = AetherTor.OFF,
    val torBridges: AetherTorBridges = AetherTorBridges.AUTO,
    val torBridgeLines: String = "",
    val torRelays: AetherTorRelays = AetherTorRelays.AUTO,
    val masqueSni: String = AetherCoreManager.AETHER_MASQUE_SNI,
    val fingerprint: AetherFingerprint = AetherFingerprint.CHROME,
    val fragment: Boolean = false,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    val ech: Boolean = false,
    val echDns: String = AetherCoreManager.AETHER_ECH_DNS,
    val echDomain: String = AetherCoreManager.AETHER_ECH_DOMAIN,
    val dns: String = "",
    val upstream: String = "",
    val exitLoc: String = "",
    val exitNode: String = "",
    val finalMask: String = "",
    val dialMode: String = "",
    val server: String = "",
    val port: String = "",
    val wiwOuter: String = "",
    val wiwInner: String = "",
    val command: String = "",
) {
    val warpRuns: Boolean
        get() = psiphon != AetherPsiphon.ONLY && tor != AetherTor.ONLY

    val overMasque: Boolean
        get() = warpRuns && protocol.overMasque

    val usesSingleEndpoint: Boolean
        get() = warpRuns && !protocol.twoHops

    val usesWiwOuter: Boolean
        get() = warpRuns && protocol.twoHops

    val usesWiwInner: Boolean
        get() = warpRuns && protocol.twoHops

    companion object {
        fun fromProfile(config: ProfileItem): AetherUiState = AetherUiState(
            protocol = AetherProtocol.fromString(config.aetherProtocol),
            transport = AetherTransport.fromString(config.aetherTransport),
            scanMode = AetherScanMode.fromString(config.aetherScanMode),
            obfuscation = AetherObfuscation.fromString(config.aetherObfuscation),
            ipVersion = AetherIpVersion.fromString(config.aetherIpVersion),
            psiphon = AetherPsiphon.fromString(config.aetherPsiphon),
            psiphonMode = AetherPsiphonMode.fromString(config.aetherPsiphonMode),
            psiphonCdnIps = config.aetherPsiphonCdnIps.orEmpty(),
            psiphonCdnSni = config.aetherPsiphonCdnSni.orEmpty(),
            psiphonCdnSets = AetherPsiphonCdnSet.parse(config.aetherPsiphonCdnSets),
            psiphonRegion = config.aetherPsiphonRegion?.trim()?.uppercase().orEmpty(),
            psiphonBundledList = config.aetherPsiphonBundledList != false,
            tor = AetherTor.fromString(config.aetherTor),
            torBridges = AetherTorBridges.fromString(config.aetherTorBridges),
            torBridgeLines = config.aetherTorBridgeLines.orEmpty(),
            torRelays = AetherTorRelays.fromString(config.aetherTorRelays),
            masqueSni = config.aetherMasqueSni?.ifEmpty { null } ?: AetherCoreManager.AETHER_MASQUE_SNI,
            fingerprint = AetherFingerprint.fromString(config.aetherFingerprint),
            fragment = config.aetherFragment == true,
            fragmentSize = config.aetherFragmentSize.orEmpty(),
            fragmentDelay = config.aetherFragmentDelay.orEmpty(),
            ech = config.aetherEch == true,
            echDns = config.aetherEchDns?.ifEmpty { null } ?: AetherCoreManager.AETHER_ECH_DNS,
            echDomain = config.aetherEchDomain?.ifEmpty { null } ?: AetherCoreManager.AETHER_ECH_DOMAIN,
            dns = config.aetherDns.orEmpty(),
            upstream = config.aetherUpstream.orEmpty(),
            exitLoc = config.aetherExitLoc.orEmpty(),
            exitNode = config.aetherExitNode.orEmpty(),
            finalMask = config.aetherFinalMask.orEmpty(),
            dialMode = config.aetherDialMode.orEmpty(),
            server = config.server.orEmpty(),
            port = config.serverPort.orEmpty(),
            wiwOuter = config.aetherWiwOuter.orEmpty(),
            wiwInner = config.aetherWiwInner.orEmpty(),
            command = config.aetherCommand.orEmpty(),
        )
    }

    fun applyTo(config: ProfileItem, socksPort: Int = AetherCoreManager.DEFAULT_SOCKS_PORT) {
        config.aetherProtocol = protocol.type
        config.aetherTransport = transport.type
        config.aetherScanMode = scanMode.type
        config.aetherObfuscation = obfuscation.type
        config.aetherIpVersion = ipVersion.type
        config.aetherPsiphon = psiphon.type
        config.aetherPsiphonMode = psiphonMode.type
        if (psiphon != AetherPsiphon.OFF && psiphonMode != AetherPsiphonMode.DIRECT) {
            config.aetherPsiphonCdnIps = psiphonCdnIps.trim().ifEmpty { null }
            config.aetherPsiphonCdnSni = psiphonCdnSni.trim().ifEmpty { null }
            config.aetherPsiphonCdnSets = AetherPsiphonCdnSet.join(psiphonCdnSets)
        } else {
            config.aetherPsiphonCdnIps = null
            config.aetherPsiphonCdnSni = null
            config.aetherPsiphonCdnSets = null
        }
        config.aetherPsiphonRegion = if (psiphon != AetherPsiphon.OFF) {
            psiphonRegion.trim().uppercase().ifEmpty { null }
        } else {
            null
        }
        config.aetherPsiphonBundledList = if (psiphon != AetherPsiphon.OFF && !psiphonBundledList) false else null
        config.aetherTor = tor.type
        if (tor != AetherTor.OFF) {
            config.aetherTorBridges = torBridges.type
            config.aetherTorBridgeLines = if (torBridges == AetherTorBridges.OWN) {
                AetherCoreManager.parseBridgeLines(torBridgeLines).joinToString("\n").ifEmpty { null }
            } else {
                null
            }
            config.aetherTorRelays = if (torBridges == AetherTorBridges.AUTO || torBridges == AetherTorBridges.FIRST) {
                torRelays.type
            } else {
                AetherTorRelays.AUTO.type
            }
        } else {
            config.aetherTorBridges = AetherTorBridges.AUTO.type
            config.aetherTorBridgeLines = null
            config.aetherTorRelays = AetherTorRelays.AUTO.type
        }
        config.aetherMasqueSni = masqueSni.trim().ifEmpty { AetherCoreManager.AETHER_MASQUE_SNI }
        config.aetherFingerprint = fingerprint.type
        config.aetherFragment = fragment
        if (fragment) {
            config.aetherFragmentSize = AetherRange.parseFragmentSize(fragmentSize)?.format()
                ?: fragmentSize.trim().ifEmpty { null }
            config.aetherFragmentDelay = AetherRange.parseFragmentDelay(fragmentDelay)?.format()
                ?: fragmentDelay.trim().ifEmpty { null }
        } else {
            config.aetherFragmentSize = null
            config.aetherFragmentDelay = null
        }
        config.aetherEch = ech
        if (ech) {
            config.aetherEchDns = echDns.trim().ifEmpty { AetherCoreManager.AETHER_ECH_DNS }
            config.aetherEchDomain = echDomain.trim().ifEmpty { AetherCoreManager.AETHER_ECH_DOMAIN }
        } else {
            config.aetherEchDns = null
            config.aetherEchDomain = null
        }
        config.aetherDns = dns.trim().ifEmpty { null }
        config.aetherUpstream = AetherFmt.normalizeUpstreamUrl(upstream)?.ifEmpty { null }
            ?: upstream.trim().ifEmpty { null }
        config.aetherExitLoc = if (psiphon != AetherPsiphon.OFF && warpRuns) {
            exitLoc.trim().uppercase().ifEmpty { null }
        } else {
            null
        }
        config.aetherExitNode = exitNode.trim().ifEmpty { null }
        config.aetherFinalMask = finalMask.trim().ifEmpty { null }
        config.aetherDialMode = dialMode.trim().ifEmpty { null }

        if (usesSingleEndpoint) {
            val ep = AetherEndpoint.of(server, port)
            if (ep != null) {
                config.server = ep.host
                config.serverPort = ep.port.toString()
            } else {
                config.server = server.trim().ifEmpty { null }
                config.serverPort = port.trim().ifEmpty { null }
            }
        } else {
            config.server = null
            config.serverPort = null
        }
        config.aetherWiwOuter = if (usesWiwOuter) {
            AetherEndpoint.parse(wiwOuter)?.format() ?: wiwOuter.trim().ifEmpty { null }
        } else {
            null
        }
        config.aetherWiwInner = if (usesWiwInner) {
            AetherEndpoint.parse(wiwInner)?.format() ?: wiwInner.trim().ifEmpty { null }
        } else {
            null
        }

        val writtenCommand = command.trim()
        val builtCommand = AetherFmt.builtCommand(config.copy(aetherCommand = null), socksPort)
        config.aetherCommand = writtenCommand.takeIf { it.isNotEmpty() && it != builtCommand }
    }
}
