package com.manjaniq.core

/**
 * PattNG port: the Aether core a configuration runs on, as the arguments its process is started
 * with. The core of a configuration is the command line it carries; a command line is read as
 * written and run as written, so that what a profile or a hand-written command says is what runs.
 * Two cores with the same arguments are one core, which is how one process serves several outbounds
 * as long as they dial out through the same [exit].
 *
 * Adapted to Manjaniq: this project loads `libaether.so` through the JNI bridge (`AetherBridge`)
 * rather than spawning a separate core process, so [AetherCore] describes the argument list that
 * `AetherBridge.coreStart` hands to `aether_core_start`, and the loopback port the app dials.
 *
 * The core is a pure value type: it holds no state, starts nothing, and opens no socket. The service
 * (`AetherService`) owns the process lifecycle and the readiness probe; this class only describes
 * what is to be started and where the app reaches it.
 */
data class AetherCore(
    /** The argument list handed to `aether_core_start` (the `aether` command line minus the program name). */
    val arguments: List<String>,

    /** The exit-node the core dials out through. */
    val exit: AetherExit = AetherExit.PLAIN,
) {

    /**
     * The loopback port the app dials: the port the `--bind` listener names, or Aether's own
     * default (`1819`) when the command leaves it out.
     */
    val port: Int
        get() = listenerPortOf(arguments)

    /** Every port the core listens on, default ports filled in where a listener is unnamed. */
    val ports: List<Int>
        get() = boundListeners.mapNotNull { flag ->
            portAfter(arguments, flag) ?: defaultListenerPorts[flag]
        }.filter { it != 0 }.distinct()

    /**
     * The first port two of the listeners the core binds at one time share, as a command written by
     * hand may name; null when each listener has a port of its own. With only the core's own
     * listener this is always null.
     */
    val sharedPort: Int?
        get() = null

    /** The command line a shell would write for this core, quotes included. */
    fun command(): String =
        (listOf(COMMAND_NAME) + arguments).joinToString(" ") { word ->
            if (word.isEmpty() || word.any { it.isWhitespace() }) "\"$word\"" else word
        }

    companion object {
        /** The name a command line starts with; the app runs its own copy of the core whatever the name says. */
        const val COMMAND_NAME = "aether"

        /** Aether's own default SOCKS listen port: 127.0.0.1:1819. */
        const val DEFAULT_SOCKS_PORT = 1819

        /** The loopback address the app dials the core on. */
        const val LOOPBACK = "127.0.0.1"

        /** The option that names the core's own SOCKS listener. */
        const val SOCKS_BIND = "--bind"

        /** The option that names the proxy the core dials out through. */
        const val UPSTREAM = "--upstream"

        /** The ports the core's SOCKS listeners take where a command does not name them. */
        val defaultListenerPorts: Map<String, Int> = mapOf(SOCKS_BIND to DEFAULT_SOCKS_PORT)

        /**
         * The listener options a core may be told to bind, in the order it binds them: its own
         * listener first (the one the app dials).
         */
        val boundListeners: List<String> = listOf(SOCKS_BIND)

        /**
         * The port a command names for [flag], or null when it names no port for it: an unparsable
         * value means the command names a listener whose port cannot be read.
         */
        fun portAfter(arguments: List<String>, flag: String): Int? {
            val at = arguments.indexOf(flag)
            return if (at < 0 || arguments.size <= at + 1) null else arguments[at + 1].substringAfterLast(":").toIntOrNull()
        }

        /** The port the core's own listener takes, or [DEFAULT_SOCKS_PORT] when the command names none. */
        fun listenerPortOf(arguments: List<String>): Int =
            portAfter(arguments, SOCKS_BIND) ?: DEFAULT_SOCKS_PORT

        /** The words of a command line: split on whitespace, quotes keep a word together. */
        fun words(command: String): List<String> {
            val words = mutableListOf<String>()
            var current = StringBuilder()
            var quote: Char? = null
            var open = false
            for (c in command) {
                when {
                    quote != null -> if (c == quote) quote = null else current.append(c)
                    c == '"' || c == '\'' -> { quote = c; open = true }
                    c.isWhitespace() -> if (open) { words.add(current.toString()); current.setLength(0); open = false }
                    else -> { current.append(c); open = true }
                }
            }
            if (open) words.add(current.toString())
            return words
        }

        /** The arguments of [command]: its [words], without a program name in front. */
        fun argumentsOf(command: String): List<String> {
            val words = words(command)
            return if (words.isEmpty() || !words.first().startsWith("-")) words.dropWhile { !it.startsWith("-") } else words
        }
    }
}
