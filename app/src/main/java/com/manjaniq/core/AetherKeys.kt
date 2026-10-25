package com.manjaniq.core

import org.json.JSONObject

data class AetherKeysSettings(
    val kind: AetherKeyKind = AetherKeyKind.ALL,
    val enrollAddress: String = "",
    val apiSni: String = "",
    val apiHost: String = "",
    val upstreamProxy: String = "",
    val fragment: Boolean = false,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    val ech: Boolean = false,
    val echDns: String = AetherCoreManager.AETHER_ECH_DNS,
    val echDomain: String = AetherCoreManager.AETHER_ECH_DOMAIN,
    val fingerprint: AetherFingerprint = AetherFingerprint.CHROME,
    val exitNode: String = "",
    val finalMask: String = "",
    val dialMode: String = "",
    val command: String = "",
) {
    val exit: AetherExit
        get() = exitNode.nullIfBlank()?.let { AetherExit(node = it.trim()) }
            ?: AetherExit(finalMask = finalMask.trim(), dialMode = dialMode.trim())

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("kind", kind.type)
        obj.put("enrollAddress", enrollAddress)
        obj.put("apiSni", apiSni)
        obj.put("apiHost", apiHost)
        obj.put("upstreamProxy", upstreamProxy)
        obj.put("fragment", fragment)
        obj.put("fragmentSize", fragmentSize)
        obj.put("fragmentDelay", fragmentDelay)
        obj.put("ech", ech)
        obj.put("echDns", echDns)
        obj.put("echDomain", echDomain)
        obj.put("fingerprint", fingerprint.type)
        obj.put("exitNode", exitNode)
        obj.put("finalMask", finalMask)
        obj.put("dialMode", dialMode)
        obj.put("command", command)
        return obj.toString()
    }

    companion object {
        fun fromJsonString(raw: String?): AetherKeysSettings? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(raw)
                fun str(k: String, default: String = ""): String =
                    if (obj.has(k) && !obj.isNull(k)) obj.getString(k) else default
                fun bool(k: String, default: Boolean = false): Boolean =
                    if (obj.has(k) && !obj.isNull(k)) obj.getBoolean(k) else default
                AetherKeysSettings(
                    kind = AetherKeyKind.fromString(str("kind", AetherKeyKind.ALL.type)),
                    enrollAddress = str("enrollAddress"),
                    apiSni = str("apiSni"),
                    apiHost = str("apiHost"),
                    upstreamProxy = str("upstreamProxy"),
                    fragment = bool("fragment"),
                    fragmentSize = str("fragmentSize"),
                    fragmentDelay = str("fragmentDelay"),
                    ech = bool("ech"),
                    echDns = str("echDns", AetherCoreManager.AETHER_ECH_DNS),
                    echDomain = str("echDomain", AetherCoreManager.AETHER_ECH_DOMAIN),
                    fingerprint = AetherFingerprint.fromString(str("fingerprint", AetherFingerprint.CHROME.type)),
                    exitNode = str("exitNode"),
                    finalMask = str("finalMask"),
                    dialMode = str("dialMode"),
                    command = str("command"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

object AetherKeys {

    const val REGISTER = "--register"
    const val API_ADDRESS = "--api-address"
    const val LEGACY_ENROLL_ADDRESS = "--enroll-address"
    const val API_SNI = "--api-sni"
    const val API_HOST = "--api-host"
    const val API_FRAGMENT = "--api-fragment"
    const val DEFAULT_ENROLL_ADDRESS = "162.159.137.105:443"

    private const val STORAGE_KEY = "aether_keys_settings"

    enum class Problem {
        INVALID_ENROLL_ADDRESS,
        INVALID_API_SNI,
        INVALID_API_HOST,
        INVALID_UPSTREAM,
        INVALID_FRAGMENT,
        INVALID_ECH_DNS,
        INVALID_ECH_DOMAIN,
        INVALID_FINAL_MASK,
        INVALID_COMMAND,
    }

    fun arguments(settings: AetherKeysSettings): List<String>? {
        if (problem(settings) != null) return null
        return runArguments(settings)
    }

    fun buildSettingsArguments(settings: AetherKeysSettings): List<String> = buildList {
        addAll(listOf(REGISTER, settings.kind.register))
        settings.enrollAddress.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf(API_ADDRESS, it)) }
        settings.apiSni.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf(API_SNI, it)) }
        settings.apiHost.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf(API_HOST, it)) }
        AetherFmt.normalizeUpstreamUrl(settings.upstreamProxy)?.takeIf { it.isNotEmpty() }?.let {
            addAll(listOf(AetherCoreManager.UPSTREAM, it))
        }
        if (settings.fragment) {
            add("--fragment")
            AetherRange.parse(settings.fragmentSize, AetherRange.FRAGMENT_SIZE)?.let { addAll(listOf("--fragment-size", it.toString())) }
            AetherRange.parse(settings.fragmentDelay, AetherRange.FRAGMENT_DELAY)?.let { addAll(listOf("--fragment-delay", it.toString())) }
        }
        if (settings.ech) {
            addAll(listOf("--ech", "auto"))
            addAll(listOf("--ech-dns", settings.echDns.trim().ifEmpty { AetherCoreManager.AETHER_ECH_DNS }))
            addAll(listOf("--ech-domain", settings.echDomain.trim().ifEmpty { AetherCoreManager.AETHER_ECH_DOMAIN }))
        }
        addAll(settings.fingerprint.arguments)
    }

    fun builtCommand(settings: AetherKeysSettings): String =
        AetherCore(buildSettingsArguments(settings)).command

    fun isCustom(settings: AetherKeysSettings): Boolean =
        settings.command.isNotBlank() && settings.command.trim() != builtCommand(settings)

    fun runArguments(settings: AetherKeysSettings): List<String> =
        if (isCustom(settings)) AetherCore.argumentsOf(settings.command) else buildSettingsArguments(settings)

    fun kindOf(arguments: List<String>): AetherKeyKind? =
        arguments.lastIndexOf(REGISTER).takeIf { it >= 0 }?.let { AetherKeyKind.ofRegister(arguments.getOrNull(it + 1)) }

    fun kindOf(settings: AetherKeysSettings): AetherKeyKind? =
        if (isCustom(settings)) kindOf(runArguments(settings)) else settings.kind

    fun exitOf(settings: AetherKeysSettings): AetherExit = settings.exit

    fun validate(settings: AetherKeysSettings): Problem? = problem(settings)

    fun problem(settings: AetherKeysSettings): Problem? {
        if (!AetherExit.takesFinalMask(settings.finalMask)) return Problem.INVALID_FINAL_MASK
        if (isCustom(settings)) return Problem.INVALID_COMMAND.takeIf { kindOf(runArguments(settings)) == null }
        val address = settings.enrollAddress.trim()
        if (address.isNotEmpty() && !isEnrollAddress(address)) return Problem.INVALID_ENROLL_ADDRESS
        val sni = settings.apiSni.trim()
        if (sni.isNotEmpty() && !AetherFmt.isMasqueSni(sni)) return Problem.INVALID_API_SNI
        val host = settings.apiHost.trim()
        if (host.isNotEmpty() && !AetherFmt.isMasqueSni(host)) return Problem.INVALID_API_HOST
        if (AetherFmt.normalizeUpstreamUrl(settings.upstreamProxy) == null) return Problem.INVALID_UPSTREAM
        if (settings.fragment && !(takesRange(settings.fragmentSize, AetherRange.FRAGMENT_SIZE) && takesRange(settings.fragmentDelay, AetherRange.FRAGMENT_DELAY))) {
            return Problem.INVALID_FRAGMENT
        }
        if (settings.ech) {
            val dns = settings.echDns.trim()
            if (dns.isNotEmpty() && !AetherFmt.isEchDns(dns)) return Problem.INVALID_ECH_DNS
            val domain = settings.echDomain.trim()
            if (domain.isNotEmpty() && !AetherFmt.isEchDomain(domain)) return Problem.INVALID_ECH_DOMAIN
        }
        return null
    }

    internal fun isEnrollAddress(value: String): Boolean {
        if (isEnrollHost(value)) return true
        val separator = value.lastIndexOf(':')
        if (separator <= 0) return false
        val host = value.substring(0, separator)
        if (':' in host && !(host.startsWith('[') && host.endsWith(']'))) return false
        val port = value.substring(separator + 1)
        return port.length in 1..5 && port.all { it in '0'..'9' } && port.toInt() in 1..65535 && isEnrollHost(host)
    }

    private fun takesRange(text: String, bounds: IntRange): Boolean =
        text.isBlank() || AetherRange.parse(text, bounds) != null

    private fun isEnrollHost(host: String): Boolean =
        AetherEndpoint.of(host, "443") != null || AetherFmt.isEchDomain(host)

    fun load(): AetherKeysSettings =
        AetherKeysSettings.fromJsonString(AetherSettingsStorage.decodeString(STORAGE_KEY))
            ?: AetherKeysSettings()

    fun save(settings: AetherKeysSettings) {
        AetherSettingsStorage.encodeString(STORAGE_KEY, settings.toJsonString())
    }
}
