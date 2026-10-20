package com.manjaniq

import com.manjaniq.core.AetherLogSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Aether logging boundary must never let a credential reach the screen.
 *
 * Aether log lines are assembled from FFI payloads and replies, which can carry
 * Zero Trust enrolment secrets, a team token, or key material. These tests pin
 * both halves of the contract: secrets are masked, and ordinary operational
 * detail (endpoints, ports, transports, job ids, timings) is left untouched so
 * the panel stays useful.
 *
 * Plain JUnit: [AetherLogSanitizer] is text-only.
 */
class AetherLogSanitizerTest {

    private fun assertMasked(input: String, vararg mustNotAppear: String) {
        val out = AetherLogSanitizer.redact(input)
        assertTrue("expected ${AetherLogSanitizer.MASK} in: $out", out.contains(AetherLogSanitizer.MASK))
        mustNotAppear.forEach {
            assertFalse("secret fragment '$it' survived redaction in: $out", out.contains(it))
        }
    }

    @Test
    fun redactsPemPrivateKeyBlocks() {
        assertMasked(
            """identity loaded -----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7
-----END PRIVATE KEY-----""",
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7"
        )
    }

    @Test
    fun redactsUnterminatedPemBlock() {
        // A truncated key (a partial read, a clipped log line) must not leak either.
        assertMasked(
            "-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBALecretsecret",
            "MIIBOgIBAAJBALecretsecret"
        )
    }

    @Test
    fun redactsJsonWebTokens() {
        assertMasked(
            "team enrolment reply eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcDEF123_-xyz",
            "eyJhbGciOiJIUzI1NiJ9",
            "abcDEF123_-xyz"
        )
    }

    @Test
    fun redactsCredentialFlags() {
        assertMasked("aether --access-secret s3cr3t-value --bind 127.0.0.1:1819", "s3cr3t-value")
        assertMasked("--access-token=abc123XYZ", "abc123XYZ")
        assertMasked("--client-secret \"quotedSecretValue\"", "quotedSecretValue")
        // The non-secret part of the same command line survives.
        val out = AetherLogSanitizer.redact("aether --access-secret s3cr3t --bind 127.0.0.1:1819")
        assertTrue("operational detail must survive: $out", out.contains("--bind 127.0.0.1:1819"))
        assertTrue("the flag name stays so the line is still readable: $out", out.contains("--access-secret"))
    }

    @Test
    fun redactsCredentialsInsideProxyUrls() {
        assertMasked("upstream http://alice:hunter2@127.0.0.1:1080 dialing", "hunter2", "alice")
        val out = AetherLogSanitizer.redact("upstream socks5://alice:hunter2@127.0.0.1:1080")
        assertTrue("host and port must survive: $out", out.contains("@127.0.0.1:1080"))
    }

    @Test
    fun redactsSensitiveKeyValuesInEveryShape() {
        assertMasked("token=abcdef123456", "abcdef123456")
        assertMasked("token: abcdef123456", "abcdef123456")
        assertMasked("""{"access_token":"abcdef123456"}""", "abcdef123456")
        assertMasked("'client_secret': 'abcdef123456'", "abcdef123456")
        assertMasked("team_secret=abcdef123456", "abcdef123456")
        assertMasked("authorization=abcdef123456", "abcdef123456")
    }

    @Test
    fun recognisesSensitiveKeyNames() {
        listOf("token", "Token", "ACCESS_TOKEN", "access-secret", "client.secret", "team_secret", "password", "bearer")
            .forEach { assertTrue("'$it' must be treated as sensitive", AetherLogSanitizer.isSensitiveKey(it)) }
        listOf("socksBind", "transport", "scanMode", "endpoint", "job", "state", "version", "ip")
            .forEach { assertFalse("'$it' must not be treated as sensitive", AetherLogSanitizer.isSensitiveKey(it)) }
    }

    @Test
    fun leavesOrdinaryAetherLogLinesByteForByteIntact() {
        listOf(
            "Aether start requested",
            "Scan chose endpoint 203.0.113.7:2408",
            "Tunnel job 12 started; waiting for SOCKS5 127.0.0.1:1819",
            "transport=masque scanMode=balanced ipVersion=v4",
            "state=running version=2.3.0",
            "SOCKS5 127.0.0.1:1819 is accepting connections",
            "Tunnel closed by the gateway",
            ""
        ).forEach { line ->
            assertEquals("must not be altered: $line", line, AetherLogSanitizer.redact(line))
        }
    }

    @Test
    fun preservesLineBreaksSoThePanelStaysReadable() {
        val out = AetherLogSanitizer.redact("step one\ntoken=abcdef123456\nstep three")
        assertEquals(3, out.split("\n").size)
        assertTrue(out.contains("step one"))
        assertTrue(out.contains("step three"))
        assertFalse(out.contains("abcdef123456"))
    }
}
