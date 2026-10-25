package com.manjaniq.core

import java.net.URI

data class AetherEndpoint(val host: String, val port: Int) {

    fun format(): String = if (':' in host) "[$host]:$port" else "$host:$port"

    companion object {
        private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
        private val ipv6Chars = Regex("""^[0-9a-fA-F:]+$""")
        private val domainLabel = Regex("""^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$""")
        private val countryCode = Regex("""^[a-zA-Z]{2}$""")

        fun parse(value: String?): AetherEndpoint? {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val (rawHost, rawPort) = if (trimmed.startsWith('[')) {
                val close = trimmed.indexOf(']')
                if (close < 0 || close + 2 > trimmed.length || trimmed[close + 1] != ':') return null
                trimmed.substring(1, close) to trimmed.substring(close + 2)
            } else {
                val colon = trimmed.lastIndexOf(':')
                if (colon <= 0 || trimmed.indexOf(':') != colon) return null
                trimmed.substring(0, colon) to trimmed.substring(colon + 1)
            }
            return of(rawHost, rawPort)
        }

        fun of(host: String?, port: String?): AetherEndpoint? {
            val cleanHost = stripBrackets(host?.trim() ?: return null)
            val portNum = port?.trim()?.toIntOrNull() ?: return null
            if (!isValidHost(cleanHost) || portNum !in 1..65535) return null
            return AetherEndpoint(cleanHost, portNum)
        }

        fun isValidHost(host: String): Boolean {
            val ipv4 = ipv4Regex.matchEntire(host)
            if (ipv4 != null) {
                return ipv4.groupValues.drop(1).all { (it.toIntOrNull() ?: 256) in 0..255 }
            }
            return isValidIpv6(host)
        }

        private fun isValidIpv6(host: String): Boolean {
            if (':' !in host || ":::" in host || !ipv6Chars.matches(host)) return false
            if (host.startsWith(':') && !host.startsWith("::")) return false
            if (host.endsWith(':') && !host.endsWith("::")) return false
            val doubleColons = host.windowed(2).count { it == "::" }
            if (doubleColons > 1) return false
            val groups = host.split(':').filter { it.isNotEmpty() }
            if (groups.any { it.length > 4 }) return false
            return if (doubleColons == 1) groups.size <= 7 else groups.size == 8
        }

        fun isValidDomain(value: String?): Boolean {
            val domain = value?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() && it.length <= 253 } ?: return false
            val labels = domain.split('.')
            if (labels.any { !domainLabel.matches(it) }) return false
            return labels.last().any { it.isLetter() }
        }

        /**
         * Parses an API address (`--api-address`): an IPv4 address, an IPv6 address (in brackets when a
         * port follows), or a domain name, with or without `:port`. Returns the normalized string as
         * the core takes it, or null when invalid.
         */
        fun parseHostOrAddress(value: String?): String? {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            parse(trimmed)?.let { return it.format() }
            val colon = trimmed.lastIndexOf(':')
            if (colon > 0 && trimmed.indexOf(':') == colon) {
                val host = trimmed.substring(0, colon)
                val port = trimmed.substring(colon + 1).toIntOrNull() ?: return null
                if (port !in 1..65535 || !isValidDomain(host)) return null
                return "$host:$port"
            }
            val bare = stripBrackets(trimmed)
            return when {
                isValidHost(bare) -> bare
                !trimmed.startsWith('[') && isValidDomain(trimmed) -> trimmed
                else -> null
            }
        }

        fun isValidEchDns(value: String?): Boolean {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return false
            return when {
                trimmed.startsWith("udp://") -> isSocketOrHost(trimmed.removePrefix("udp://"))
                trimmed.startsWith("tcp://") -> isSocketOrHost(trimmed.removePrefix("tcp://"))
                trimmed.startsWith("https://") -> isValidDohUrl(trimmed)
                else -> false
            }
        }

        private fun isSocketOrHost(address: String): Boolean =
            parse(address) != null || isValidHost(stripBrackets(address))

        private fun isValidDohUrl(value: String): Boolean {
            val parts = value.split('@')
            val urlPart = parts.first()
            val uri = try {
                URI(urlPart)
            } catch (_: Exception) {
                return false
            }
            if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null) return false
            val host = uri.host?.let(::stripBrackets)?.takeIf { it.isNotEmpty() } ?: return false
            if (!isValidHost(host) && !isValidDomain(host)) return false
            if (uri.port != -1 && uri.port !in 1..65535) return false

            var seenAddress = false
            var seenSni = false
            for (option in parts.drop(1)) {
                when {
                    option.startsWith("address=") -> {
                        if (seenAddress) return false
                        seenAddress = true
                        val target = option.removePrefix("address=")
                        if (!isValidHost(stripBrackets(target)) && !isValidDomain(target)) return false
                    }
                    option.startsWith("sni=") -> {
                        if (seenSni) return false
                        seenSni = true
                        val sni = option.removePrefix("sni=")
                        if (!isValidDomain(sni)) return false
                    }
                    else -> return false
                }
            }
            return true
        }

        fun isValidDnsList(value: String?): Boolean {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return true
            val items = trimmed.split(',')
            return items.isNotEmpty() && items.all { item ->
                val clean = item.trim()
                clean.isNotEmpty() && isSocketOrHost(clean)
            }
        }

        fun isValidExitLoc(value: String?): Boolean {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return true
            val body = trimmed.removePrefix("!").trim()
            if (body.isEmpty()) return false
            val codes = body.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            return codes.isNotEmpty() && codes.all { countryCode.matches(it) }
        }

        private fun stripBrackets(host: String): String =
            if (host.startsWith('[') && host.endsWith(']')) host.substring(1, host.length - 1) else host
    }
}
