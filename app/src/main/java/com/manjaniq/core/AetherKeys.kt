package com.manjaniq.core

/**
 * The arguments and the validation of the WARP keys registration screen, which runs the core on
 * `--register` to register new WARP keys without scanning or opening a tunnel.
 */
object AetherKeys {

    const val REGISTER = "--register"
    const val API_ADDRESS = "--api-address"
    const val LEGACY_ENROLL_ADDRESS = "--enroll-address"
    const val API_SNI = "--api-sni"
    const val API_HOST = "--api-host"
    const val API_FRAGMENT = "--api-fragment"

    enum class Carrier(val type: String) {
        OFF("off"),
        REVERSE("reverse");

        companion object {
            fun fromString(type: String?): Carrier = entries.find { it.type == type } ?: OFF
        }
    }

    fun buildArguments(
        kind: AetherKeyKind = AetherKeyKind.ALL,
        enrollAddress: String? = null,
        apiSni: String? = null,
        apiHost: String? = null,
        fingerprint: AetherFingerprint = AetherFingerprint.CHROME,
        fragment: Boolean = false,
        fragmentSize: String? = null,
        fragmentDelay: String? = null,
        ech: Boolean = false,
        echDns: String? = null,
        echDomain: String? = null,
        psiphon: Carrier = Carrier.OFF,
        psiphonMode: AetherPsiphonMode = AetherPsiphonMode.AUTO,
        psiphonCdnIps: String? = null,
        psiphonCdnSni: String? = null,
        psiphonCdnSets: String? = null,
        psiphonBundledList: Boolean = true,
        tor: Carrier = Carrier.OFF,
        torBridges: AetherTorBridges = AetherTorBridges.AUTO,
        torBridgeLines: String? = null,
        torRelays: AetherTorRelays = AetherTorRelays.AUTO,
        listenPort: Int = AetherCoreManager.socksPort,
    ): List<String> = buildList {
        addAll(listOf(REGISTER, kind.register))
        AetherEndpoint.parseHostOrAddress(enrollAddress)?.let { addAll(listOf(API_ADDRESS, it)) }
        apiSni?.trim()?.takeIf { AetherEndpoint.isValidDomain(it) }?.let { addAll(listOf(API_SNI, it)) }
        apiHost?.trim()?.takeIf { AetherEndpoint.isValidDomain(it) }?.let { addAll(listOf(API_HOST, it)) }
        addAll(fingerprint.arguments)
        addAll(
            if (fragment) {
                buildList {
                    add(API_FRAGMENT)
                    AetherRange.parseFragmentSize(fragmentSize)?.let { addAll(listOf("--fragment-size", it.format())) }
                    AetherRange.parseFragmentDelay(fragmentDelay)?.let { addAll(listOf("--fragment-delay", it.format())) }
                }
            } else {
                listOf("--no-fragment")
            }
        )
        addAll(AetherCoreManager.echArguments(ech, echDns, echDomain))
        if (psiphon == Carrier.REVERSE) {
            addAll(
                AetherCoreManager.psiphonArguments(
                    mode = AetherPsiphon.REVERSE,
                    connectionMode = psiphonMode,
                    cdnIps = psiphonCdnIps,
                    cdnSni = psiphonCdnSni,
                    cdnSets = psiphonCdnSets,
                    region = null,
                    bundledList = psiphonBundledList,
                    socksPort = listenPort,
                )
            )
        }
        if (tor == Carrier.REVERSE) {
            addAll(AetherCoreManager.torArguments(AetherTor.REVERSE, torBridges, torBridgeLines, torRelays, listenPort))
        }
    }

    fun command(arguments: List<String>): String = AetherCore(arguments).command

    fun ofCommand(command: String): Pair<AetherKeyKind, List<String>>? {
        val arguments = AetherCore.argumentsOf(command)
        val kind = kindOf(arguments) ?: return null
        return kind to arguments
    }

    fun kindOf(arguments: List<String>): AetherKeyKind? {
        var register: String? = null
        var i = 0
        while (i < arguments.size) {
            val arg = arguments[i]
            when {
                arg == REGISTER -> {
                    val value = arguments.getOrNull(i + 1) ?: return null
                    register = value
                    i += 2
                }
                arg in AetherCoreManager.flagsWithValue -> {
                    if (i + 1 >= arguments.size) return null
                    i += 2
                }
                else -> i++
            }
        }
        return AetherKeyKind.ofRegister(register)
    }
}
