package com.manjaniq.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Read-only connectivity facts about this device, logged next to a registration
 * failure so the report says what the device could see rather than leaving the
 * cause to guesswork.
 *
 * It makes no connection of its own: no socket, no DNS query, no HTTP request and
 * no certificate work, so it cannot bypass or weaken any check the engine performs.
 * It reads only what `ConnectivityManager` already knows, under the
 * `ACCESS_NETWORK_STATE` permission the manifest declares, and every field it
 * reports comes from the platform's own answer - a value it could not read is
 * reported as unread, not as absent.
 */
object AetherNetworkCheck {

    /**
     * What the platform reported. [activeNetwork] is false only when the platform
     * said there is no active network; a call that failed leaves [factsOf] returning
     * null instead of pretending nothing is connected.
     */
    data class Facts(
        val activeNetwork: Boolean,
        val validated: Boolean,
        val internet: Boolean,
        val captivePortal: Boolean,
        val transports: List<String>,
        val dnsServers: Int,
        val systemProxy: String?,
        val metered: Boolean
    )

    /** Reads the facts, or null when the platform would not answer. */
    fun factsOf(context: Context): Facts? = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            null
        } else {
            val network = manager.activeNetwork
            val caps = if (network == null) null else manager.getNetworkCapabilities(network)
            val link = if (network == null) null else manager.getLinkProperties(network)
            val transports = listOf(
                NetworkCapabilities.TRANSPORT_WIFI to "wifi",
                NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
                NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
                NetworkCapabilities.TRANSPORT_VPN to "vpn"
            ).filter { caps?.hasTransport(it.first) == true }.map { it.second }
            Facts(
                activeNetwork = network != null,
                validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                captivePortal = caps?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
                ) == true,
                transports = transports,
                dnsServers = link?.dnsServers?.size ?: 0,
                systemProxy = link?.httpProxy?.toString(),
                metered = manager.isActiveNetworkMetered
            )
        }
    } catch (t: Throwable) {
        null
    }

    /** One honest log line for the facts, or for the platform not answering. */
    fun summarize(facts: Facts?): String {
        if (facts == null) {
            return "Device network state could not be read, so this report cannot say " +
                "whether the device was online when registration failed"
        }
        if (!facts.activeNetwork) {
            return "Device network: none active - registration cannot reach the WARP API " +
                "until the device is online"
        }
        val transport = facts.transports.ifEmpty { listOf("unrecognised") }.joinToString("+")
        val validation = when {
            facts.captivePortal -> "captive portal detected, so HTTPS to the API is intercepted"
            !facts.internet -> "present but not marked as reaching the internet"
            !facts.validated -> "present but not validated, so the device has not confirmed it works"
            else -> "validated"
        }
        val dns = if (facts.dnsServers > 0) {
            "${facts.dnsServers} DNS server(s)"
        } else {
            "no DNS server reported"
        }
        val proxy = facts.systemProxy?.let { "system proxy $it" } ?: "no system proxy"
        val metered = if (facts.metered) "metered" else "unmetered"
        return "Device network: $transport, $validation, $dns, $proxy, $metered"
    }

    /** True when the facts say the device itself was in a position to reach the API. */
    fun looksReachable(facts: Facts?): Boolean =
        facts != null && facts.activeNetwork && facts.validated && !facts.captivePortal
}
