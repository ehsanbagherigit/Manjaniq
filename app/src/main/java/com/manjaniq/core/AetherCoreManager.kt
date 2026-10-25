package com.manjaniq.core

import android.content.Context
import android.os.Process as AndroidProcess
import android.util.Log
import com.manjaniq.AetherBridge
import com.manjaniq.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

object AetherCoreManager {

    private const val TAG = "AetherCoreManager"

    const val LOOPBACK = "127.0.0.1"
    const val DEFAULT_SOCKS_PORT = 1819
    const val MIN_SOCKS_PORT = 1024
    const val MAX_SOCKS_PORT = 65532
    const val PORT_SPAN = 3

    const val AETHER_MASQUE_SNI = "www.cloudflare.com"
    const val AETHER_ECH_DNS = "udp://1.1.1.1"
    const val AETHER_ECH_DOMAIN = "cloudflare-ech.com"

    private const val BINARY_NAME = "libaether.so"
    private const val TOR_LYREBIRD_BINARY = "libtor_lyrebird.so"
    private const val TOR_CONJURE_BINARY = "libtor_conjure.so"
    private const val PSIPHON_BINARY = "libpsiphon.so"
    private const val TOR_DIR_NAME = "tor"
    private const val TOR_PROBE_DIR_NAME = "tor-probe"
    private const val TOR_BRIDGES_FILE_NAME = "tor-bridges.txt"
    internal const val PSIPHON_DIR_NAME = "psiphon"
    internal const val PSIPHON_PROBE_DIR_NAME = "psiphon-probe"
    private const val STARTUP_TIMEOUT_MS = 90_000L
    private const val CARRIER_STARTUP_TIMEOUT_MS = 8 * 60_000L
    private const val SHUTDOWN_WAIT_MS = 500L
    private const val SOCKS_POLL_INTERVAL_MS = 200L
    private const val SOCKS_CONNECT_TIMEOUT_MS = 300
    internal const val SOCKS_GREETING_TIMEOUT_MS = 100
    private const val SOURCE = "aether"

    internal const val OWNER_PID_ENV = "AETHER_OWNER_PID"
    internal const val SESSION_ENV = "AETHER_SESSION"
    internal const val EXIT_ENV = "AETHER_EXIT"

    const val TOR_BIND = "--tor-bind"
    const val PSIPHON_BIND = "--psiphon-bind"
    const val HTTP_PROXY = "--http-proxy"
    const val TOR_HTTP = "--tor-http"
    const val PSIPHON_HTTP = "--psiphon-http"
    const val UPSTREAM = "--upstream"
    const val PSIPHON_SERVER_ENTRIES = "--psiphon-server-entries"
    const val SHIPPED_LIST = "shipped-list"

    const val CORE_DEFAULT_SOCKS_PORT = 1819
    const val CORE_DEFAULT_TOR_PORT = 1820
    const val CORE_DEFAULT_PSIPHON_PORT = 1821

    internal val defaultListenerPorts = mapOf(
        "--bind" to CORE_DEFAULT_SOCKS_PORT,
        TOR_BIND to CORE_DEFAULT_TOR_PORT,
        PSIPHON_BIND to CORE_DEFAULT_PSIPHON_PORT,
    )

    internal val flagsWithValue = setOf(
        "--bind",
        "--http-proxy",
        "--upstream",
        "--mark",
        "--exit-loc",
        "--exit-loc-secs",
        "--stats-secs",
        "--ip",
        "--peer",
        "--wg-peer",
        "--gool-peer",
        "--wiw-outer", "--gool-outer", "--outer-peer",
        "--wiw-inner", "--gool-inner", "--inner-peer",
        "--wiw-peers", "--gool-peers",
        "--mim-outer",
        "--mim-inner",
        "--mim-peers",
        "--protocol",
        "--scan",
        "--noize",
        "--masque-sni",
        "--h2-peer",
        "--ech",
        "--ech-dns",
        "--ech-domain",
        "--tls-groups",
        "--tls-key-shares",
        "--tls13-ciphers",
        "--tls-ciphers",
        "--tls-sigalgs",
        "--tls-cert-compression",
        "--tls-alps",
        "--tls-trust-anchors",
        "--tls-server-padding",
        "--validate-secs",
        "--startup-secs",
        "--reconnect-secs",
        "--dns",
        "--fragment-size",
        "--fragment-delay",
        "--keepalive",
        "--config",
        "--wg-config",
        "--masque-config",
        "--register",
        "--api-address",
        "--enroll-address",
        "--api-sni",
        "--api-host",
        "--team", "--organization",
        "--access-id",
        "--access-secret",
        "--access-token",
        "--access-email",
        "--route-block",
        "--route-direct",
        "--routes",
        "--perf",
        "--log-level",
        "--tor-bridge",
        "--tor-bridge-file",
        "--tor-relays",
        "--tor-relay-ports",
        "--tor-pt",
        "--tor-pt-dir",
        "--tor-bind",
        "--tor-http",
        "--tor-dir",
        "--psiphon-config",
        "--psiphon-mode",
        "--psiphon-cdn-ips",
        "--psiphon-cdn-sni",
        "--psiphon-cdn-sets",
        "--psiphon-bind",
        "--psiphon-http",
        "--psiphon-region",
        "--psiphon-dir",
        "--psiphon-bin",
        "--psiphon-server-entries",
    )

    private val wiwSelectionFlags = setOf(
        "--wiw-outer", "--gool-outer", "--outer-peer",
        "--wiw-inner", "--gool-inner", "--inner-peer",
        "--wiw-peers", "--gool-peers",
        "--wiw-scan", "--gool-scan",
    )
    private val mimSelectionFlags = setOf("--mim-outer", "--mim-inner", "--mim-peers", "--mim-scan")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socksAnswers = SocksAnswers(SOCKS_GREETING_TIMEOUT_MS, SOCKS_CONNECT_TIMEOUT_MS)

    @Volatile
    private var process: Process? = null

    @Volatile
    private var activeJobHandle: Long? = null

    @Volatile
    private var activeCoreArgs: List<String>? = null

    @Volatile
    private var activeExitKey: String? = null

    @Volatile
    private var outputJob: Job? = null

    @Volatile
    private var monitorJob: Job? = null

    @Volatile
    private var readyWordSeen = false

    @Volatile
    private var noEchKey = false

    @Volatile
    private var chainEndReason: String? = null

    val socksPort: Int
        get() = parseSocksPort(AetherSettingsStorage.getListenPort())

    fun parseSocksPort(raw: String?): Int =
        raw?.trim()?.toIntOrNull()?.takeIf { it in MIN_SOCKS_PORT..MAX_SOCKS_PORT } ?: DEFAULT_SOCKS_PORT

    fun secondaryPort(socksPort: Int = this.socksPort): Int = socksPort + PORT_SPAN

