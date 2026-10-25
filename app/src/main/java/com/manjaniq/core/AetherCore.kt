package com.manjaniq.core

/**
 * The Aether core a configuration runs on, as the arguments its process is started with, the
 * listeners among them. The core of a profile is built from the profile's settings, or is the
 * command line the profile carries in their place. A command line is read as written and run as
 * written, so that what the profile says is what runs.
 */
data class AetherCore(val arguments: List<String>, val exit: AetherExit = AetherExit.PLAIN) {

    /**
     * The loopback port the app dials: Psiphon's or Tor's listener when one of them runs inside the
     * tunnel and is what the app reaches, the core's own listener otherwise.
     */
    val port: Int get() = AetherCoreManager.listenerPortOf(arguments) ?: AetherCoreManager.socksPort

    /**
     * Every port the core listens on as its modes run, its HTTP proxies' too: the ports it is told,
     * and its defaults for the listeners it is not told about.
     */
    val ports: List<Int>
        get() = portsOf(boundListeners).distinct()

    /**
     * The first port two of the listeners the core holds at one time share, as a command written by hand may name;
     * null when each has a port of its own.
     */
    val sharedPort: Int?
        get() = listenerGroups.firstNotNullOfOrNull { group ->
            val seen = mutableSetOf<Int>()
            portsOf(group).firstOrNull { !seen.add(it) }
        }

    /**
     * The first option of a listener the core binds whose address the core does not take; null when it takes each.
     */
    val invalidListener: String?
        get() = boundListeners.firstOrNull { flag ->
            val address = AetherCoreManager.listenerAddressAfter(arguments, flag) ?: return@firstOrNull false
            !AetherCoreManager.isSocketAddress(address) && !(flag in HTTP_LISTENERS && (address.isEmpty() || address == "off"))
        }

    /**
     * The option of the listener the app dials when the core takes its address but the app does not reach it there;
     * null when it does, or when the command names none and the app gives it one.
     */
    val unreachableListener: String?
        get() {
            val flag = AetherCoreManager.listenerFlagOf(arguments)
            val address = AetherCoreManager.listenerAddressAfter(arguments, flag) ?: return null
            if (!AetherCoreManager.isSocketAddress(address)) return null
            return flag.takeUnless { AetherCoreManager.reachesLoopback(address.substringBeforeLast(':')) }
        }

    private fun portsOf(listeners: List<String>): List<Int> =
        listeners.mapNotNull { flag ->
            if (flag in arguments) AetherCoreManager.portAfter(arguments, flag) else AetherCoreManager.defaultListenerPorts[flag]
        }.filter { it != 0 }

    private val boundListeners: List<String>
        get() {
            val bound = listenerGroups.flatten().toSet()
            return LISTENERS.filter { it in bound }
        }

    private val listenerGroups: List<List<String>>
        get() {
            val tor = AetherCoreManager.torModeOf(arguments)
            val psiphon = AetherCoreManager.psiphonModeOf(arguments)
            if (AetherKeys.REGISTER in arguments) {
                return listOf(
                    LISTENERS.filter { flag ->
                        (tor == AetherTor.REVERSE && (flag == AetherCoreManager.TOR_BIND || flag == AetherCoreManager.TOR_HTTP)) ||
                            (psiphon == AetherPsiphon.REVERSE && (flag == AetherCoreManager.PSIPHON_BIND || flag == AetherCoreManager.PSIPHON_HTTP))
                    }
                )
            }
            if (AetherCoreManager.aloneOf(arguments) != null) {
                val aloneHttp = if (tor == AetherTor.ONLY) AetherCoreManager.TOR_HTTP else AetherCoreManager.PSIPHON_HTTP
                return listOf(listOf("--bind", AetherCoreManager.HTTP_PROXY), listOf("--bind", aloneHttp))
            }
            return listOf(LISTENERS.filter { flag ->
                when (flag) {
                    AetherCoreManager.TOR_BIND -> tor == AetherTor.CHAIN || tor == AetherTor.REVERSE
                    AetherCoreManager.TOR_HTTP -> tor != AetherTor.OFF
                    AetherCoreManager.PSIPHON_BIND -> psiphon == AetherPsiphon.CHAIN || psiphon == AetherPsiphon.REVERSE
                    AetherCoreManager.PSIPHON_HTTP -> psiphon != AetherPsiphon.OFF
                    else -> true
                }
            })
        }

    /** The protocol, which tells whose identity files the core uses. */
    val protocol: AetherProtocol get() = AetherCoreManager.protocolOf(arguments)

    /** The tunnel as the names of its parts from the outside in, carriers included; see [AetherCoreManager.pathOf]. */
    val path: List<String> get() = AetherCoreManager.pathOf(arguments)

