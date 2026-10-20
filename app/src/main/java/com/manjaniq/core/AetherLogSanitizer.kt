package com.manjaniq.core

/**
 * Redaction applied at the Aether logging boundary.
 *
 * The Aether operational log panel shows what the Aether workflow really did.
 * Some of that text is assembled from FFI payloads and replies, which can carry
 * Zero Trust credentials (`--access-secret`, `--access-token`, a service token
 * client secret), a team enrolment token, or key material read from the
 * identity file. None of those may reach the screen.
 *
 * This is a display/logging-boundary filter only: it never changes what is sent
 * to Aether, and it deliberately leaves operational detail (endpoints, ports,
 * transports, job ids, versions, timings) intact so the log stays useful.
 */
object AetherLogSanitizer {

    const val MASK = "[redacted]"

    /** Key names whose value must never be shown, normalised to snake_case. */
    private val SENSITIVE_KEYS = setOf(
        "token",
        "access_token",
        "access_secret",
        "access_id",
        "client_id",
        "client_secret",
        "secret",
        "password",
        "passwd",
        "pass",
        "private_key",
        "privkey",
        "priv_key",
        "authorization",
        "auth",
        "cookie",
        "credentials",
        "credential",
        "jwt",
        "api_key",
        "apikey",
        "session_token",
        "refresh_token",
        "bearer"
    )

    /** PEM private-key blocks, including the base64 body. */
    private val PEM_BLOCK = Regex(
        "-----BEGIN [A-Za-z0-9 ]*PRIVATE KEY-----[\\s\\S]*?(-----END [A-Za-z0-9 ]*PRIVATE KEY-----|$)"
    )

    /** A JSON Web Token: three dot-separated base64url segments starting with `eyJ`. */
    private val JWT = Regex("""eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}""")

    /** CLI-style credential flags, e.g. `--access-secret abc` or `--access-token=abc`. */
    private val CREDENTIAL_FLAG = Regex(
        """(--(?:access-secret|access-token|access-id|client-secret|client-id|team-secret|token|secret|password|api-key))([=\s]+)(\S+)"""
    )

    /**
     * Credentials embedded in a proxy URL, e.g. `http://user:pass@host:8080`.
     * The whole userinfo is masked — a user name is part of the credential —
     * while the scheme, host and port stay so the line remains useful.
     */
    private val URL_USERINFO = Regex("""([A-Za-z][A-Za-z0-9+.\-]*://)([^/\s:@]+):([^@\s/]+)(@)""")

    /**
     * `key=value`, `key: value`, `"key": "value"` and `'key': 'value'`.
     * Groups: 1 opening quote, 2 key, 3 separator, 4 value quote, 5 value.
     */
    private val KEY_VALUE = Regex("""(["']?)([A-Za-z0-9_.\-]+)\1(\s*[:=]\s*)(["']?)([^\s"'`,;}\]]+)\4""")

    /**
     * Returns [message] with every credential-shaped value replaced by [MASK].
     * Non-sensitive text is returned byte-for-byte, so ordinary Aether log
     * lines ("Scan chose endpoint 1.2.3.4:443") are never altered.
     */
    fun redact(message: String): String {
        if (message.isEmpty()) return message
        var out = PEM_BLOCK.replace(message) { MASK }
        out = JWT.replace(out) { MASK }
        out = CREDENTIAL_FLAG.replace(out) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}$MASK"
        }
        out = URL_USERINFO.replace(out) { m ->
            "${m.groupValues[1]}$MASK${m.groupValues[4]}"
        }
        out = KEY_VALUE.replace(out) { m ->
            val key = m.groupValues[2]
            if (isSensitiveKey(key)) {
                "${m.groupValues[1]}$key${m.groupValues[3]}${m.groupValues[4]}$MASK${m.groupValues[4]}"
            } else {
                m.value
            }
        }
        return out
    }

    /** True when [key] names a credential once case and `-`/`.` separators are normalised. */
    fun isSensitiveKey(key: String): Boolean {
        val normalised = key.trim().lowercase().replace('-', '_').replace('.', '_')
        if (normalised in SENSITIVE_KEYS) return true
        // Suffixed forms such as `team_secret`, `zerotrust_token`, `access_token_v2`.
        return SENSITIVE_KEYS.any { normalised.endsWith("_$it") || normalised.startsWith("${it}_") }
    }
}