    fun portsOf(socksPort: Int = this.socksPort): IntRange = socksPort..secondaryPort(socksPort)

    fun binaryFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    fun hasBinary(context: Context): Boolean =
        AetherBridge.isNativeAvailable || binaryFile(context).canExecute()

    fun torLyrebirdFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, TOR_LYREBIRD_BINARY)

    fun torConjureFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, TOR_CONJURE_BINARY)

    fun hasTorTransports(context: Context): Boolean =
        torLyrebirdFile(context).canExecute() && torConjureFile(context).canExecute()

    fun psiphonFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, PSIPHON_BINARY)

    fun hasPsiphon(context: Context): Boolean =
        psiphonFile(context).canExecute()

    fun isRunning(context: Context? = null): Boolean =
        activeJobHandle != null || process?.isAlive == true || (context != null && sessionProcess(context) != null)

    suspend fun start(
        context: Context,
        profile: ProfileItem,
        onOutput: (String) -> Unit,
        onUnexpectedExit: () -> Unit,
    ): Boolean = start(context, AetherCore.of(profile), onOutput, onUnexpectedExit)

    suspend fun start(
        context: Context,
        core: AetherCore,
        onOutput: (String) -> Unit,
        onUnexpectedExit: () -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        stop(context)

        if (!hasBinary(context)) {
            Log.e(TAG, "Aether library is not available")
            return@withContext false
        }

        val workDir = AetherIdentityManager.workDir(context)
        if (!workDir.exists() && !workDir.mkdirs()) {
            Log.e(TAG, "Failed to create working directory: ${workDir.absolutePath}")
            return@withContext false
        }
        AetherIdentityManager.settle(context)

        val arguments = withInfoLines(core.arguments)
        val port = core.port
        prepareCarrierFiles(context, arguments, workDir)
        val fullArguments = fullArguments(context, workDir, arguments, isSession = true)

        readyWordSeen = false
        noEchKey = false
        chainEndReason = null

        val startupTimeout = if (usesTor(arguments) || usesPsiphon(arguments)) {
            CARRIER_STARTUP_TIMEOUT_MS
        } else {
            STARTUP_TIMEOUT_MS
        }
        val needsWord = readyNeedsWord(arguments)

        if (AetherBridge.isNativeAvailable) {
            AetherBridge.initialize(context)
            AetherBridge.envReset()
            val cursorStart = currentLogCursor()
            val startResult = AetherBridge.coreStart(fullArguments)
            val handle = startResult.getOrNull()?.optLong("job", -1L)?.takeIf { it >= 0L }
            if (handle == null) {
                Log.e(TAG, "Failed to start Aether core via JNI: ${startResult.getErrorOrNull()}")
                return@withContext false
            }
            activeJobHandle = handle
            activeCoreArgs = core.arguments
            activeExitKey = core.exit.key

            monitorJob = scope.launch {
                var cursor = cursorStart
                while (isActive && activeJobHandle == handle) {
                    val batch = AetherBridge.logLines(cursor).getOrNull()
                    if (batch != null) {
                        for (entry in batch) {
                            if (entry.seq > cursor) cursor = entry.seq
                            val line = entry.text
                            if (line.isNotEmpty()) {
                                handleCoreLine(line, onOutput)
                            }
                        }
                    }
                    val poll = AetherBridge.jobPoll(handle).getOrNull()
                    if (poll != null && poll.optString("state") == "done") {
                        val res = poll.optJSONObject("result")
                        if (res != null && !res.optBoolean("ok", true)) {
                            val err = res.optString("error", "")
                            if (err.isNotBlank()) handleCoreLine("[-] $err", onOutput)
                        }
                        if (activeJobHandle == handle) {
                            activeJobHandle = null
                            activeCoreArgs = null
                            activeExitKey = null
                            AetherBridge.jobFree(handle)
                            onUnexpectedExit()
                        }
                        return@launch
                    }
                    val crashed = chainEndReason
                    if (crashed != null && activeJobHandle == handle) {
                        Log.w(TAG, "Ending session after carrier inside tunnel exited: $crashed")
                        activeJobHandle = null
                        activeCoreArgs = null
                        activeExitKey = null
                        AetherBridge.jobCancel(handle)
                        AetherBridge.jobFree(handle)
                        onUnexpectedExit()
                        return@launch
                    }
                    delay(120)
                }
            }

            val ready = waitForSocks(port, startupTimeout, needsWord) { activeJobHandle == handle }
            if (!ready) {
                Log.e(TAG, "Aether did not become ready on $LOOPBACK:$port within ${startupTimeout}ms")
                stop(context)
            }
            return@withContext ready
        }

        val binary = binaryFile(context)
        val started = try {
            ProcessBuilder(listOf(binary.absolutePath) + fullArguments)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    environment()[OWNER_PID_ENV] = AndroidProcess.myPid().toString()
                    environment()[SESSION_ENV] = "1"
                    environment()[EXIT_ENV] = core.exit.key
                }
                .start()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start Aether process", e)
            return@withContext false
        }

        process = started
        outputJob = scope.launch {
            streamProcessOutput(started, SOURCE) { line ->
                handleCoreLine(line, onOutput)
            }
        }
        monitorJob = scope.launch {
            val code = runCatching { started.waitFor() }.getOrNull()
            if (process === started) {
                process = null
                Log.w(TAG, "Aether exited unexpectedly with code $code")
                onUnexpectedExit()
            }
        }

        val ready = waitForSocks(port, startupTimeout, needsWord) { process === started && started.isAlive }
        if (!ready) {
            Log.e(TAG, "Aether did not open SOCKS on $LOOPBACK:$port within ${startupTimeout}ms")
            stop(context)
        }
        ready
    }

    private fun handleCoreLine(line: String, onOutput: (String) -> Unit) {
        if (!readyWordSeen && isReadyWord(line)) {
            readyWordSeen = true
        }
        if (!noEchKey && isNoEchKeyWord(line)) {
            noEchKey = true
        }
        PsiphonServerList.regionsOf(line)?.let(PsiphonServerList::remember)
        val ended = chainEndReasonOf(line)
        if (ended != null && chainEndReason == null) {
            chainEndReason = ended
            process?.destroy()
        }
        onOutput(line)
    }

    private fun currentLogCursor(): Long {
        val lines = AetherBridge.logLines(0).getOrNull() ?: return 0L
        return lines.lastOrNull()?.seq ?: 0L
    }

    fun stop(context: Context? = null) {
        val handle = activeJobHandle
        activeJobHandle = null
        activeCoreArgs = null
        activeExitKey = null
        if (handle != null) {
            AetherBridge.jobCancel(handle)
            AetherBridge.jobFree(handle)
        }

        val current = process
        process = null
        monitorJob?.cancel()
        monitorJob = null
        outputJob?.cancel()
        outputJob = null
        if (current != null) {
            terminateProcess(current)
        }
        if (context != null) {
            killRunning(context, sessionOnly = true)
        }
    }

    internal fun stoppedMessage(context: Context): String {
        val reason = chainEndReason
        return when {
            noEchKey -> context.getString(R.string.aether_core_stopped_no_ech_key)
            reason != null -> context.getString(R.string.aether_core_stopped_chain_ended, reason)
            else -> context.getString(R.string.aether_core_stopped)
        }
    }

    suspend fun <T> runUntil(
        context: Context,
        arguments: List<String>,
        exit: AetherExit = AetherExit.PLAIN,
        timeoutMs: Long,
        source: String,
        onOutput: (String) -> Unit,
        keysDir: File? = null,
        matcher: (String) -> T?,
    ): T? = withProcess(context, arguments, exit, source, onOutput, keysDir) { output ->
        withTimeoutOrNull(timeoutMs) {
            for (line in output) {
                matcher(line)?.let { return@withTimeoutOrNull it }
            }
            null
        }
    }

    suspend fun <T> withProcess(
        context: Context,
        arguments: List<String>,
        exit: AetherExit = AetherExit.PLAIN,
        source: String,
        onOutput: (String) -> Unit,
        keysDir: File? = null,
        configuration: String? = null,
        block: suspend (ReceiveChannel<String>) -> T,
    ): T? = withContext(Dispatchers.IO) {
        if (!hasBinary(context)) {
            Log.e(TAG, "$source: Aether library is not available")
            return@withContext null
        }

        val workDir = AetherIdentityManager.workDir(context)
        val keysFolder = keysDir ?: workDir
        if ((!workDir.exists() && !workDir.mkdirs()) || (!keysFolder.exists() && !keysFolder.mkdirs())) {
            Log.e(TAG, "$source: failed to create directory")
            return@withContext null
        }

        prepareCarrierFiles(context, arguments, workDir, isSession = false)
        val fullArguments = fullArguments(context, keysFolder, arguments, isSession = false)

        if (AetherBridge.isNativeAvailable) {
            AetherBridge.initialize(context)
            AetherBridge.envReset()
            val startCursor = currentLogCursor()
            val startResult = AetherBridge.coreStart(fullArguments)
            val handle = startResult.getOrNull()?.optLong("job", -1L)?.takeIf { it >= 0L }
            if (handle == null) {
                Log.e(TAG, "$source: failed to start Aether job: ${startResult.getErrorOrNull()}")
                return@withContext null
            }
            val lines = Channel<String>(Channel.UNLIMITED)
            return@withContext coroutineScope {
                val reader = launch {
                    var cursor = startCursor
                    try {
                        while (isActive) {
                            val batch = AetherBridge.logLines(cursor).getOrNull()
                            if (batch != null) {
                                for (entry in batch) {
                                    if (entry.seq > cursor) cursor = entry.seq
                                    val text = entry.text
                                    if (text.isNotEmpty()) {
                                        PsiphonServerList.regionsOf(text)?.let(PsiphonServerList::remember)
                                        onOutput(text)
                                        lines.trySend(text)
                                    }
                                }
                            }
                            val poll = AetherBridge.jobPoll(handle).getOrNull()
                            if (poll != null && poll.optString("state") == "done") {
                                val res = poll.optJSONObject("result")
                                if (res != null && !res.optBoolean("ok", true)) {
                                    val err = res.optString("error", "")
                                    if (err.isNotBlank()) {
                                        val msg = "[-] $err"
                                        onOutput(msg)
                                        lines.trySend(msg)
                                    }
                                }
                                break
                            }
                            delay(100)
                        }
                    } finally {
                        lines.close()
                    }
                }
                try {
                    block(lines)
                } finally {
                    withContext(NonCancellable) {
                        lines.close()
                        reader.cancel()
                        AetherBridge.jobCancel(handle)
                        AetherBridge.jobFree(handle)
                    }
                }
            }
        }

        val binary = binaryFile(context)
        val started = try {
            ProcessBuilder(listOf(binary.absolutePath) + fullArguments)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply { environment()[OWNER_PID_ENV] = AndroidProcess.myPid().toString() }
                .start()
        } catch (e: IOException) {
            Log.e(TAG, "$source: failed to start Aether process", e)
            return@withContext null
        }
        val lines = Channel<String>(Channel.UNLIMITED)
        coroutineScope {
            val reader = launch {
                try {
                    streamProcessOutput(started, source) { line ->
                        PsiphonServerList.regionsOf(line)?.let(PsiphonServerList::remember)
                        onOutput(line)
                        lines.trySend(line)
                    }
                } finally {
                    lines.close()
                }
            }
            try {
                block(lines)
            } finally {
                withContext(NonCancellable) {
                    lines.close()
                    terminateProcess(started)
                    reader.cancel()
                }
            }
        }
    }

    private fun prepareCarrierFiles(context: Context, arguments: List<String>, workDir: File, isSession: Boolean = true) {
        if (usesTor(arguments)) {
            torStateDir(context, isSession).mkdirs()
            val bridgeLines = valuesAfter(arguments, "--tor-bridge")
            if (bridgeLines.isNotEmpty()) {
                try {
                    torBridgesFile(context, isSession).writeText(bridgeLines.joinToString("\n", postfix = "\n"))
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to write Tor bridge lines file", e)
                }
            }
        }
        if (usesPsiphon(arguments)) {
            psiphonStateDir(context, isSession).mkdirs()
        }
    }

    private fun fullArguments(
        context: Context,
        keysDir: File,
        arguments: List<String>,
        isSession: Boolean,
    ): List<String> {
        val base = File(keysDir, AetherIdentityManager.BASE_FILE)
        val wg = File(keysDir, AetherIdentityManager.WIREGUARD_FILE)
        val masque = File(keysDir, AetherIdentityManager.MASQUE_FILE)
        val workDir = AetherIdentityManager.workDir(context)
        val serverEntries = if (usesPsiphon(arguments)) {
            PsiphonServerList.entriesFile(context.filesDir, workDir) { e ->
                Log.w(TAG, "Bundled Psiphon server list could not be unpacked", e)
            }
        } else {
            null
        }
        return listOf(
            "--config", base.absolutePath,
            "--wg-config", wg.absolutePath,
            "--masque-config", masque.absolutePath,
        ) + runtimeArguments(
            arguments = arguments,
            torStateDir = torStateDir(context, isSession),
            torBridgesFile = torBridgesFile(context, isSession),
            lyrebirdBinary = torLyrebirdFile(context).takeIf { it.canExecute() },
            conjureBinary = torConjureFile(context).takeIf { it.canExecute() },
            psiphonStateDir = psiphonStateDir(context, isSession),
            psiphonBinary = psiphonFile(context).takeIf { it.canExecute() },
            psiphonServerEntries = serverEntries,
        )
    }

    internal fun withShippedList(arguments: List<String>, entries: File?): List<String> {
        val at = arguments.indexOf(PSIPHON_SERVER_ENTRIES)
        if (at < 0 || arguments.getOrNull(at + 1) != SHIPPED_LIST) return arguments
        return if (entries == null) {
            arguments.filterIndexed { index, _ -> index != at && index != at + 1 }
        } else {
            arguments.toMutableList().also { it[at + 1] = entries.absolutePath }
        }
    }

    internal fun runtimeArguments(
        arguments: List<String>,
        torStateDir: File,
        torBridgesFile: File,
        lyrebirdBinary: File?,
        conjureBinary: File?,
        psiphonStateDir: File,
        psiphonBinary: File?,
        psiphonServerEntries: File? = null,
    ): List<String> {
        val withEntries = withShippedList(arguments, psiphonServerEntries)
        val withoutInlineBridges = withoutOption(withEntries, "--tor-bridge")
        val hasInlineBridges = withoutInlineBridges.size != withEntries.size
        return buildList {
            addAll(withoutInlineBridges)
            if (usesTor(withoutInlineBridges)) {
                if ("--tor-dir" !in withoutInlineBridges) {
                    addAll(listOf("--tor-dir", torStateDir.absolutePath))
                }
                if (hasInlineBridges && "--tor-bridge-file" !in withoutInlineBridges) {
                    addAll(listOf("--tor-bridge-file", torBridgesFile.absolutePath))
                }
                if ("--tor-pt" !in withoutInlineBridges && "--tor-pt-dir" !in withoutInlineBridges) {
                    if (lyrebirdBinary != null) {
                        addAll(listOf("--tor-pt", "obfs4=${lyrebirdBinary.absolutePath}"))
                        addAll(listOf("--tor-pt", "webtunnel=${lyrebirdBinary.absolutePath}"))
                    }
                    if (conjureBinary != null) {
                        addAll(listOf("--tor-pt", "conjure=${conjureBinary.absolutePath}"))
                    }
                }
            }
            if (usesPsiphon(withoutInlineBridges)) {
                if ("--psiphon-dir" !in withoutInlineBridges) {
                    addAll(listOf("--psiphon-dir", psiphonStateDir.absolutePath))
                }
                if (psiphonBinary != null && "--psiphon-bin" !in withoutInlineBridges) {
                    addAll(listOf("--psiphon-bin", psiphonBinary.absolutePath))
                }
                if (psiphonServerEntries != null && PSIPHON_SERVER_ENTRIES !in withoutInlineBridges) {
                    addAll(listOf(PSIPHON_SERVER_ENTRIES, psiphonServerEntries.absolutePath))
                }
            }
        }
    }

    fun torStateDir(context: Context, isSession: Boolean = true): File =
        File(AetherIdentityManager.workDir(context), if (isSession) TOR_DIR_NAME else TOR_PROBE_DIR_NAME)

    fun torBridgesFile(context: Context, isSession: Boolean = true): File =
        File(torStateDir(context, isSession), TOR_BRIDGES_FILE_NAME)

    fun psiphonStateDir(context: Context, isSession: Boolean = true): File =
        File(AetherIdentityManager.workDir(context), if (isSession) PSIPHON_DIR_NAME else PSIPHON_PROBE_DIR_NAME)

    fun clearPsiphonData(context: Context): Boolean {
        val cleared = clearPsiphonDirs(AetherIdentityManager.workDir(context))
        if (cleared) PsiphonServerList.forgetRemembered()
        return cleared
    }

    internal fun clearPsiphonDirs(workDir: File): Boolean =
        listOf(PSIPHON_DIR_NAME, PSIPHON_PROBE_DIR_NAME).all { File(workDir, it).deleteRecursively() }

    fun scanPort(profile: ProfileItem): Int =
        if (AetherTor.fromString(profile.aetherTor) == AetherTor.REVERSE) {
            try {
                ServerSocket(0).use { it.localPort }
            } catch (_: IOException) {
                0
            }
        } else {
            0
        }

    fun masqueOverHttp2(
        protocol: AetherProtocol,
        transport: AetherTransport,
        tor: AetherTor,
        psiphon: AetherPsiphon,
    ): Boolean = protocol.overMasque &&
        (transport == AetherTransport.HTTP2 || tor == AetherTor.REVERSE || psiphon == AetherPsiphon.REVERSE)

    private fun settingValue(value: String?): String? =
        value?.trim()?.takeUnless { it.isEmpty() || it.startsWith('-') }

    fun buildArguments(
        profile: ProfileItem,
        port: Int = this.socksPort,
        scan: Boolean = false,
        logLevel: String = "info",
    ): List<String> {
        val protocol = AetherProtocol.fromString(profile.aetherProtocol)
        val tor = AetherTor.fromString(profile.aetherTor).takeUnless { scan && it != AetherTor.REVERSE } ?: AetherTor.OFF
        val psiphon = AetherPsiphon.fromString(profile.aetherPsiphon).takeUnless { scan && it != AetherPsiphon.REVERSE } ?: AetherPsiphon.OFF
        val dialsPsiphon = psiphon == AetherPsiphon.CHAIN
        val dialsTor = tor == AetherTor.CHAIN && !dialsPsiphon
        var next = port + 1
        val own = if (dialsPsiphon || dialsTor) next++ else port
        val torBind = when (tor) {
            AetherTor.CHAIN -> if (dialsTor) port else next++
            AetherTor.REVERSE -> next++
            AetherTor.OFF, AetherTor.ONLY -> null
        }
        val psiphonBind = when (psiphon) {
            AetherPsiphon.CHAIN -> if (dialsPsiphon) port else next++
            AetherPsiphon.REVERSE -> 0
            AetherPsiphon.OFF, AetherPsiphon.ONLY -> null
        }
        return buildList {
            addAll(listOf("--bind", "$LOOPBACK:$own"))
            if (psiphon != AetherPsiphon.ONLY && tor != AetherTor.ONLY) {
                addAll(listOf("--protocol", protocol.core))
                if (protocol == AetherProtocol.GOOL) add("--gool-classic")
                addAll(listOf("--scan", AetherScanMode.fromString(profile.aetherScanMode).type))
                val transport = AetherTransport.fromString(profile.aetherTransport)
                AetherObfuscation.fromString(profile.aetherObfuscation)
                    .takeUnless { it == AetherObfuscation.AUTO || masqueOverHttp2(protocol, transport, tor, psiphon) }
                    ?.let { addAll(listOf("--noize", it.type)) }
                addAll(listOf("--ip", AetherIpVersion.fromString(profile.aetherIpVersion).type))
                settingValue(profile.aetherDns)?.let { addAll(listOf("--dns", it)) }
                settingValue(profile.aetherExitLoc)?.let { addAll(listOf("--exit-loc", it)) }

                if (protocol.overMasque) {
                    addAll(listOf("--masque-sni", settingValue(profile.aetherMasqueSni) ?: AETHER_MASQUE_SNI))
                }
                if (protocol.overMasque && transport == AetherTransport.HTTP2) {
                    add("--h2")
                    if (profile.aetherFragment == true) {
                        add("--fragment")
                        AetherRange.parse(profile.aetherFragmentSize, AetherRange.FRAGMENT_SIZE)
                            ?.let { addAll(listOf("--fragment-size", it.toString())) }
                        AetherRange.parse(profile.aetherFragmentDelay, AetherRange.FRAGMENT_DELAY)
                            ?.let { addAll(listOf("--fragment-delay", it.toString())) }
                    }
                }
                if (protocol.overMasque && profile.aetherEch == true) {
                    addAll(listOf("--ech", "auto"))
                    addAll(listOf("--ech-dns", settingValue(profile.aetherEchDns) ?: AETHER_ECH_DNS))
                    addAll(listOf("--ech-domain", settingValue(profile.aetherEchDomain) ?: AETHER_ECH_DOMAIN))
                }
                if (protocol.overMasque) {
                    addAll(AetherFingerprint.fromString(profile.aetherFingerprint).arguments)
                }

                if (protocol == AetherProtocol.WG_OVER_MASQUE) {
                    AetherEndpoint.parse(profile.aetherWiwOuter).takeUnless { scan }?.let { addAll(listOf("--peer", it.toString())) }
                    AetherEndpoint.parse(profile.aetherWiwInner)?.let { addAll(listOf("--gool-peer", it.toString())) }
                } else if (protocol.twoHops) {
                    val hop = if (protocol == AetherProtocol.MIM) "--mim" else "--wiw"
                    val outer = AetherEndpoint.parse(profile.aetherWiwOuter).takeUnless { scan }
                    val inner = AetherEndpoint.parse(profile.aetherWiwInner).takeUnless { scan }
                    outer?.let { addAll(listOf("$hop-outer", it.toString())) }
                    inner?.let { addAll(listOf("$hop-inner", it.toString())) }
                    if (outer == null && inner == null) add("$hop-scan")
                } else if (!scan) {
                    AetherEndpoint.of(profile.server, profile.serverPort)?.let { addAll(listOf("--peer", it.toString())) }
                }

                add(if (scan) "--no-quick-reconnect" else "--quick-reconnect")
            }

            when (tor) {
                AetherTor.OFF -> Unit
                AetherTor.CHAIN -> add("--tor")
                AetherTor.REVERSE -> add("--tor-reverse")
                AetherTor.ONLY -> add("--tor-only")
            }
            torBind?.let { addAll(listOf(TOR_BIND, "$LOOPBACK:$it")) }
            if (tor != AetherTor.OFF) {
                val bridges = AetherTorBridges.fromString(profile.aetherTorBridges)
                when (bridges) {
                    AetherTorBridges.AUTO -> Unit
                    AetherTorBridges.FIRST -> add("--tor-bridges")
                    AetherTorBridges.NEVER -> add("--no-tor-bridges")
                    AetherTorBridges.OWN -> AetherFmt.bridgeLines(profile.aetherTorBridgeLines)
                        .mapNotNull { settingValue(it) }
                        .forEach { addAll(listOf("--tor-bridge", it)) }
                }
                if (bridges == AetherTorBridges.AUTO || bridges == AetherTorBridges.FIRST) {
                    AetherTorRelays.fromString(profile.aetherTorRelays).takeUnless { it == AetherTorRelays.AUTO }
                        ?.let { addAll(listOf("--tor-relays", it.type)) }
                }
            }

            when (psiphon) {
                AetherPsiphon.OFF -> Unit
                AetherPsiphon.CHAIN -> add("--psiphon")
                AetherPsiphon.REVERSE -> add("--psiphon-reverse")
                AetherPsiphon.ONLY -> add("--psiphon-only")
            }
            psiphonBind?.let { addAll(listOf(PSIPHON_BIND, "$LOOPBACK:$it")) }
            if (psiphon != AetherPsiphon.OFF) {
                val shape = AetherPsiphonMode.fromString(profile.aetherPsiphonMode)
                addAll(listOf("--psiphon-mode", shape.type))
                val cdnIps = settingValue(profile.aetherPsiphonCdnIps)?.takeIf { shape != AetherPsiphonMode.DIRECT }
                cdnIps?.let { addAll(listOf("--psiphon-cdn-ips", it)) }
                if (cdnIps != null) settingValue(profile.aetherPsiphonCdnSni)?.let { addAll(listOf("--psiphon-cdn-sni", it)) }
                if (shape != AetherPsiphonMode.DIRECT) {
                    AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(profile.aetherPsiphonCdnSets))?.let { addAll(listOf("--psiphon-cdn-sets", it)) }
                }
                settingValue(profile.aetherPsiphonRegion)?.let { addAll(listOf("--psiphon-region", it)) }
                if (profile.aetherPsiphonBundledList != false) addAll(listOf(PSIPHON_SERVER_ENTRIES, SHIPPED_LIST))
            }
            addAll(listOf("--log-level", logLevel))
        }
    }

    internal fun echArguments(enabled: Boolean, dns: String?, domain: String?): List<String> {
        if (!enabled) return emptyList()
        val cleanDns = settingValue(dns) ?: AETHER_ECH_DNS
        val cleanDomain = settingValue(domain) ?: AETHER_ECH_DOMAIN
        return listOf("--ech", "auto", "--ech-dns", cleanDns, "--ech-domain", cleanDomain)
    }

    fun parseBridgeLines(raw: String?): List<String> = AetherFmt.bridgeLines(raw)

    internal fun listenerFlagOf(arguments: List<String>): String = when {
        torModeOf(arguments) == AetherTor.CHAIN -> TOR_BIND
        psiphonModeOf(arguments) == AetherPsiphon.CHAIN -> PSIPHON_BIND
        else -> "--bind"
    }

    internal fun listenerAddressOf(arguments: List<String>): String? =
        listenerAddressAfter(arguments, listenerFlagOf(arguments))

    internal fun listenerPortOf(arguments: List<String>): Int? =
        listenerAddressOf(arguments)?.substringAfterLast(':', "")?.toIntOrNull()

    internal fun portAfter(arguments: List<String>, flag: String): Int? =
        listenerAddressAfter(arguments, flag)?.substringAfterLast(':', "")?.toIntOrNull()

    internal fun listenerAddressAfter(arguments: List<String>, flag: String): String? =
        valueAfter(arguments, flag)?.let { if (flag == "--bind") it else it.trim(::isRustWhitespace) }

    internal fun isRustWhitespace(c: Char): Boolean =
        c in '\u0009'..'\u000D' || c == ' ' || c == '\u0085' || c == '\u00A0' || c == '\u1680' || c in '\u2000'..'\u200A' ||
            c == '\u2028' || c == '\u2029' || c == '\u202F' || c == '\u205F' || c == '\u3000'

    internal fun reachesLoopback(host: String): Boolean {
        if (!host.startsWith('[')) return host == LOOPBACK || host == "0.0.0.0"
        val groups = ipv6Groups(host.removePrefix("[").removeSuffix("]").substringBefore('%')) ?: return false
        if (groups.all { it == 0 }) return true
        val mapped = (0..4).all { groups[it] == 0 } && groups[5] == 0xFFFF
        return mapped && ((groups[6] == 0x7F00 && groups[7] == 0x0001) || (groups[6] == 0 && groups[7] == 0))
    }

    internal fun ipv6Groups(text: String): IntArray? {
        if ('%' in text || !isIpv6Literal(text)) return null
        fun groupsOf(part: String): List<Int> = if (part.isEmpty()) emptyList() else part.split(':').flatMap { group ->
            if ('.' in group) {
                val octets = group.split('.').map { it.toInt() }
                listOf((octets[0] shl 8) or octets[1], (octets[2] shl 8) or octets[3])
            } else {
                listOf(group.toInt(16))
            }
        }
        val halves = text.split("::")
        val head = groupsOf(halves[0])
        val tail = if (halves.size == 2) groupsOf(halves[1]) else emptyList()
        val zeros = if (halves.size == 2) 8 - head.size - tail.size else 0
        return (head + List(zeros) { 0 } + tail).toIntArray()
    }

    internal fun isSocketAddress(address: String): Boolean {
        val colon = address.lastIndexOf(':')
        if (colon <= 0 || !isDecimalUpTo(address.substring(colon + 1), 65_535)) return false
        val host = address.substring(0, colon)
        return if (host.startsWith('[') && host.endsWith(']')) isIpv6Literal(host.substring(1, host.length - 1)) else isIpv4Literal(host)
    }

    internal fun isIpv4Literal(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } && (part == "0" || !part.startsWith('0')) &&
                part.toInt() <= 255
        }
    }

    internal fun isIpv6Literal(text: String): Boolean {
        val address = text.substringBefore('%')
        if ('%' in text && !isDecimalUpTo(text.substringAfter('%'), 4_294_967_295)) return false
        val halves = address.split("::")
        if (halves.size > 2) return false
        val head = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
        val tail = if (halves.size < 2 || halves[1].isEmpty()) emptyList() else halves[1].split(':')
        val groups = head + tail
        var count = 0
        for ((index, group) in groups.withIndex()) {
            count += when {
                '.' in group -> if (index == groups.lastIndex && isIpv4Literal(group) && (halves.size == 1 || tail.isNotEmpty())) 2 else return false
                group.isNotEmpty() && group.length <= 4 && group.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> 1
                else -> return false
            }
        }
        return if (halves.size == 2) count <= 7 else count == 8
    }

    private fun isDecimalUpTo(text: String, max: Long): Boolean {
        if (text.isEmpty() || !text.all { it in '0'..'9' }) return false
        val significant = text.trimStart('0')
        return significant.length <= 10 && (significant.isEmpty() || significant.toLong() <= max)
    }

    internal fun withListener(arguments: List<String>, flag: String, port: Int): List<String> =
        listOf(flag, "$LOOPBACK:$port") + withoutOption(arguments, flag)

    internal fun withInfoLines(arguments: List<String>): List<String> {
        val level = valueAfter(arguments, "--log-level")?.lowercase()
        if (level == "info" || level == "debug" || level == "trace") return arguments
        if (level == null && "--verbose" in arguments) return arguments
        return withoutOption(arguments, "--log-level") + listOf("--log-level", "info")
    }

    internal fun showsInfo(arguments: List<String>): Boolean {
        for (i in arguments.indices.reversed()) {
            when (arguments[i]) {
                "--verbose" -> return true
                "--log-level" -> {
                    val level = arguments.getOrNull(i + 1)?.trim()?.lowercase()
                    if (level in setOf("error", "warn", "info", "debug", "trace")) {
                        return level != "error" && level != "warn"
                    }
                }
            }
        }
        return true
    }

    internal fun tunnelArguments(arguments: List<String>): List<String> =
        withoutFlag("--verbose", withoutOptions(arguments, setOf("--bind", TOR_BIND, PSIPHON_BIND, "--log-level")))

    internal fun withoutOption(arguments: List<String>, flag: String): List<String> =
        withoutOptions(arguments, setOf(flag))

    private fun withoutOptions(arguments: List<String>, flags: Set<String>): List<String> = buildList {
        var i = 0
        while (i < arguments.size) {
            if (arguments[i] in flags) {
                i += 2
            } else {
                add(arguments[i])
                i++
            }
        }
    }

    private fun withoutFlag(flag: String, arguments: List<String>): List<String> = buildList {
        var i = 0
        while (i < arguments.size) {
            val arg = arguments[i]
            when {
                arg == flag -> i++
                arg in flagsWithValue -> {
                    add(arg)
                    if (i + 1 < arguments.size) add(arguments[i + 1])
                    i += 2
                }
                else -> {
                    add(arg)
                    i++
                }
            }
        }
    }

    internal fun protocolOf(arguments: List<String>): AetherProtocol {
        var protocol: String? = null
        var goolMode: String? = null
        var hasWiw = false
        var hasMim = false
        var hasPeer = false
        var i = 0
        while (i < arguments.size) {
            val arg = arguments[i]
            when {
                arg == "--masque" -> protocol = "masque"
                arg == "--wg" || arg == "--wireguard" || arg == "--warp" -> protocol = "wg"
                arg == "--gool" || arg == "--wiw" -> protocol = "gool"
                arg == "--gool-peer" -> if (i + 1 < arguments.size) protocol = "gool"
                arg == "--gool-classic" -> {
                    protocol = "gool"
                    goolMode = "classic"
                }
                arg == "--mim" || arg == "--masque-in-masque" -> protocol = "mim"
                arg == "--protocol" -> arguments.getOrNull(i + 1)?.let { protocol = it }
                arg in wiwSelectionFlags && (arg !in flagsWithValue || i + 1 < arguments.size) -> hasWiw = true
                arg in mimSelectionFlags && (arg !in flagsWithValue || i + 1 < arguments.size) -> hasMim = true
                (arg == "--peer" || arg == "--wg-peer") && i + 1 < arguments.size -> hasPeer = true
            }
            i += if (arg in flagsWithValue) 2 else 1
        }
        return when (protocol?.trim()?.lowercase()) {
            "wg", "wireguard", "warp" -> AetherProtocol.WIREGUARD
            "gool", "wiw", "warp-in-warp" -> if (goolMode == "classic") AetherProtocol.GOOL else AetherProtocol.WG_OVER_MASQUE
            "mim", "masque-in-masque" -> AetherProtocol.MIM
            "masque" -> AetherProtocol.MASQUE
            null -> when {
                hasWiw -> AetherProtocol.GOOL
                hasMim -> AetherProtocol.MIM
                hasPeer -> AetherProtocol.MASQUE
                else -> AetherProtocol.MASQUE
            }
            else -> AetherProtocol.MASQUE
        }
    }

    internal fun usesTor(arguments: List<String>): Boolean = torModeOf(arguments) != AetherTor.OFF

    internal fun usesPsiphon(arguments: List<String>): Boolean = psiphonModeOf(arguments) != AetherPsiphon.OFF

    internal fun reachesWarpThroughCarrier(arguments: List<String>): Boolean {
        val tor = torModeOf(arguments)
        val psiphon = psiphonModeOf(arguments)
        if (tor == AetherTor.ONLY || psiphon == AetherPsiphon.ONLY) return false
        return tor == AetherTor.REVERSE || psiphon == AetherPsiphon.REVERSE
    }

    internal fun torModeOf(arguments: List<String>): AetherTor {
        var mode = AetherTor.OFF
        var i = 0
        while (i < arguments.size) {
            val arg = arguments[i]
            when (arg) {
                "--tor" -> mode = AetherTor.CHAIN
                "--tor-reverse" -> mode = AetherTor.REVERSE
                "--tor-only" -> mode = AetherTor.ONLY
            }
            i += if (arg in flagsWithValue) 2 else 1
        }
        return mode
    }

    internal fun psiphonModeOf(arguments: List<String>): AetherPsiphon {
        var mode = AetherPsiphon.OFF
        var i = 0
        while (i < arguments.size) {
            val arg = arguments[i]
            when (arg) {
                "--psiphon" -> mode = AetherPsiphon.CHAIN
                "--psiphon-reverse" -> mode = AetherPsiphon.REVERSE
                "--psiphon-only" -> mode = AetherPsiphon.ONLY
            }
            i += if (arg in flagsWithValue) 2 else 1
        }
        return mode
    }

    internal fun aloneOf(arguments: List<String>): String? =
        aloneOf(torModeOf(arguments), psiphonModeOf(arguments))

    internal fun aloneOf(tor: AetherTor, psiphon: AetherPsiphon): String? = when {
        tor == AetherTor.ONLY -> "Tor"
        psiphon == AetherPsiphon.ONLY -> "Psiphon"
        else -> null
    }

    internal fun leavesThroughWarp(arguments: List<String>): Boolean =
        leavesThroughWarp(torModeOf(arguments), psiphonModeOf(arguments))

    internal fun leavesThroughWarp(tor: AetherTor, psiphon: AetherPsiphon): Boolean =
        aloneOf(tor, psiphon) == null && tor != AetherTor.CHAIN && psiphon != AetherPsiphon.CHAIN

    internal fun pathOf(
        arguments: List<String>,
        protocolLabel: (AetherProtocol) -> String = { it.type },
    ): List<String> = pathOf(protocolLabel(protocolOf(arguments)), torModeOf(arguments), psiphonModeOf(arguments))

    internal fun pathOf(protocol: String, tor: AetherTor, psiphon: AetherPsiphon): List<String> {
        aloneOf(tor, psiphon)?.let { return listOf(it) }
        return buildList {
            if (tor == AetherTor.REVERSE) add("Tor")
            if (psiphon == AetherPsiphon.REVERSE) add("Psiphon")
            add(protocol)
            if (tor == AetherTor.CHAIN) add("Tor")
            if (psiphon == AetherPsiphon.CHAIN) add("Psiphon")
        }
    }

    internal fun refusalOf(
        arguments: List<String>,
        hasTorTransports: Boolean,
        hasPsiphon: Boolean,
    ): Int? {
        val tor = torModeOf(arguments)
        val psiphon = psiphonModeOf(arguments)
        if (psiphon != AetherPsiphon.OFF && !hasPsiphon) return R.string.aether_psiphon_unavailable
        if (tor != AetherTor.OFF && !hasTorTransports && valuesAfter(arguments, "--tor-bridge").isNotEmpty()) {
            return R.string.aether_tor_transports_unavailable
        }
        val registration = AetherKeys.REGISTER in arguments
        val bothInSamePlace = if (registration) {
            tor == AetherTor.REVERSE && psiphon == AetherPsiphon.REVERSE
        } else {
            aloneOf(tor, psiphon) == null &&
                ((tor == AetherTor.REVERSE && psiphon == AetherPsiphon.REVERSE) ||
                    (tor == AetherTor.CHAIN && psiphon == AetherPsiphon.CHAIN))
        }
        if (bothInSamePlace) return R.string.aether_tor_psiphon_conflict
        if (!registration && aloneOf(tor, psiphon) == null && !protocolOf(arguments).overMasque) {
            if (tor == AetherTor.REVERSE) return R.string.aether_tor_needs_masque
            if (psiphon == AetherPsiphon.REVERSE) return R.string.aether_psiphon_needs_masque
        }
        return null
    }

    internal fun readyNeedsWord(arguments: List<String>): Boolean {
        val tor = torModeOf(arguments)
        val psiphon = psiphonModeOf(arguments)
        if (tor == AetherTor.ONLY || psiphon == AetherPsiphon.ONLY) return false
        return tor != AetherTor.CHAIN && psiphon != AetherPsiphon.CHAIN
    }

    internal fun isReadyWord(line: String): Boolean =
        "tunnel up (" in line || "tunnel up: " in line

    internal fun isNoEchKeyWord(line: String): Boolean =
        "is on, and no ECHConfigList could be got (" in line

    internal fun chainEndReasonOf(line: String): String? =
        chainEndWords.firstOrNull { line.startsWith(it) }?.let { prefix ->
            line.removePrefix(prefix).trim().ifEmpty { null }
        }

    private val chainEndWords = listOf("[-] tor: ", "[-] psiphon: ")

    private fun valueAfter(arguments: List<String>, flag: String): String? =
        valuesAfter(arguments, flag).lastOrNull()

    private fun valuesAfter(arguments: List<String>, flag: String): List<String> = buildList {
        var i = 0
        while (i < arguments.size) {
            val arg = arguments[i]
            when {
                arg == flag -> {
                    if (i + 1 < arguments.size) add(arguments[i + 1])
                    i += 2
                }
                arg in flagsWithValue -> i += 2
                else -> i++
            }
        }
    }

    fun isPortFree(port: Int): Boolean =
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(LOOPBACK, port))
            }
            true
        } catch (_: IOException) {
            false
        }

    fun invalidPort(ports: Iterable<Int>): Int? = ports.firstOrNull { it !in 1..65535 }

    fun firstPortInUse(
        context: Context,
        ports: Iterable<Int>,
        isFree: (Int) -> Boolean = ::isPortFree,
    ): Int? {
        val distinct = ports.distinct()
        val busy = distinct.firstOrNull { !isFree(it) } ?: return null
        killRunning(context)
        return distinct.firstOrNull { !isFree(it) } ?: busy.takeUnless(isFree)
    }

    private suspend fun waitForSocks(
        port: Int,
        timeoutMs: Long,
        needsWord: Boolean = false,
        isAlive: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive()) return false
            if ((!needsWord || readyWordSeen) && answersSocks(port)) return true
            delay(SOCKS_POLL_INTERVAL_MS)
        }
        return false
    }

    internal fun answersSocks(port: Int): Boolean = socksAnswers.answers(port)

    private fun streamProcessOutput(
        target: Process,
        source: String,
        onLine: (String) -> Unit,
    ) {
        try {
            target.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    if (line.isNotEmpty()) {
                        Log.i(TAG, "$source: $line")
                        onLine(line)
                    }
                }
            }
        } catch (_: IOException) {
        }
    }

    internal class CoreProcess(val pid: Int, val argv: List<String>, val session: Boolean = false, val exit: String? = null)

    internal fun sessionProcess(context: Context): CoreProcess? {
        val args = activeCoreArgs
        if (activeJobHandle != null && args != null) {
            return CoreProcess(AndroidProcess.myPid(), listOf(BINARY_NAME) + args, session = true, exit = activeExitKey)
        }
        return sessionProcessIn(File("/proc"), binaryFile(context).canonicalPath, AndroidProcess.myUid())
    }

    internal fun canListProcesses(procRoot: File = File("/proc"), myPid: Int = AndroidProcess.myPid()): Boolean =
        File(procRoot, "$myPid/cmdline").canRead() &&
            procRoot.listFiles()?.any { it.name.toIntOrNull().let { pid -> pid != null && pid != myPid } } == true

    internal fun sessionProcessIn(procRoot: File, targetPath: String, myUid: Int): CoreProcess? =
        runningCoreProcesses(procRoot, targetPath, myUid).firstOrNull { it.session }

    fun killRunning(context: Context, sessionOnly: Boolean = false) {
        if (sessionOnly) {
            val handle = activeJobHandle
            if (handle != null) {
                activeJobHandle = null
                activeCoreArgs = null
                activeExitKey = null
                AetherBridge.jobCancel(handle)
                AetherBridge.jobFree(handle)
            }
        }
        val targetPath = runCatching { binaryFile(context).canonicalPath }.getOrNull() ?: return
        val processes = runningCoreProcesses(File("/proc"), targetPath, AndroidProcess.myUid())
            .filter { !sessionOnly || it.session }
        if (processes.isEmpty()) return

        processes.forEach { proc ->
            AndroidProcess.sendSignal(proc.pid, android.system.OsConstants.SIGTERM)
        }
        val deadline = System.currentTimeMillis() + SHUTDOWN_WAIT_MS
        while (System.currentTimeMillis() < deadline && processes.any { File("/proc/${it.pid}").exists() }) {
            try {
                Thread.sleep(25)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        processes.forEach { proc ->
            if (File("/proc/${proc.pid}").exists()) {
                AndroidProcess.killProcess(proc.pid)
            }
        }
    }

    internal fun runningCoreProcesses(
        procRoot: File,
        targetPath: String,
        myUid: Int,
        myPid: Int = AndroidProcess.myPid(),
    ): List<CoreProcess> {
        val entries = procRoot.listFiles() ?: return emptyList()
        val cores = entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull()?.takeIf { it != myPid } ?: return@mapNotNull null
            val status = try {
                File(entry, "status").readText()
            } catch (_: IOException) {
                return@mapNotNull null
            }
            if (uidOfStatus(status) != myUid) return@mapNotNull null
            val argv = try {
                File(entry, "cmdline").readBytes().toString(Charsets.UTF_8)
                    .split('\u0000')
                    .dropLastWhile { it.isEmpty() }
            } catch (_: IOException) {
                return@mapNotNull null
            }
            val executable = argv.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!matchesBinary(executable, targetPath)) return@mapNotNull null
            val env = try {
                File(entry, "environ").readBytes().toString(Charsets.UTF_8)
            } catch (_: IOException) {
                null
            }
            val ownerPid = env?.let { envValue(it, OWNER_PID_ENV)?.toIntOrNull() }
            val session = env?.let { envValue(it, SESSION_ENV) == "1" } ?: false
            val exit = env?.let { envValue(it, EXIT_ENV) }
            if (ownerPid != null && ownerPid != myPid && !session && isOurProcessAlive(procRoot, ownerPid, myUid)) {
                return@mapNotNull null
            }
            CoreProcess(pid, argv, session, exit)
        }
        val corePids = cores.mapTo(HashSet()) { it.pid }
        val children = if (corePids.isEmpty()) {
            emptyList()
        } else {
            entries.mapNotNull { entry ->
                val pid = entry.name.toIntOrNull()?.takeIf { it != myPid && it !in corePids } ?: return@mapNotNull null
                val status = try {
                    File(entry, "status").readText()
                } catch (_: IOException) {
                    return@mapNotNull null
                }
                if (uidOfStatus(status) != myUid) return@mapNotNull null
                val parentPid = ppidOfStatus(status) ?: return@mapNotNull null
                val parent = cores.firstOrNull { it.pid == parentPid } ?: return@mapNotNull null
                CoreProcess(pid, emptyList(), parent.session)
            }
        }
        return cores + children
    }

    private fun isOurProcessAlive(procRoot: File, pid: Int, myUid: Int): Boolean {
        val status = try {
            File(procRoot, "$pid/status").readText()
        } catch (_: IOException) {
            return false
        }
        return uidOfStatus(status) == myUid
    }

    internal fun envValue(environ: String, name: String): String? {
        val prefix = "$name="
        return environ.split('\u0000').firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
    }

    internal fun uidOfStatus(status: String): Int? = fieldOfStatus(status, "Uid:")

    internal fun ppidOfStatus(status: String): Int? = fieldOfStatus(status, "PPid:")

    private fun fieldOfStatus(status: String, prefix: String): Int? =
        status.lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trimStart()
            ?.takeWhile { !it.isWhitespace() }
            ?.toIntOrNull()

    private fun matchesBinary(argv0: String, targetPath: String): Boolean {
        if (argv0 == targetPath || File(argv0).name == BINARY_NAME) return true
        return try {
            File(argv0).canonicalPath == targetPath
        } catch (_: IOException) {
            false
        }
    }

    private fun terminateProcess(target: Process) {
        if (!target.isAlive) return
        target.destroy()
        try {
            val deadline = System.currentTimeMillis() + SHUTDOWN_WAIT_MS
            while (target.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (target.isAlive) {
            target.destroyForcibly()
        }
    }
}
