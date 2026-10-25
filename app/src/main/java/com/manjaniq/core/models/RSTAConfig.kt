// Package com.manjaniq.core.models provides data models for the RSTA engine.
package com.manjaniq.core.models

import org.json.JSONObject

// EngineState represents the current state of the RSTA engine.
enum class EngineState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

// RSTAConfig represents the configuration for the RSTA engine.
data class RSTAConfig(
    val listenHost: String,
    val listenPort: Int,
    val connectIp: String,
    val connectPort: Int,
    val sniHostname: String,
    val bypassMethod: String,
    val fragmentStrategy: String,
    val fragmentDelay: Double,
    val useTtlTrick: Boolean,
    val monitorEnabled: Boolean,
    val quietMode: Boolean,
    val fakeSNIMethod: String,
    val fakeSNIFragmentReal: Boolean
) {
    companion object {
        @JvmField
        val DEFAULT = RSTAConfig(
            listenHost = "0.0.0.0",
            listenPort = 40443,
            connectIp = "104.18.38.202",
            connectPort = 443,
            sniHostname = "cdnjs.cloudflare.com",
            bypassMethod = "fragment",
            fragmentStrategy = "sni_split",
            fragmentDelay = 0.1,
            useTtlTrick = false,
            monitorEnabled = true,
            quietMode = false,
            fakeSNIMethod = "prefix_fake",
            fakeSNIFragmentReal = true
        )

        val BYPASS_METHODS = listOf("fragment", "fake_sni", "combined")
        val FRAGMENT_STRATEGIES = listOf("sni_split", "half", "multi", "tls_record_frag")

        /**
         * Parses a config in the genuine RSTA config.json format.
         * Missing keys fall back to [DEFAULT] per field. Returns null
         * when [json] is not a JSON object at all; callers must still
         * [validate] the result before starting the engine.
         */
        fun fromJson(json: String): RSTAConfig? {
            val obj = try {
                JSONObject(json)
            } catch (e: Exception) {
                return null
            }
            val d = DEFAULT
            return RSTAConfig(
                listenHost = obj.optString("LISTEN_HOST", d.listenHost),
                listenPort = obj.optInt("LISTEN_PORT", d.listenPort),
                connectIp = obj.optString("CONNECT_IP", d.connectIp),
                connectPort = obj.optInt("CONNECT_PORT", d.connectPort),
                sniHostname = obj.optString("FAKE_SNI", d.sniHostname),
                bypassMethod = obj.optString("BYPASS_METHOD", d.bypassMethod),
                fragmentStrategy = obj.optString("FRAGMENT_STRATEGY", d.fragmentStrategy),
                fragmentDelay = obj.optDouble("FRAGMENT_DELAY", d.fragmentDelay),
                useTtlTrick = obj.optBoolean("USE_TTL_TRICK", d.useTtlTrick),
                monitorEnabled = obj.optBoolean("MONITOR", d.monitorEnabled),
                quietMode = obj.optBoolean("quiet", d.quietMode),
                fakeSNIMethod = obj.optString("FAKE_SNI_METHOD", d.fakeSNIMethod),
                fakeSNIFragmentReal = obj.optBoolean("FAKE_SNI_FRAGMENT_REAL", d.fakeSNIFragmentReal)
            )
        }
    }

    /** Serialises to the genuine RSTA config.json format. */
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("LISTEN_HOST", listenHost)
        obj.put("LISTEN_PORT", listenPort)
        obj.put("CONNECT_IP", connectIp)
        obj.put("CONNECT_PORT", connectPort)
        obj.put("FAKE_SNI", sniHostname)
        obj.put("BYPASS_METHOD", bypassMethod)
        obj.put("FRAGMENT_STRATEGY", fragmentStrategy)
        obj.put("FRAGMENT_DELAY", fragmentDelay)
        obj.put("USE_TTL_TRICK", useTtlTrick)
        obj.put("FAKE_SNI_METHOD", fakeSNIMethod)
        obj.put("FAKE_SNI_FRAGMENT_REAL", fakeSNIFragmentReal)
        obj.put("MONITOR", monitorEnabled)
        obj.put("quiet", quietMode)
        return obj.toString(2)
    }

    /**
     * Returns a human-readable problem, or null when the config may
     * be handed to the engine. Mirrors the Go-side validation; the
     * engine re-validates and its verdict is authoritative.
     */
    fun validate(): String? {
        if (listenHost.isBlank() || listenHost.any { it.isWhitespace() }) {
            return "Listen host must be an IP address or hostname without spaces"
        }
        if (listenPort !in 1..65535) return "Listen port must be between 1 and 65535"
        if (connectIp.isBlank() || connectIp.any { it.isWhitespace() }) {
            return "Target host/IP must not be empty"
        }
        if (connectPort !in 1..65535) return "Target port must be between 1 and 65535"
        if (!HOSTNAME_REGEX.matches(sniHostname)) {
            return "SNI hostname is not a valid hostname"
        }
        if (bypassMethod !in BYPASS_METHODS) return "Unknown bypass method: $bypassMethod"
        if (fragmentStrategy !in FRAGMENT_STRATEGIES) {
            return "Unknown fragmentation strategy: $fragmentStrategy"
        }
        if (fragmentDelay < 0.0 || fragmentDelay > 5.0) {
            return "Fragment delay must be between 0 and 5 seconds"
        }
        return null
    }
}

private val HOSTNAME_REGEX =
    Regex("^(?=.{1,253}$)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
