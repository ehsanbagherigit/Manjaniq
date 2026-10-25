package com.manjaniq.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The identity folder Aether registers its keys into, and the key files it holds.
 *
 * Keys are written under the app's private `filesDir/aether` directory and moved into place only
 * once every key of a renewal is ready, so the keys in use are never lost to an interrupted
 * renewal, a cancellation, or the process being killed.
 */
object AetherIdentityManager {

    private const val TAG = "AetherIdentity"

    const val BASE_FILE = "aether.toml"
    const val MASQUE_FILE = "aether-masque.toml"
    const val MASQUE_INNER_FILE = "aether-masque-secondary.toml"
    const val WIREGUARD_FILE = "aether-wg.toml"
    const val WIREGUARD_INNER_FILE = "aether-wg-secondary.toml"
    const val MASQUE_GOOL_FILE = "aether-masque-gool.toml"

    /** The key files of every kind, in the order the core registers them. */
    val KEY_FILES = listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE, MASQUE_FILE, MASQUE_INNER_FILE, MASQUE_GOOL_FILE)

    private const val WORK_DIR = "aether"
    private const val RENEWAL_DIR = "aether-renewal"
    internal const val READY_MARK = "ready"
    private const val PREVIOUS_DIR = "aether-previous"

    private const val RENEW_TIMEOUT_MS = 4 * 60_000L
    private const val RENEW_THROUGH_CARRIER_TIMEOUT_MS = 10 * 60_000L
    private const val SOURCE = "aether-key"

    private val identityField = Regex("""^(device_id|ipv4|ipv6)\s*=\s*"([^"]*)"$""")
    private val multilineDelimiter = Regex("\"\"\"|'''")
    private val keysRegistered = Regex("""identities ready: \S""")

    private val renewal = Mutex()

    fun workDir(context: Context): File = File(context.filesDir, WORK_DIR)

    private fun renewalDir(context: Context): File = File(context.filesDir, RENEWAL_DIR)

    suspend fun status(context: Context, protocol: AetherProtocol): AetherIdentityStatus =
        withContext(Dispatchers.IO) {
            settle(context)
            status(workDir(context), protocol)
        }

    suspend fun keys(context: Context): List<AetherKey> =
        withContext(Dispatchers.IO) {
            settle(context)
            keys(workDir(context), KEY_FILES)
        }

    suspend fun renew(
        context: Context,
        kind: AetherKeyKind,
        arguments: List<String>,
        exit: AetherExit = AetherExit.PLAIN,
        onOutput: (String) -> Unit,
    ): List<AetherKey>? = renewal.withLock {
        val workDir = workDir(context)
        val renewalDir = renewalDir(context)
        val files = filesOf(kind)
        if (!renew(workDir, renewalDir, files) { register(context, arguments, exit, renewalDir, onOutput) }) return@withLock null
        withContext(Dispatchers.IO) {
            if (!settle(workDir, renewalDir)) {
                Log.w(TAG, "the new keys are ready but not all in place yet; the next core start moves the rest")
            }
            keys(workDir, files)
        }
    }

    fun settle(context: Context): Boolean = settleIn(context.filesDir)

    internal fun settleIn(filesDir: File): Boolean {
        if (!File(filesDir, PREVIOUS_DIR).deleteRecursively()) {
            Log.w(TAG, "the keys an interrupted renewal of an older version left could not all be removed")
        }
        return settle(File(filesDir, WORK_DIR), File(filesDir, RENEWAL_DIR))
    }

    private suspend fun register(
        context: Context,
        arguments: List<String>,
        exit: AetherExit,
        renewalDir: File,
        onOutput: (String) -> Unit,
    ): Boolean = AetherCoreManager.runUntil(
        context = context,
        arguments = AetherCoreManager.withInfoLines(arguments),
        exit = exit,
        timeoutMs = if (AetherCoreManager.reachesWarpThroughCarrier(arguments)) RENEW_THROUGH_CARRIER_TIMEOUT_MS else RENEW_TIMEOUT_MS,
        source = SOURCE,
        onOutput = onOutput,
        keysDir = renewalDir,
    ) { line -> line.takeIf(::isRegistered) } != null

    internal fun status(workDir: File, protocol: AetherProtocol): AetherIdentityStatus = when (protocol) {
        AetherProtocol.MASQUE -> AetherIdentityStatus(protocol, read(File(workDir, MASQUE_FILE)))
        AetherProtocol.WIREGUARD -> AetherIdentityStatus(protocol, read(File(workDir, WIREGUARD_FILE)))
        AetherProtocol.GOOL -> AetherIdentityStatus(
            protocol,
            read(File(workDir, WIREGUARD_FILE)),
            read(File(workDir, WIREGUARD_INNER_FILE)),
        )
        AetherProtocol.MIM -> AetherIdentityStatus(
            protocol,
            read(File(workDir, MASQUE_FILE)),
            read(File(workDir, MASQUE_INNER_FILE)),
        )
        AetherProtocol.WG_OVER_MASQUE -> AetherIdentityStatus(
            protocol,
            read(File(workDir, MASQUE_FILE)),
            read(File(workDir, MASQUE_GOOL_FILE)),
        )
    }

    fun filesOf(kind: AetherKeyKind): List<String> = when (kind) {
        AetherKeyKind.ALL -> KEY_FILES
        AetherKeyKind.WIREGUARD -> listOf(WIREGUARD_FILE)
        AetherKeyKind.MASQUE -> listOf(MASQUE_FILE)
        AetherKeyKind.GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherKeyKind.MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        AetherKeyKind.WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
    }

    fun filesOf(protocol: AetherProtocol): List<String> = when (protocol) {
        AetherProtocol.MASQUE -> listOf(MASQUE_FILE)
        AetherProtocol.WIREGUARD -> listOf(WIREGUARD_FILE)
        AetherProtocol.GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherProtocol.MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        AetherProtocol.WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
    }

    fun filesOf(protocol: String): List<String> = when (protocol) {
        "masque" -> listOf(MASQUE_FILE)
        "wg" -> listOf(WIREGUARD_FILE)
        "gool" -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        "mim" -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        "wg-over-masque" -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
        else -> emptyList()
    }

    fun filesNeededBy(arguments: List<String>): List<String> =
        if (AetherCoreManager.psiphonModeOf(arguments) == AetherPsiphon.ONLY || AetherCoreManager.torModeOf(arguments) == AetherTor.ONLY) {
            emptyList()
        } else {
            filesOf(AetherCoreManager.protocolOf(arguments))
        }

    suspend fun missing(context: Context, files: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            settle(context)
            missing(workDir(context), files)
        }

    internal fun missing(dir: File, files: List<String>): List<String> = files.filter { read(File(dir, it)) == null }

    internal fun keys(dir: File, files: List<String>): List<AetherKey> = files.map { AetherKey(it, read(File(dir, it))) }

    fun parse(text: String): AetherIdentity? {
        val fields = mutableMapOf<String, String>()
        var insideMultiline = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!insideMultiline) {
                identityField.matchEntire(line)?.destructured?.let { (key, value) -> fields.putIfAbsent(key, value) }
            }
            if (multilineDelimiter.findAll(line).count() % 2 == 1) {
                insideMultiline = !insideMultiline
            }
        }
        val deviceId = fields["device_id"]?.takeIf { it.isNotBlank() } ?: return null
        return AetherIdentity(deviceId, fields["ipv4"].orEmpty(), fields["ipv6"].orEmpty())
    }

    fun sharesIdentity(first: AetherProtocol, second: AetherProtocol): Boolean =
        first.overMasque == second.overMasque

    internal fun isRegistered(line: String): Boolean = keysRegistered.containsMatchIn(line)

    internal suspend fun renew(
        workDir: File,
        renewalDir: File,
        files: List<String>,
        io: CoroutineDispatcher = Dispatchers.IO,
        register: suspend () -> Boolean,
    ): Boolean {
        val cleared = withContext(io) {
            settle(workDir, renewalDir) && renewalDir.deleteRecursively() && renewalDir.mkdirs()
        }
        if (!cleared) return false
        val ready = AtomicBoolean(false)
        try {
            if (register()) {
                withContext(io) {
                    (KEY_FILES - files.toSet()).forEach { File(renewalDir, it).delete() }
                    ready.set(isComplete(renewalDir, files) && markReady(renewalDir))
                }
            }
        } finally {
            withContext(NonCancellable + io) {
                if (ready.get()) settle(workDir, renewalDir) else renewalDir.deleteRecursively()
            }
        }
        return ready.get()
    }

    internal fun settle(workDir: File, renewalDir: File): Boolean {
        if (!File(renewalDir, READY_MARK).isFile) return true
        workDir.mkdirs()
        for (name in KEY_FILES) {
            val renewed = File(renewalDir, name)
            if (renewed.isFile && !moveOver(renewed, File(workDir, name))) return false
        }
        return renewalDir.deleteRecursively()
    }

    private fun moveOver(source: File, target: File): Boolean = try {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    } catch (_: IOException) {
        !source.isFile
    }

    internal fun isComplete(dir: File, files: List<String>): Boolean = files.all { read(File(dir, it)) != null }

    private fun markReady(dir: File): Boolean = try {
        File(dir, READY_MARK).createNewFile()
    } catch (_: IOException) {
        false
    }

    fun read(file: File): AetherIdentity? = try {
        if (file.isFile) parse(file.readText()) else null
    } catch (_: IOException) {
        null
    }
}
