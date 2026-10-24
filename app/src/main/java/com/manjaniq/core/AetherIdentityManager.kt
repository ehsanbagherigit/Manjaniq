package com.manjaniq.core

import android.content.Context
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * PattNG port: the identity folder Aether registers its keys into, and the key files it holds.
 *
 * Keys are written under the app's private `filesDir/aether` directory and moved into place only
 * once every key of a renewal is ready, so the keys in use are never lost to an interrupted
 * renewal, a cancellation, or the process being killed. Nothing here ever writes outside
 * [workDir]: an identity path that climbs out of the app's data directory is refused before the
 * core ever sees it.
 *
 * Adapted to Manjaniq: [workDir] resolves from `context.filesDir` exactly as
 * [com.manjaniq.core.AetherPreflight] resolves the identity file, and the core's own argument list
 * (built by `AetherConfig.toAetherArgs` and started through `AetherBridge.coreStart`) names the
 * config file it writes into this folder.
 */
object AetherIdentityManager {

    /** The identity file the core writes and registers its keys into. */
    const val BASE_FILE = "aether.toml"

    /** The MASQUE key file, written beside [BASE_FILE] as the core names it. */
    const val MASQUE_FILE = "aether-masque.toml"

    /** The secondary MASQUE key file used by a two-hop tunnel. */
    const val MASQUE_INNER_FILE = "aether-masque-secondary.toml"

    /** The WireGuard key file. */
    const val WIREGUARD_FILE = "aether-wg.toml"

    /** The secondary WireGuard key file used by a two-hop tunnel. */
    const val WIREGUARD_INNER_FILE = "aether-wg-secondary.toml"

    /** The WireGuard-over-MASQUE key file, beside [MASQUE_FILE] as the core names it. */
    const val MASQUE_GOOL_FILE = "aether-masque-gool.toml"

    /** The key files of every kind, in the order the core registers them. */
    val KEY_FILES = listOf(
        WIREGUARD_FILE,
        WIREGUARD_INNER_FILE,
        MASQUE_FILE,
        MASQUE_INNER_FILE,
        MASQUE_GOOL_FILE
    )

    /** Where a renewal gathers its new keys; the keys in use stay in [WORK_DIR] until they are ready. */
    private const val RENEWAL_DIR = "aether-renewal"

    /** Written into [RENEWAL_DIR] once every new key of a renewal is there. */
    private const val READY_MARK = "ready"

    /** Where a renewal before [RENEWAL_DIR] moved the keys in use while it registered new ones. */
    private const val PREVIOUS_DIR = "aether-previous"

    /** One line of a key file: `device_id = "..."`, `ipv4 = "..."` or `ipv6 = "..."`. */
    private val identityField = Pattern.compile("""^(device_id|ipv4|ipv6)\s*=\s*"([^"]*)""$""")

    /** Triple-quote delimiters that open or close a multiline value. */
    private val multilineDelimiter = Pattern.compile('"""|''''')

    /** The core's last word in a registration, once every key it was asked for is saved. */
    private val keysRegistered = Pattern.compile("""identities ready: \S""")

    /** The maximum time a registration is allowed to run before it is treated as failed. */
    private const val REGISTER_TIMEOUT_MS = 4L * 60_000L

    /** One renewal at a time: renewals share [RENEWAL_DIR]. */
    private val renewal = java.util.concurrent.locks.ReentrantLock()

    /**
     * The identity folder: `filesDir/aether`, created on demand. This is the directory the core's
     * identity (`--config`) lives in.
     */
    fun workDir(context: Context): File = File(context.filesDir, WORK_DIR).apply { mkdirs() }

    private const val WORK_DIR = "aether"

    private fun renewalDir(context: Context): File = File(context.filesDir, RENEWAL_DIR)

    /**
     * Removes [PREVIOUS_DIR] from `filesDir` (keys an interrupted renewal of an older build left
     * behind) and settles the identity and renewal folders; the keys in use stay as they are.
     * Safe to call before every use of Aether.
     */
    fun settle(context: Context): Boolean {
        File(context.filesDir, PREVIOUS_DIR).deleteRecursively()
        return settle(workDir(context), File(context.filesDir, RENEWAL_DIR))
    }

    /**
     * Moves the new keys of a ready renewal over the keys in use, one after another, then removes
     * the renewal folder. Without the ready mark there is nothing to move and the keys in use stay.
     * True when no renewal with ready keys waits any longer.
     */
    internal fun settle(workDir: File, renewalDir: File): Boolean {
        if (!File(renewalDir, READY_MARK).isFile) return true
        workDir.mkdirs()
        for (name in KEY_FILES) {
            val renewed = File(renewalDir, name)
            if (renewed.isFile && !moveOver(renewed, File(workDir, name))) return false
        }
        return renewalDir.deleteRecursively()
    }

    /** Replaces [target] by [source] in one atomic step; true once [source] is gone. */
    private fun moveOver(source: File, target: File): Boolean =
        try {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            true
        } catch (_: Exception) {
            !source.isFile
        }

    /** Every key of the identity folder, each of [KEY_FILES] with the identity it holds. */
    fun keys(context: Context): List<AetherKey> =
        KEY_FILES.map { AetherKey(it, read(File(workDir(context), it))) }

    /** Which of [files] the identity folder lacks: not there, or not readable as a key. */
    fun missing(context: Context, files: List<String>): List<String> =
        files.filter { read(File(workDir(context), it)) == null }

    /** The identity [file] holds, or null when it is missing or unreadable as a key. */
    fun read(file: File): AetherIdentity? =
        if (file.isFile) parse(file.readText()) else null

    /**
     * Parses the text of a key file into an [AetherIdentity]. `device_id` is mandatory; `ipv4` and
     * `ipv6` default to empty when absent.
     */
    fun parse(text: String): AetherIdentity? {
        val fields = mutableMapOf<String, String>()
        var insideMultiline = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!insideMultiline) {
                val matcher: Matcher = identityField.matcher(line)
                if (matcher.matches()) {
                    fields.putIfAbsent(matcher.group(1), matcher.group(2))
                }
            }
            val delimiters = multilineDelimiter.matcher(line)
            var count = 0
            while (delimiters.find()) count++
            if (count % 2 == 1) insideMultiline = !insideMultiline
        }
        val deviceId = fields["device_id"] ?: return null
        return AetherIdentity(deviceId, fields.getOrElse("ipv4") { "" }, fields.getOrElse("ipv6") { "" })
    }

    /** The key files a tunnel of [protocol] uses: its own, and the inner hop's for a two-hop one. */
    fun filesOf(protocol: String): List<String> = when (protocol) {
        "masque" -> listOf(MASQUE_FILE)
        "wg" -> listOf(WIREGUARD_FILE)
        "gool" -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        "mim" -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        else -> emptyList()
    }
}
