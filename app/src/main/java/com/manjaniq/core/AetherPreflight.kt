package com.manjaniq.core

import com.manjaniq.core.models.AetherConfig
import java.io.File
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * The checks a start must pass before the engine is asked to run.
 *
 * Every failure this object reports was previously discovered *after* the core had
 * been started, as an engine error with no context, or not discovered at all:
 *
 *  * **The identity file.** `--config` is a path, and the core writes a freshly
 *    registered identity there (`cli.rs:285`, `account.rs`). A relative path is
 *    resolved against the process working directory, which on Android is `/`, so
 *    the core would either fail to write it or write it somewhere the app cannot
 *    read back on the next start. The path is resolved against `filesDir` here, and
 *    its directory is created - a nested `identityPath` used to have no directory at
 *    all.
 *  * **The SOCKS listen address.** Aether parses it with `SocketAddr::parse` and
 *    silently falls back to `127.0.0.1:1819` when it cannot (`lib.rs:115-118`), so a
 *    value the app accepted but the engine could not parse would look like a
 *    successful start on a port the user did not choose.
 *  * **A port that is already taken.** `socks::bind_listener` fails immediately, but
 *    the failure arrives as an engine message while the tunnel is being dialled.
 *    Binding the port for a moment first turns it into "port 1819 is already in use",
 *    before anything is spawned.
 *
 * The probe binds loopback only, holds the socket for the duration of the check and
 * closes it again; nothing is sent anywhere.
 */
object AetherPreflight {

    /** The absolute identity file for [cfg], or null when the path escapes [filesDir]. */
    fun identityFile(cfg: AetherConfig, filesDir: String): File? {
        val relative = cfg.identityPath.trim()
        if (relative.isEmpty()) return null
        // `--config` is a path the core writes to. An absolute path - or one that
        // climbs out with `..` - would put the identity outside the app's private
        // storage, where a later start could not read it and where Android may not
        // even allow the write.
        if (relative.startsWith("/") || relative.startsWith("\\")) return null
        val root = try {
            File(filesDir).canonicalFile
        } catch (_: Throwable) {
            return null
        }
        val candidate = try {
            File(root, relative).canonicalFile
        } catch (_: Throwable) {
            return null
        }
        val inside = candidate.path == root.path ||
            candidate.path.startsWith(root.path + File.separator)
        return if (inside) candidate else null
    }

    /**
     * The reason this configuration cannot be started, or null when it can.
     *
     * [filesDir] is the app's private directory; [isLoopbackPortFree] is injectable so
     * a test can describe a busy port without binding one.
     */
    fun check(
        cfg: AetherConfig,
        filesDir: String,
        isLoopbackPortFree: (host: String, port: Int) -> Boolean = ::isFree
    ): String? {
        val root = File(filesDir)
        if (!root.isDirectory) root.mkdirs()
        if (!root.isDirectory) {
            return "The app's data directory ($filesDir) is not available, so Aether cannot " +
                "store an identity or a configuration"
        }
        if (!root.canWrite()) {
            return "The app's data directory ($filesDir) is not writable, so Aether cannot " +
                "store an identity or a configuration"
        }

        val identity = identityFile(cfg, filesDir)
            ?: return "The identity path '${cfg.identityPath}' is not usable: it must be a " +
                "relative path inside the app's data directory (no leading '/', no '..')"
        prepareIdentity(identity)?.let { return it }

        // A listener this app has just stopped takes a moment to release its
        // port, and a start straight after a stop would otherwise be refused
        // with "already in use" while nothing is actually listening. The wait is
        // only ever spent when the port looks busy, and it is short: a port held
        // by another app is reported with the same message as before.
        var socksProblem: String? = null
        for (attempt in 0 until PORT_RETRY_ATTEMPTS) {
            socksProblem = socksPortProblem(cfg, isLoopbackPortFree)
            if (socksProblem == null) break
            if (attempt < PORT_RETRY_ATTEMPTS - 1) Thread.sleep(PORT_RETRY_DELAY_MS)
        }
        socksProblem?.let { return it }
        return httpPortProblem(cfg, isLoopbackPortFree)
    }