    /** [path], with the WARP protocol named by [label], as a screen names it. */
    fun path(label: (AetherProtocol) -> String): List<String> = AetherCoreManager.pathOf(arguments, label)

    /** The command line a profile carries for this core; [ofCommand] reads it back. */
    val command: String get() = (listOf(COMMAND_NAME) + arguments).joinToString(" ", transform = ::quoted)

    /** Fallback function form of [command]. */
    fun command(): String = command

    /** True when a process started with [processArguments] runs this core, on whatever SOCKS ports and at whatever log level. */
    fun runsAs(processArguments: List<String>): Boolean =
        AetherCoreManager.tunnelArguments(processArguments) == AetherCoreManager.tunnelArguments(arguments)

    /** True when the core is told which proxy to dial out through, as a command written by hand may be. */
    val hasUpstream: Boolean get() = AetherCoreManager.UPSTREAM in arguments

    /**
     * This core dialling out through the SOCKS inbound on [port] of the loopback address.
     * A core told an upstream of its own keeps it.
     */
    fun through(port: Int): AetherCore =
        if (hasUpstream) this else copy(arguments = arguments + listOf(AetherCoreManager.UPSTREAM, "socks5://${AetherCoreManager.LOOPBACK}:$port"))

    companion object {
        const val COMMAND_NAME = "aether"
        const val DEFAULT_SOCKS_PORT = 1819
        const val LOOPBACK = "127.0.0.1"
        const val SOCKS_BIND = "--bind"
        const val UPSTREAM = "--upstream"

        private val LISTENERS = listOf(
            "--bind",
            AetherCoreManager.TOR_BIND,
            AetherCoreManager.PSIPHON_BIND,
            AetherCoreManager.HTTP_PROXY,
            AetherCoreManager.TOR_HTTP,
            AetherCoreManager.PSIPHON_HTTP,
        )

        private val HTTP_LISTENERS = setOf(
            AetherCoreManager.HTTP_PROXY,
            AetherCoreManager.TOR_HTTP,
            AetherCoreManager.PSIPHON_HTTP,
        )

        fun of(profile: ProfileItem, listenPort: Int = AetherCoreManager.socksPort): AetherCore {
            val core = profile.aetherCommand?.takeIf { it.isNotBlank() }?.let { ofCommand(it, listenPort) }
                ?: AetherCore(
                    AetherCoreManager.withoutOption(
                        AetherCoreManager.buildArguments(profile, listenPort),
                        "--log-level",
                    )
                )
            return core.copy(exit = AetherExit.of(profile))
        }

        fun ofCommand(command: String, listenPort: Int = AetherCoreManager.socksPort): AetherCore? {
            val arguments = readableArguments(command) ?: return null
            val listener = AetherCoreManager.listenerFlagOf(arguments)
            if (listener !in arguments) return AetherCore(AetherCoreManager.withListener(arguments, listener, listenPort))
            return AetherCore(arguments)
        }

        private fun readableArguments(command: String): List<String>? {
            val arguments = argumentsOf(command)
            if (arguments.isEmpty()) return null
            val listener = AetherCoreManager.listenerFlagOf(arguments)
            return arguments.takeIf { listener !in arguments || AetherCoreManager.portAfter(arguments, listener) != null }
        }

        fun leavesThroughWarp(profile: ProfileItem): Boolean {
            val arguments = profile.aetherCommand?.takeIf { it.isNotBlank() }?.let(::readableArguments)
            return if (arguments != null) {
                AetherCoreManager.leavesThroughWarp(arguments)
            } else {
                AetherCoreManager.leavesThroughWarp(
                    AetherTor.fromString(profile.aetherTor),
                    AetherPsiphon.fromString(profile.aetherPsiphon),
                )
            }
        }

        internal fun argumentsOf(command: String): List<String> {
            val words = words(command)
            return if (words.firstOrNull()?.startsWith("-") == false) words.drop(1) else words
        }

        internal fun words(command: String): List<String> {
            val words = mutableListOf<String>()
            val word = StringBuilder()
            var quote: Char? = null
            var open = false
            for (c in command) {
                when {
                    quote != null -> if (c == quote) quote = null else word.append(c)
                    c == '"' || c == '\'' -> {
                        quote = c
                        open = true
                    }
                    c.isWhitespace() -> if (open) {
                        words.add(word.toString())
                        word.setLength(0)
                        open = false
                    }
                    else -> {
                        word.append(c)
                        open = true
                    }
                }
            }
            if (open) words.add(word.toString())
            return words
        }

        private fun quoted(word: String): String =
            if (word.isEmpty() || word.any(Char::isWhitespace)) "\"$word\"" else word
    }
}
