package com.manjaniq.core

import android.content.Context
import com.manjaniq.core.models.AetherHelperPaths
import java.io.File

/**
 * Resolves the helper executables Aether spawns, under Android's W^X rules.
 *
 * WHAT NEEDS A HELPER
 * -------------------
 *  * `psiphon-tunnel-core` - `psiphon.rs:749` spawns it for every `--psiphon*`
 *    mode, and `--psiphon-bin` names it.
 *
 * That is the only child process this app has left: the Tor routes and their
 * pluggable transports were removed with the engine-options screen, so no Tor
 * helper is resolved or shipped any more.
 *
 * WHERE IT MAY LIVE
 * -----------------
 * Android 10 (API 29) enforces W^X: an app may not `execve` a file in its own
 * writable data directory (`filesDir`, `cacheDir`, code cache, no-backup dir).
 * The read-only native library directory is the one place a shipped executable
 * may be started from, so the helpers are built as `lib<name>.so` entries in
 * `jniLibs` (`scripts/ci/build_helpers.sh`) and the app is installed with
 * `extractNativeLibs=true` (`useLegacyPackaging = true` in app/build.gradle.kts),
 * which puts them there as real, executable files.
 *
 * This object therefore looks in `nativeLibraryDir` and nowhere else. It never
 * copies a helper into a writable directory to run it, and it refuses a path
 * outside that directory even if one is handed to it, so nothing here can turn
 * the data directory into an execution surface.
 *
 * The state the helper needs (psiphon's data) is the other way round: that
 * *must* be writable, so `--psiphon-dir` points into `filesDir`. Data is
 * writable, code is not.
 */
object AetherHelpers {

    /** Packaged name of the Psiphon client. */
    const val PSIPHON_FILE = "libpsiphon_tunnel_core.so"

    /** State directory created beside the identity under `filesDir`. */
    const val STATE_DIR = "aether"

    /**
     * The library directory as last seen by [configure].
     *
     * Volatile and nullable: a second process (or a unit test that never calls
     * [configure]) sees "no helper" rather than a half-initialised value.
     */
    @Volatile
    private var nativeLibraryDirectory: String? = null

    /** Records where this installation keeps its native libraries. */
    fun configure(context: Context) {
        nativeLibraryDirectory = try {
            context.applicationInfo?.nativeLibraryDir
        } catch (_: Throwable) {
            null
        }
    }

    /** The recorded library directory, or null when [configure] has not run. */
    val nativeLibraryDir: String? get() = nativeLibraryDirectory

    /**
     * Absolute path of the packaged Psiphon client, or null when it is not
     * shipped with this build.
     */
    fun psiphonBinary(): String? = executable(PSIPHON_FILE)

    /** True when this build ships a Psiphon client that can be started. */
    fun hasPsiphon(): Boolean = psiphonBinary() != null

    /**
     * The helper path for [name] inside the native library directory, or null.
     *
     * A file that exists but is not executable is reported as missing: the whole
     * point of resolving it is that Aether can start it.
     */
    private fun executable(name: String): String? {
        val directory = nativeLibraryDirectory ?: return null
        val candidate = File(directory, name)
        return if (isExecutableLibraryFile(candidate, directory)) candidate.absolutePath else null
    }

    /**
     * True when [file] is a real, executable file inside [libraryDirectory].
     *
     * Both halves matter: `canExecute` alone would accept anything the file
     * system allowed, and the containment check is what keeps a caller-supplied
     * path from moving execution into the writable data directory.
     */
    fun isExecutableLibraryFile(file: File, libraryDirectory: String): Boolean {
        if (!file.isFile || !file.canExecute()) return false
        val root = try {
            File(libraryDirectory).canonicalPath
        } catch (_: Throwable) {
            return false
        }
        val path = try {
            file.canonicalPath
        } catch (_: Throwable) {
            return false
        }
        // Compare with the separator so "/data/app/x/lib" cannot match
        // "/data/app/x/library-elsewhere".
        return path == root || path.startsWith(root + File.separator)
    }

    /**
     * Everything a start needs to name the helper, with its writable state
     * directory created under `filesDir`.
     *
     * Safe to call on any thread: it only reads the recorded directory and
     * creates two directories, ignoring a failure (a start that cannot create
     * one will fail later with the engine's own message rather than here).
     */
    fun paths(context: Context): AetherHelperPaths {
        configure(context)
        val base = File(context.filesDir, STATE_DIR)
        val psiphon = File(base, "psiphon")
        listOf(base, psiphon).forEach { dir ->
            try {
                dir.mkdirs()
            } catch (_: Throwable) {
                // Reported by the engine when it cannot use the directory.
            }
        }
        return AetherHelperPaths(
            psiphonBinary = psiphonBinary() ?: "",
            psiphonDirectory = psiphon.absolutePath
        )
    }
}