    /**
     * Makes sure the identity file can be written where the core is told to write
     * it, and that an identity already there stays private.
     *
     * `--config` names a file the *core* creates and owns: `cli.rs` turns it into
     * `AETHER_CONFIG` and `config::save` writes it with mode 0600 through a
     * temporary file and a rename (`config.rs:159-201`). Three things can be
     * wrong before the core ever sees the path, and each used to arrive as an
     * engine error in the middle of a start - or, worse, as no error at all:
     *
     *  * **The directory does not exist.** `identityPath` may contain
     *    subdirectories; the core's `create_dir_all` would create them, but a
     *    path this app cannot create is a configuration mistake worth naming
     *    before anything is spawned.
     *  * **The path names a directory, or is unreadable.** `config::load` would
     *    fail and quarantine it (renaming it to `*.corrupt`), which loses the
     *    identity silently.
     *  * **The directory is not writable.** Tested by actually creating, writing
     *    and removing a probe file: `canWrite()` on Android answers for the
     *    directory entry, not for whether this app's SELinux context may create a
     *    file in it.
     *
     * The file itself is never created here: an empty `aether.toml` is not a valid
     * identity (`config::load` would report a parse error and quarantine it), so
     * creating one would break the very start this check exists to protect.
     *
     * An identity that exists is tightened to owner-only if the platform allows
     * it. `config::save` writes 0600 itself; this also covers a file restored by
     * the platform or copied in with a wider mode.
     */
    fun prepareIdentity(identity: File): String? {
        val parent = identity.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            return "The directory for the identity file (${parent.absolutePath}) could not be " +
                "created, so Aether could not write the identity it registers"
        }
        if (identity.exists() && !identity.isFile) {
            return "The identity path (${identity.absolutePath}) is not a file, so Aether " +
                "cannot write the identity it registers there"
        }
        if (identity.isFile && !identity.canRead()) {
            return "The identity file (${identity.absolutePath}) is not readable by this app, " +
                "so Aether would register a new identity and lose the existing one"
        }
        val directory = parent ?: File(".")
        val probe = File(directory, ".aether-write-probe-${System.nanoTime()}")
        try {
            if (!probe.createNewFile()) {
                return "The directory for the identity file (${directory.absolutePath}) " +
                    "refuses a new file, so Aether could not write the identity it registers"
            }
            probe.writeText("probe")
            if (probe.length() <= 0L) {
                return "The directory for the identity file (${directory.absolutePath}) " +
                    "accepts a file but not its contents, so Aether could not write the identity"
            }
        } catch (t: Throwable) {
            return "The directory for the identity file (${directory.absolutePath}) is not " +
                "usable for writing (${t.message ?: t.javaClass.simpleName}), so Aether could " +
                "not write the identity it registers"
        } finally {
            probe.delete()
        }
        if (identity.isFile) {
            try {
                identity.setReadable(false, false)
                identity.setReadable(true, true)
                identity.setWritable(true, true)
                identity.setExecutable(false, false)
            } catch (_: Throwable) {
                // Best effort: the core writes 0600 itself, and a platform that
                // refuses the change is not a reason to refuse the start.
            }
        }
        return null
    }

    private fun socksPortProblem(
        cfg: AetherConfig,
        isLoopbackPortFree: (String, Int) -> Boolean
    ): String? {
        val literal = cfg.bindLiteral(cfg.socksBind.ifEmpty { DEFAULT_SOCKS })
        val host = literal.substringBeforeLast(":", "")
        val port = literal.substringAfterLast(":", "").toIntOrNull()
            ?: return "The SOCKS listen address '$literal' is not a host:port pair"
        if (host.isEmpty()) return "The SOCKS listen address '$literal' names no host"
        if (!isLoopbackPortFree(host, port)) {
            return "SOCKS5 $literal is already in use on this device. Another proxy or an " +
                "Aether that is still running holds it; stop that one, or choose another port"
        }
        return null
    }

    private fun httpPortProblem(
        cfg: AetherConfig,
        isLoopbackPortFree: (String, Int) -> Boolean
    ): String? {
        val raw = cfg.httpProxyBind.trim()
        if (raw.isEmpty()) return null
        val literal = cfg.bindLiteral(raw)
        val host = literal.substringBeforeLast(":", "")
        val port = literal.substringAfterLast(":", "").toIntOrNull()
            ?: return "The HTTP proxy address '$literal' is not a host:port pair"
        if (host.isEmpty()) return "The HTTP proxy address '$literal' names no host"
        if (!isLoopbackPortFree(host, port)) {
            return "HTTP proxy $literal is already in use on this device; stop what holds it, " +
                "or choose another port"
        }
        return null
    }

    /** True when nothing is listening on `host:port` right now. */
    fun isFree(host: String, port: Int): Boolean = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(host, port), 1)
        }
        true
    } catch (_: BindException) {
        false
    } catch (_: Throwable) {
        // An address the platform will not even try (a host that does not resolve,
        // for instance) is not a port conflict; the engine's own parse is the gate
        // for that, and validation has already rejected what it can.
        true
    }

    private const val DEFAULT_SOCKS = "127.0.0.1:1819"

    /** How often a busy listener port is probed again before it is reported. */
    private const val PORT_RETRY_ATTEMPTS = 3

    /** How long to wait between those probes. */
    private const val PORT_RETRY_DELAY_MS = 200L

    /**
     * True when [bind] is a loopback literal the engine can parse. Used by the panel
     * and by the tests; validation in [AetherConfig] is what refuses anything else.
     */
    fun isLoopbackBind(bind: String): Boolean {
        val literal = bind.trim()
        return literal.startsWith("127.0.0.1:") || literal.startsWith("localhost:") ||
            literal.startsWith("[::1]:")
    }
}
