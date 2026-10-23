package com.manjaniq.core.models

/**
 * Paths of the helper processes Aether spawns, resolved for one start.
 *
 * Aether runs two kinds of child process:
 *
 *  * `psiphon-tunnel-core`, spawned by `psiphon.rs:749` and pointed at by
 *    `--psiphon-bin` (`cli.rs`); every `--psiphon*` mode needs it;
 *  * a Tor pluggable transport, spawned by `bridges.rs` for an obfs4,
 *    snowflake or webtunnel bridge and named by `--tor-pt [name=]<path>`.
 *
 * Neither is on `PATH` under Android, and Android 10 (API 29) refuses to
 * `execve` a file inside the app's writable data directory, so the app resolves
 * them in the read-only native library directory and names the exact file on the
 * command line. See [com.manjaniq.core.AetherHelpers] for that resolution and
 * `scripts/ci/build_helpers.sh` for how the files get there.
 *
 * Everything is empty in [none], i.e. "no helper is available": a caller that
 * does not know about helpers (a unit test, a headless caller) keeps exactly the
 * behaviour it had before, and a route that needs one is told it is missing
 * instead of being handed a path that does not exist.
 */
data class AetherHelperPaths(
    /** Absolute path of a packaged `psiphon-tunnel-core`, or empty. */
    val psiphonBinary: String = "",
    /** Absolute path of a packaged Tor pluggable transport (lyrebird), or empty. */
    val ptBinary: String = "",
    /** Directory Aether may search for pluggable transports, or empty. */
    val ptDirectory: String = "",
    /** Writable directory for tor's state (`--tor-dir`), or empty. */
    val torDirectory: String = "",
    /** Writable directory for psiphon's state (`--psiphon-dir`), or empty. */
    val psiphonDirectory: String = ""
) {
    fun hasPsiphon(): Boolean = psiphonBinary.isNotEmpty()
    fun hasPluggableTransport(): Boolean = ptBinary.isNotEmpty() || ptDirectory.isNotEmpty()

    companion object {
        /** Nothing resolved: no helper is available to this caller. */
        val none = AetherHelperPaths()
    }
}
