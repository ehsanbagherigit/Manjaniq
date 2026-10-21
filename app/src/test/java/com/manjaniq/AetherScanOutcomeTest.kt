package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherJobPolling
import com.manjaniq.core.AetherScanOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What an Aether scan reply means, checked against fixtures rather than a live
 * network.
 *
 * The shapes asserted here are read out of the vendored core, not guessed:
 * `aether_scan_start` finishes a job with `json!({"endpoint": endpoint})`
 * (third_party/Aether/aether/src/ffi.rs), `ok_value` merges `"ok": true` into
 * that object, `api::Endpoint` serialises as `{"ip": .., "port": .., "rtt_ms": ..}`
 * (api.rs), and `aether_tunnel_start` parses its `peer` with `socket_of`, i.e.
 * `str::parse::<SocketAddr>()`. Failure texts are `AetherError` Display strings
 * (error.rs).
 *
 * Robolectric because `org.json` is only a stub on the plain host JVM.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherScanOutcomeTest {

    /** The exact object a successful scan job ends with. */
    private fun scanReply(ip: String = "198.51.100.7", port: Int = 443, rttMs: Int = 37) =
        JSONObject("""{"ok":true,"endpoint":{"ip":"$ip","port":$port,"rtt_ms":$rttMs}}""")

    @Test
    fun successfulScanYieldsTheAddressTheTunnelStartParses() {
        val endpoint = AetherScanOutcome.endpointFrom(scanReply())
        assertEquals("198.51.100.7:443", endpoint)
        assertTrue(
            "the core parses this with str::parse::<SocketAddr>()",
            AetherScanOutcome.isSocketAddress(endpoint)
        )
    }

    @Test
    fun ipv6EndpointIsBracketedTheWaySocketAddrRequires() {
        // `IpAddr`'s Display does not add brackets; `SocketAddr::from_str` needs them.
        val endpoint = AetherScanOutcome.endpointFrom(scanReply(ip = "2001:db8::7"))
        assertEquals("[2001:db8::7]:443", endpoint)
        assertTrue(AetherScanOutcome.isSocketAddress(endpoint))
    }

    @Test
    fun readingTheEndpointMemberAsAStringIsRejected() {
        // This is the shape that used to reach `aether_tunnel_start`: `optString`
        // on an object member stringifies it, and the core then answers
        // "the peer address '<json>' is not an address:port".
        val asText = scanReply().optString("endpoint", "")
        assertTrue("precondition: optString stringifies the object", asText.startsWith("{"))
        assertFalse(AetherScanOutcome.isSocketAddress(asText))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":"{\"ip\":\"1.2.3.4\"}"}""")))
        assertEquals("198.51.100.7:443", AetherScanOutcome.endpointFrom(scanReply()))
    }

    @Test
    fun unusableRepliesProduceNoEndpoint() {
        assertNull(AetherScanOutcome.endpointFrom(null))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"ok":true}""")))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":{"ip":"","port":443}}""")))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":{"ip":"1.2.3.4"}}""")))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":{"ip":"1.2.3.4","port":0}}""")))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":{"ip":"1.2.3.4","port":70000}}""")))
        assertNull(AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":{"ip":"1.2.3.4","port":"443x"}}""")))
    }

    @Test
    fun anAlreadyFormattedAddressIsStillAccepted() {
        assertEquals(
            "203.0.113.9:443",
            AetherScanOutcome.endpointFrom(JSONObject("""{"endpoint":"203.0.113.9:443"}"""))
        )
        assertEquals(
            "203.0.113.9:443",
            AetherScanOutcome.endpointFrom(JSONObject("""{"peer":"203.0.113.9:443"}"""))
        )
    }

    @Test
    fun everyScanFailureIsClassifiedFromAethersOwnWording() {
        // The poll loop returns null when its own deadline expires: a timeout,
        // not an error from the core.
        assertEquals(AetherScanOutcome.Failure.TIMEOUT, AetherScanOutcome.classify(null))
        assertEquals(
            AetherScanOutcome.Failure.NO_ENDPOINT,
            AetherScanOutcome.classify("prober: no clean endpoint found")
        )
        assertEquals(AetherScanOutcome.Failure.CANCELLED, AetherScanOutcome.classify("cancelled"))
        assertEquals(
            AetherScanOutcome.Failure.ECH_CONFIG,
            AetherScanOutcome.classify("ech: could not fetch the ECH configuration")
        )
        assertEquals(AetherScanOutcome.Failure.PROTOCOL, AetherScanOutcome.classify("quic: Timeout"))
        assertEquals(AetherScanOutcome.Failure.PROTOCOL, AetherScanOutcome.classify("h3: internal error"))
        assertEquals(AetherScanOutcome.Failure.PROTOCOL, AetherScanOutcome.classify("tls: handshake failed"))
        assertEquals(AetherScanOutcome.Failure.PROBE, AetherScanOutcome.classify("masque: bad response"))
        assertEquals(AetherScanOutcome.Failure.PROBE, AetherScanOutcome.classify("capsule: malformed"))
        assertEquals(
            AetherScanOutcome.Failure.IDENTITY,
            AetherScanOutcome.classify("identity refused: rate limited")
        )
        assertEquals(AetherScanOutcome.Failure.OTHER, AetherScanOutcome.classify("there is no job 3"))
        assertEquals(AetherScanOutcome.Failure.OTHER, AetherScanOutcome.classify(""))
        assertEquals(AetherScanOutcome.Failure.OTHER, AetherScanOutcome.classify("   "))
    }

    @Test
    fun noEndpointKeepsAethersWordingAndSaysNothingWasConnected() {
        val report = AetherScanOutcome.report(
            "prober: no clean endpoint found",
            AetherJobPolling.scanTimeoutFor("masque", "balanced")
        )
        assertTrue(report.contains("prober: no clean endpoint found"))
        assertTrue(report.contains("none of them passed verification"))
        assertTrue(report.contains("nothing was connected"))
        assertFalse("a scan that found nothing must not read as a success", report.contains("chose endpoint"))
    }

    @Test
    fun anExpiredPollDeadlineIsReportedAsATimeoutWithTheWaitThatWasApplied() {
        val timeoutMs = AetherJobPolling.scanTimeoutFor("wg", "thorough")
        val report = AetherScanOutcome.report(null, timeoutMs)
        assertTrue("names the wait actually applied: $report", report.contains("timed out after ${timeoutMs / 1000}s"))
        assertTrue(report.contains("ended the scan job"))
    }

    @Test
    fun cancellationIsReportedAsCancellation() {
        val report = AetherScanOutcome.report("cancelled", AetherJobPolling.scanTimeoutFor("masque", "turbo"))
        assertTrue(report.contains("cancelled"))
        assertTrue(report.contains("before an endpoint was chosen"))
        assertFalse("a stop the user asked for is not a failure", report.contains("Scan failed"))
    }

    @Test
    fun anEchFailureNamesTheWayOut() {
        val report = AetherScanOutcome.report("ech: dns lookup failed", 60_000L)
        assertTrue(report.contains("ech: dns lookup failed"))
        assertTrue(report.contains("Turn ECH off"))
    }

    @Test
    fun anyOtherErrorIsPassedThroughVerbatim() {
        assertEquals("Scan failed: there is no job 3", AetherScanOutcome.report("there is no job 3", 60_000L))
        assertEquals("Scan failed: masque: bad response", AetherScanOutcome.report("masque: bad response", 60_000L))
        assertEquals("Scan failed: unknown error", AetherScanOutcome.report("", 60_000L))
    }
}
