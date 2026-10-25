package com.manjaniq.core

import org.json.JSONObject

enum class EConfigType(val value: Int, val protocolScheme: String) {
    VMESS(1, "vmess://"),
    CUSTOM(2, ""),
    SHADOWSOCKS(3, "ss://"),
    SOCKS(4, "socks://"),
    VLESS(5, "vless://"),
    TROJAN(6, "trojan://"),
    WIREGUARD(7, "wireguard://"),
    HYSTERIA2(9, "hysteria2://"),
    HTTP(10, "http://"),
    POLICYGROUP(11, ""),
    PROXYCHAIN(12, ""),
    AETHER(13, "aether://");

    companion object {
        fun fromName(name: String?): EConfigType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AETHER
    }
}

fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

data class ProfileItem(
    val configVersion: Int = 4,
    val configType: EConfigType = EConfigType.AETHER,
    var subscriptionId: String = "",
    var addedTime: Long = System.currentTimeMillis(),

    var remarks: String = "",
    var description: String? = null,
    var server: String? = null,
    var serverPort: String? = null,

    var password: String? = null,
    var method: String? = null,
    var flow: String? = null,
    var username: String? = null,

    var network: String? = null,
    var headerType: String? = null,
    var host: String? = null,
    var path: String? = null,
    var seed: String? = null,
    var kcpMtu: Int? = null,
    var kcpTti: Int? = null,

    var quicSecurity: String? = null,
    var quicKey: String? = null,
    var mode: String? = null,
    var serviceName: String? = null,
    var authority: String? = null,
    var xhttpMode: String? = null,
    var xhttpExtra: String? = null,
    var finalMask: String? = null,

    var security: String? = null,
    var sni: String? = null,
    var alpn: String? = null,
    var fingerPrint: String? = null,
    var cipherSuites: String? = null,
    var insecure: Boolean? = null,
    var echConfigList: String? = null,
    var echOutbound: String? = null,
    var verifyPeerCertByName: String? = null,
    var pinnedCA256: String? = null,

    var publicKey: String? = null,
    var shortId: String? = null,
    var spiderX: String? = null,
    var mldsa65Verify: String? = null,

    var secretKey: String? = null,
    var preSharedKey: String? = null,
    var localAddress: String? = null,
    var reserved: String? = null,
    var mtu: Int? = null,
    var remoteDNS: String? = null,

    var obfsPassword: String? = null,
    var portHopping: String? = null,
    var portHoppingInterval: String? = null,
    var bandwidthDown: String? = null,
    var bandwidthUp: String? = null,

    var policyGroupType: String? = null,
    var policyGroupSubscriptionId: String? = null,
    var policyGroupFilter: String? = null,
    var policyGroupTestOutbounds: Boolean? = null,
    var policyGroupFallbackTag: String? = null,
    var proxyChainProfiles: String? = null,

    var browserDialerMode: String? = null,
    var dialMode: String? = null,
    var targetStrategy: String? = null,

    var aetherProtocol: String? = null,
    var aetherTransport: String? = null,
    var aetherMasqueSni: String? = null,
    var aetherScanMode: String? = null,
    var aetherObfuscation: String? = null,
    var aetherFingerprint: String? = null,
    var aetherIpVersion: String? = null,
    var aetherWiwOuter: String? = null,
    var aetherWiwInner: String? = null,
    var aetherFragment: Boolean? = null,
    var aetherFragmentSize: String? = null,
    var aetherFragmentDelay: String? = null,
    var aetherEch: Boolean? = null,
    var aetherEchDns: String? = null,
    var aetherEchDomain: String? = null,
    var aetherDns: String? = null,
    var aetherExitLoc: String? = null,
    var aetherExitNode: String? = null,
    var aetherListenPort: String? = null,
    var aetherPsiphon: String? = null,
    var aetherPsiphonMode: String? = null,
    var aetherPsiphonCdnIps: String? = null,
    var aetherPsiphonCdnSni: String? = null,
    var aetherPsiphonCdnSets: String? = null,
    var aetherPsiphonRegion: String? = null,
    var aetherPsiphonBundledList: Boolean? = null,
    var aetherTor: String? = null,
    var aetherTorBridges: String? = null,
    var aetherTorBridgeLines: String? = null,
    var aetherTorRelays: String? = null,
    var aetherCommand: String? = null,
    var aetherFinalMask: String? = null,
    var aetherDialMode: String? = null,
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("configVersion", configVersion)
        obj.put("configType", configType.name)
        obj.put("subscriptionId", subscriptionId)
        obj.put("addedTime", addedTime)
        obj.put("remarks", remarks)
        fun putStr(key: String, v: String?) {
            if (v != null) obj.put(key, v)
        }
        fun putBool(key: String, v: Boolean?) {
            if (v != null) obj.put(key, v)
        }
        fun putInt(key: String, v: Int?) {
            if (v != null) obj.put(key, v)
        }
        putStr("description", description)
        putStr("server", server)
        putStr("serverPort", serverPort)
        putStr("password", password)
        putStr("method", method)
        putStr("flow", flow)
        putStr("username", username)
        putStr("network", network)
        putStr("headerType", headerType)
        putStr("host", host)
        putStr("path", path)
        putStr("seed", seed)
        putInt("kcpMtu", kcpMtu)
        putInt("kcpTti", kcpTti)
        putStr("mode", mode)
        putStr("serviceName", serviceName)
        putStr("authority", authority)
        putStr("xhttpMode", xhttpMode)
        putStr("xhttpExtra", xhttpExtra)
        putStr("finalMask", finalMask)
        putStr("security", security)
        putStr("sni", sni)
        putStr("alpn", alpn)
        putStr("fingerPrint", fingerPrint)
        putStr("cipherSuites", cipherSuites)
        putBool("insecure", insecure)
        putStr("echConfigList", echConfigList)
        putStr("echOutbound", echOutbound)
        putStr("verifyPeerCertByName", verifyPeerCertByName)
        putStr("pinnedCA256", pinnedCA256)
        putStr("publicKey", publicKey)
        putStr("shortId", shortId)
        putStr("spiderX", spiderX)
        putStr("mldsa65Verify", mldsa65Verify)
        putStr("secretKey", secretKey)
        putStr("preSharedKey", preSharedKey)
        putStr("localAddress", localAddress)
        putStr("reserved", reserved)
        putInt("mtu", mtu)
        putStr("remoteDNS", remoteDNS)
        putStr("obfsPassword", obfsPassword)
        putStr("portHopping", portHopping)
        putStr("portHoppingInterval", portHoppingInterval)
        putStr("bandwidthDown", bandwidthDown)
        putStr("bandwidthUp", bandwidthUp)
        putStr("browserDialerMode", browserDialerMode)
        putStr("dialMode", dialMode)
        putStr("targetStrategy", targetStrategy)
        putStr("aetherProtocol", aetherProtocol)
        putStr("aetherTransport", aetherTransport)
        putStr("aetherMasqueSni", aetherMasqueSni)
        putStr("aetherScanMode", aetherScanMode)
        putStr("aetherObfuscation", aetherObfuscation)
        putStr("aetherFingerprint", aetherFingerprint)
        putStr("aetherIpVersion", aetherIpVersion)
        putStr("aetherWiwOuter", aetherWiwOuter)
        putStr("aetherWiwInner", aetherWiwInner)
        putBool("aetherFragment", aetherFragment)
        putStr("aetherFragmentSize", aetherFragmentSize)
        putStr("aetherFragmentDelay", aetherFragmentDelay)
        putBool("aetherEch", aetherEch)
        putStr("aetherEchDns", aetherEchDns)
        putStr("aetherEchDomain", aetherEchDomain)
        putStr("aetherDns", aetherDns)
        putStr("aetherExitLoc", aetherExitLoc)
        putStr("aetherExitNode", aetherExitNode)
        putStr("aetherListenPort", aetherListenPort)
        putStr("aetherPsiphon", aetherPsiphon)
        putStr("aetherPsiphonMode", aetherPsiphonMode)
        putStr("aetherPsiphonCdnIps", aetherPsiphonCdnIps)
        putStr("aetherPsiphonCdnSni", aetherPsiphonCdnSni)
        putStr("aetherPsiphonCdnSets", aetherPsiphonCdnSets)
        putStr("aetherPsiphonRegion", aetherPsiphonRegion)
        putBool("aetherPsiphonBundledList", aetherPsiphonBundledList)
        putStr("aetherTor", aetherTor)
        putStr("aetherTorBridges", aetherTorBridges)
        putStr("aetherTorBridgeLines", aetherTorBridgeLines)
        putStr("aetherTorRelays", aetherTorRelays)
        putStr("aetherCommand", aetherCommand)
        putStr("aetherFinalMask", aetherFinalMask)
        putStr("aetherDialMode", aetherDialMode)
        return obj.toString()
    }

    companion object {
        fun create(configType: EConfigType = EConfigType.AETHER): ProfileItem =
            ProfileItem(configType = configType)

        fun defaultTargetStrategy(profile: ProfileItem): String {
            if (profile.configType != EConfigType.AETHER) return "AsIs"
            return if (AetherCoreManager.leavesThroughWarp(AetherCore.of(profile, AetherCoreManager.socksPort).arguments)) "UseIP" else "AsIs"
        }

        fun fromJsonString(json: String?): ProfileItem? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                fun str(key: String): String? =
                    if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null
                fun bool(key: String): Boolean? =
                    if (obj.has(key) && !obj.isNull(key)) obj.getBoolean(key) else null
                fun int(key: String): Int? =
                    if (obj.has(key) && !obj.isNull(key)) obj.getInt(key) else null

                ProfileItem(
                    configVersion = int("configVersion") ?: 4,
                    configType = EConfigType.fromName(str("configType")),
                    subscriptionId = str("subscriptionId") ?: "",
                    addedTime = if (obj.has("addedTime") && !obj.isNull("addedTime")) obj.getLong("addedTime") else System.currentTimeMillis(),
                    remarks = str("remarks") ?: "",
                    description = str("description"),
                    server = str("server"),
                    serverPort = str("serverPort"),
                    password = str("password"),
                    method = str("method"),
                    flow = str("flow"),
                    username = str("username"),
                    network = str("network"),
                    headerType = str("headerType"),
                    host = str("host"),
                    path = str("path"),
                    seed = str("seed"),
                    kcpMtu = int("kcpMtu"),
                    kcpTti = int("kcpTti"),
                    mode = str("mode"),
                    serviceName = str("serviceName"),
                    authority = str("authority"),
                    xhttpMode = str("xhttpMode"),
                    xhttpExtra = str("xhttpExtra"),
                    finalMask = str("finalMask"),
                    security = str("security"),
                    sni = str("sni"),
                    alpn = str("alpn"),
                    fingerPrint = str("fingerPrint"),
                    cipherSuites = str("cipherSuites"),
                    insecure = bool("insecure"),
                    echConfigList = str("echConfigList"),
                    echOutbound = str("echOutbound"),
                    verifyPeerCertByName = str("verifyPeerCertByName"),
                    pinnedCA256 = str("pinnedCA256"),
                    publicKey = str("publicKey"),
                    shortId = str("shortId"),
                    spiderX = str("spiderX"),
                    mldsa65Verify = str("mldsa65Verify"),
                    secretKey = str("secretKey"),
                    preSharedKey = str("preSharedKey"),
                    localAddress = str("localAddress"),
                    reserved = str("reserved"),
                    mtu = int("mtu"),
                    remoteDNS = str("remoteDNS"),
                    obfsPassword = str("obfsPassword"),
                    portHopping = str("portHopping"),
                    portHoppingInterval = str("portHoppingInterval"),
                    bandwidthDown = str("bandwidthDown"),
                    bandwidthUp = str("bandwidthUp"),
                    browserDialerMode = str("browserDialerMode"),
                    dialMode = str("dialMode"),
                    targetStrategy = str("targetStrategy"),
                    aetherProtocol = str("aetherProtocol"),
                    aetherTransport = str("aetherTransport"),
                    aetherMasqueSni = str("aetherMasqueSni"),
                    aetherScanMode = str("aetherScanMode"),
                    aetherObfuscation = str("aetherObfuscation"),
                    aetherFingerprint = str("aetherFingerprint"),
                    aetherIpVersion = str("aetherIpVersion"),
                    aetherWiwOuter = str("aetherWiwOuter"),
                    aetherWiwInner = str("aetherWiwInner"),
                    aetherFragment = bool("aetherFragment"),
                    aetherFragmentSize = str("aetherFragmentSize"),
                    aetherFragmentDelay = str("aetherFragmentDelay"),
                    aetherEch = bool("aetherEch"),
                    aetherEchDns = str("aetherEchDns"),
                    aetherEchDomain = str("aetherEchDomain"),
                    aetherDns = str("aetherDns"),
                    aetherExitLoc = str("aetherExitLoc"),
                    aetherExitNode = str("aetherExitNode"),
                    aetherListenPort = str("aetherListenPort"),
                    aetherPsiphon = str("aetherPsiphon"),
                    aetherPsiphonMode = str("aetherPsiphonMode"),
                    aetherPsiphonCdnIps = str("aetherPsiphonCdnIps"),
                    aetherPsiphonCdnSni = str("aetherPsiphonCdnSni"),
                    aetherPsiphonCdnSets = str("aetherPsiphonCdnSets"),
                    aetherPsiphonRegion = str("aetherPsiphonRegion"),
                    aetherPsiphonBundledList = bool("aetherPsiphonBundledList"),
                    aetherTor = str("aetherTor"),
                    aetherTorBridges = str("aetherTorBridges"),
                    aetherTorBridgeLines = str("aetherTorBridgeLines"),
                    aetherTorRelays = str("aetherTorRelays"),
                    aetherCommand = str("aetherCommand"),
                    aetherFinalMask = str("aetherFinalMask"),
                    aetherDialMode = str("aetherDialMode"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
