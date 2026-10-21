package com.manjaniq.core

import org.json.JSONObject

/**
 * Interpretation of an Aether scan job: what a successful reply contains, and
 * what each way of failing means.
 *
 * Everything here is pure - no FFI, no coroutine, no Android type beyond
 * [JSONObject] - so the scan path can be tested against fixtures instead of a
 * live network. The behaviour it encodes is read out of the vendored core:
 *
 *  - `aether_scan_start` finishes a job with `json!({"endpoint": endpoint})`
 *    (third_party/Aether/aether/src/ffi.rs) and `ok_value` merges `"ok": true`
 *    into that object;
 *  - `api::Endpoint` serialises as `{"ip": IpAddr, "port": u16, "rtt_ms": u64}`
 *    (api.rs). There is **no** pre-formatted `"ip:port"` string in the reply, so
 *    reading the `endpoint` member as a string yields the JSON text of that
 *    object;
 *  - `aether_tunnel_start` parses its `peer` with `socket_of`, i.e.
 *    `str::parse::<SocketAddr>()`, and answers
 *    `the peer address '<x>' is not an address:port` when it cannot. Feeding it
 *    the JSON text above therefore fails the tunnel start even though the scan
 *    succeeded, and the endpoint the scan chose is thrown away;
 *  - failures are `AetherError` Display strings (error.rs), for example
 *    `prober: no clean endpoint found` and `cancelled`.
 */
object AetherScanOutcome {

    /** Why a scan produced no endpoint. */
    enum class Failure {
        /** Aether verified candidates and none of them passed. */
        NO_ENDPOINT,

        /** The scan was cancelled, by the app or by the core. */
        CANCELLED,

        /** The ECH configuration lookup failed, which ends a MASQUE scan job. */
        ECH_CONFIG,

        /** A TLS/QUIC/HTTP3 level error. */
        PROTOCOL,

        /** A prober or MASQUE level error other than "no clean endpoint". */
        PROBE,

        /** The identity was refused while provisioning. */
        IDENTITY,

        /** The app's own poll deadline expired before the scan answered. */
        TIMEOUT,

        /** Anything else, reported with Aether's own wording. */
        OTHER
    }

    /**
     * The `address:port` string `aether_tunnel_start` accepts, or `null` when
     * [result] holds no usable endpoint.
     *
     * A candidate is only returned when it really parses as a socket address, so
     * a mis-shaped reply fails here with a clear message instead of being handed
     * to the core and coming back as a confusing peer error.
     */
    fun endpointFrom(result: JSONObject?): String? {
        if (result == null) return null

        val endpoint = result.optJSONObject("endpoint")
        if (endpoint != null) {
            val address = socketAddressOf(endpoint.optString("ip"), endpoint.optInt("port", -1))
            if (address != null) return address
        }
        // Only a value that already is an address:port is accepted as a string.
        // `optString` on an object member returns that object's JSON text, which
        // is exactly what must not be forwarded as a peer.
        result.optString("endpoint").takeIf { isSocketAddress(it) }?.let { return it.trim() }
        result.optString("peer").takeIf { isSocketAddress(it) }?.let { return it.trim() }
        return null
    }

    /** `ip` and `port` as the core's `socket_of` expects them, or `null`. */
    fun socketAddressOf(ip: String?, port: Int): String? {
        val host = ip?.trim().orEmpty()
        if (host.isEmpty() || port !in 1..65535) return null
        // `IpAddr`'s Display does not bracket an IPv6 literal, and
        // `SocketAddr::from_str` requires brackets.
        return if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }

    /** Whether [candidate] is an `address:port` the core can parse. */
    fun isSocketAddress(candidate: String?): Boolean {
        val text = candidate?.trim().orEmpty()
        if (text.isEmpty()) return false
        val host: String
        val portText: String
        if (text.startsWith("[")) {
            val close = text.indexOf("]:")
            if (close < 0) return false
            host = text.substring(1, close)
            portText = text.substring(close + 2)
        } else {
            val colon = text.lastIndexOf(':')
            if (colon <= 0) return false
            host = text.substring(0, colon)
            portText = text.substring(colon + 1)
        }
        if (host.isEmpty()) return false
        val port = portText.toIntOrNull() ?: return false
        return port in 1..65535
    }

    /**
     * The kind of failure [raw] describes.
     *
     * `null` is what the poll loop returns when its own deadline expires, which
     * is a timeout and not an error from the core.
     */
    fun classify(raw: String?): Failure {
        if (raw == null) return Failure.TIMEOUT
        val message = raw.trim().lowercase()
        if (message.isEmpty()) return Failure.OTHER
        return when {
            "cancelled" in message || "canceled" in message -> Failure.CANCELLED
            "no clean endpoint" in message -> Failure.NO_ENDPOINT
            message.startsWith("ech:") -> Failure.ECH_CONFIG
            message.startsWith("identity refused:") -> Failure.IDENTITY
            message.startsWith("quic:") || message.startsWith("h3:") ||
                message.startsWith("tls:") -> Failure.PROTOCOL
            message.startsWith("prober:") || message.startsWith("masque:") ||
                message.startsWith("capsule:") -> Failure.PROBE
            else -> Failure.OTHER
        }
    }

    /**
     * The one line to log and show when a scan produced no endpoint.
     *
     * Aether's own wording is kept verbatim wherever it exists: the app must not
     * paraphrase an engine error into something that reads like a different
     * failure. [timeoutMs] is the deadline the app applied, used only to say how
     * long it waited when the poll expired.
     */
    fun report(raw: String?, timeoutMs: Long): String {
        val detail = raw?.trim()?.takeIf { it.isNotEmpty() }
        return when (classify(raw)) {
            Failure.TIMEOUT ->
                "Scan timed out after ${timeoutMs / 1000}s: Aether had not chosen an endpoint yet, " +
                    "so the app ended the scan job. Nothing was connected; a shorter scan mode or " +
                    "another transport may answer within the wait."
            Failure.NO_ENDPOINT ->
                "Scan failed: ${detail ?: "prober: no clean endpoint found"} - Aether probed the " +
                    "candidate endpoints and none of them passed verification. This is a " +
                    "reachability result, not a configuration error: no endpoint was selected and " +
                    "nothing was connected."
            Failure.CANCELLED ->
                "Scan cancelled before an endpoint was chosen" +
                    (detail?.let { " ($it)" } ?: "") + "."
            Failure.ECH_CONFIG ->
                "Scan failed: ${detail ?: "ech"} - the ECH configuration could not be fetched, so " +
                    "the scan stopped instead of sending the server name in the clear. Turn ECH off " +
                    "to scan without it."
            Failure.PROTOCOL, Failure.PROBE, Failure.IDENTITY, Failure.OTHER ->
                "Scan failed: ${detail ?: "unknown error"}"
        }
    }
}
