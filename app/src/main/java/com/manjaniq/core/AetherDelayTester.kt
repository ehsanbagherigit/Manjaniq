package com.manjaniq.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

object AetherDelayTester {

    private const val TAG = "AetherDelayTester"
    private const val SOURCE = "aether-test"
    private const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    internal const val TEST_PORT_START = 28910
    internal const val TEST_PORT_END = 28940
    internal const val TEST_BUDGET_MS = 20_000L
    private const val CARRIER_TEST_BUDGET_MS = 2 * 60_000L
    private const val SOCKS_READY_POLL_MS = 100L
    private const val HTTP_TIMEOUT_MS = 8_000
    private const val TCP_TIMEOUT_MS = 5_000

    private val mutex = Mutex()

    internal data class LiveSession(
        val arguments: List<String>?,
        val port: Int,
        val listening: Boolean,
        val exit: String? = null,
    )

    internal enum class Route {
        ACTIVE_SESSION,
        NEW_TUNNEL,
        SKIP,
    }

    suspend fun measure(
        context: Context,
        guid: String,
        profile: ProfileItem,
        testUrl: String = DEFAULT_TEST_URL,
    ): Long {
        if (!AetherCoreManager.hasBinary(context)) return -1L
        val core = AetherCore.of(profile)
        if (AetherCoreManager.refusalOf(
                core.arguments,
                AetherCoreManager.hasTorTransports(context),
                AetherCoreManager.hasPsiphon(context),
            ) != null
        ) {
            return -1L
        }
        val activeGuid = AetherSettingsStorage.getSelectedServer()
        return when (route(guid, core, activeGuid, liveSessionOf(context))) {
            Route.ACTIVE_SESSION -> measureSocksDelay(core.port, testUrl)
            Route.NEW_TUNNEL -> measureInTemporaryTunnel(context, core, testUrl)
            Route.SKIP -> -1L
        }
    }

    fun reachability(profile: ProfileItem): Int {
        val protocol = AetherProtocol.fromString(profile.aetherProtocol)
        if (!protocol.overMasque) return -1
        val endpoint = when (protocol) {
            AetherProtocol.MASQUE, AetherProtocol.WG_OVER_MASQUE -> AetherEndpoint.of(profile.server, profile.serverPort)
            AetherProtocol.MIM -> AetherEndpoint.parse(profile.aetherWiwOuter)
            AetherProtocol.WIREGUARD, AetherProtocol.GOOL -> null
        } ?: return -1
        return measureEndpointDelay(endpoint)
    }

    internal fun liveSessionOf(
        context: Context,
        answers: (Int) -> Boolean = AetherCoreManager::answersSocks,
    ): LiveSession? = liveSessionOf(
        process = AetherCoreManager.sessionProcess(context),
        processesListed = AetherCoreManager.canListProcesses(),
        active = { AetherSettingsStorage.getSelectedServer()?.let(AetherSettingsStorage::decodeServerConfig) },
        answers = answers,
    )

    internal fun liveSessionOf(
        process: AetherCoreManager.CoreProcess?,
        processesListed: Boolean,
        active: () -> ProfileItem?,
        answers: (Int) -> Boolean,
    ): LiveSession? {
        if (process != null) {
            val arguments = process.argv.drop(1)
            val port = AetherCoreManager.listenerPortOf(arguments) ?: AetherCoreManager.socksPort
            return LiveSession(arguments, port, answers(port), process.exit)
        }
        if (processesListed) return null
        val profile = active() ?: return null
        val core = AetherCore.of(profile)
        return LiveSession(core.arguments, core.port, answers(core.port), core.exit.key).takeIf { it.listening }
    }

    internal fun route(
        guid: String,
        core: AetherCore,
        activeGuid: String?,
        session: LiveSession?,
    ): Route {
        if (session == null) return Route.NEW_TUNNEL
        val sameAsSession = guid == activeGuid ||
            (session.arguments != null &&
                session.exit == core.exit.key &&
                AetherCoreManager.tunnelArguments(session.arguments) == AetherCoreManager.tunnelArguments(core.arguments))
        if (sameAsSession) {
            return if (session.listening) Route.ACTIVE_SESSION else Route.SKIP
        }
        val activeProtocol = session.arguments?.let(AetherCoreManager::protocolOf) ?: AetherProtocol.WIREGUARD
        val candidateProtocol = AetherCoreManager.protocolOf(core.arguments)
        val candidateTor = AetherCoreManager.torModeOf(core.arguments)
        val candidatePsiphon = AetherCoreManager.psiphonModeOf(core.arguments)
        val candidateRunsWarp = candidateTor != AetherTor.ONLY && candidatePsiphon != AetherPsiphon.ONLY
        val sharesKey = candidateRunsWarp && sharesIdentityFile(activeProtocol, candidateProtocol)
        val activeTor = session.arguments?.let(AetherCoreManager::torModeOf) ?: AetherTor.OFF
        val activePsiphon = session.arguments?.let(AetherCoreManager::psiphonModeOf) ?: AetherPsiphon.OFF
        val sharesExit = session.exit != null && session.exit != AetherExit.PLAIN.key && session.exit == core.exit.key
        val sharesCarrierPort = (activeTor == AetherTor.REVERSE && candidateTor == AetherTor.REVERSE) ||
            (activePsiphon == AetherPsiphon.REVERSE && candidatePsiphon == AetherPsiphon.REVERSE)
        return if (sharesKey || sharesExit || sharesCarrierPort) Route.SKIP else Route.NEW_TUNNEL
    }

    internal fun sharesIdentityFile(first: AetherProtocol, second: AetherProtocol): Boolean =
        identityFilesOf(first).any { it in identityFilesOf(second) }

    private fun identityFilesOf(protocol: AetherProtocol): Set<String> = when (protocol) {
        AetherProtocol.WIREGUARD -> setOf(AetherIdentityManager.WIREGUARD_FILE)
        AetherProtocol.GOOL -> setOf(AetherIdentityManager.WIREGUARD_FILE, AetherIdentityManager.WIREGUARD_INNER_FILE)
        AetherProtocol.MASQUE -> setOf(AetherIdentityManager.MASQUE_FILE)
        AetherProtocol.MIM -> setOf(AetherIdentityManager.MASQUE_FILE, AetherIdentityManager.MASQUE_INNER_FILE)
        AetherProtocol.WG_OVER_MASQUE -> setOf(AetherIdentityManager.MASQUE_FILE, AetherIdentityManager.MASQUE_GOOL_FILE)
    }

    private suspend fun measureInTemporaryTunnel(
        context: Context,
        core: AetherCore,
        testUrl: String,
    ): Long = mutex.withLock {
        val port = chooseListenPort() ?: return@withLock -1L
        val arguments = testArguments(core, port)
        val budget = if (AetherCoreManager.usesTor(arguments) || AetherCoreManager.usesPsiphon(arguments)) {
            CARRIER_TEST_BUDGET_MS
        } else {
            TEST_BUDGET_MS
        }
        val needsWord = AetherCoreManager.readyNeedsWord(arguments)
        val workDir = AetherIdentityManager.workDir(context)
        val probeDir = File(workDir, "test-$port").apply { deleteRecursively() }
        try {
            copyIdentityFiles(workDir, probeDir)
            AetherCoreManager.withProcess(
                context = context,
                arguments = arguments,
                source = SOURCE,
                onOutput = {},
                keysDir = probeDir,
            ) { output ->
                withTimeoutOrNull(budget) {
                    if (needsWord) {
                        var sawReady = false
                        for (line in output) {
                            if (AetherCoreManager.isReadyWord(line)) {
                                sawReady = true
                                break
                            }
                        }
                        if (!sawReady) return@withTimeoutOrNull -1L
                    }
                    while (!AetherCoreManager.answersSocks(port)) {
                        delay(SOCKS_READY_POLL_MS)
                    }
                    measureSocksDelay(port, testUrl)
                } ?: -1L
            } ?: -1L
        } finally {
            probeDir.deleteRecursively()
        }
    }

    internal fun copyIdentityFiles(from: File, into: File) {
        if (!into.exists() && !into.mkdirs()) return
        for (name in identityFiles) {
            val source = File(from, name)
            if (source.isFile) {
                try {
                    source.copyTo(File(into, name), overwrite = true)
                } catch (_: IOException) {
                }
            }
        }
    }

    private val identityFiles = listOf(
        AetherIdentityManager.BASE_FILE,
        AetherIdentityManager.WIREGUARD_FILE,
        AetherIdentityManager.WIREGUARD_INNER_FILE,
        AetherIdentityManager.MASQUE_FILE,
        AetherIdentityManager.MASQUE_INNER_FILE,
        AetherIdentityManager.MASQUE_GOOL_FILE,
    )

    internal fun testArguments(core: AetherCore, port: Int): List<String> {
        val base = AetherCoreManager.withInfoLines(core.arguments)
        return when {
            AetherCoreManager.torModeOf(base) == AetherTor.CHAIN ->
                AetherCoreManager.withListener(
                    AetherCoreManager.withListener(base, "--bind", port + 1),
                    AetherCoreManager.TOR_BIND,
                    port,
                )
            AetherCoreManager.psiphonModeOf(base) == AetherPsiphon.CHAIN ->
                AetherCoreManager.withListener(
                    AetherCoreManager.withListener(base, "--bind", port + 1),
                    AetherCoreManager.PSIPHON_BIND,
                    port,
                )
            else -> AetherCoreManager.withListener(base, "--bind", port)
        }
    }

    internal fun chooseListenPort(isFree: (Int) -> Boolean = AetherCoreManager::isPortFree): Int? =
        (TEST_PORT_START..TEST_PORT_END step 2).firstOrNull { isFree(it) && isFree(it + 1) }

    internal fun measureSocksDelay(
        socksPort: Int,
        testUrl: String,
        timeoutMs: Int = HTTP_TIMEOUT_MS,
    ): Long {
        val target = testUrl.ifBlank { DEFAULT_TEST_URL }
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(AetherCoreManager.LOOPBACK, socksPort))
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(target)
            connection = (url.openConnection(proxy) as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "HEAD"
                setRequestProperty("Connection", "close")
            }
            val started = SystemClock.elapsedRealtime()
            val code = connection.responseCode
            val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
            if (code in 200..399) elapsed else -1L
        } catch (e: Exception) {
            Log.w(TAG, "SOCKS delay check failed on port $socksPort: ${e.message}")
            -1L
        } finally {
            connection?.disconnect()
        }
    }

    internal fun measureEndpointDelay(
        endpoint: AetherEndpoint,
        timeoutMs: Int = TCP_TIMEOUT_MS,
    ): Int =
        try {
            Socket().use { socket ->
                val address = InetSocketAddress(endpoint.host, endpoint.port)
                val started = SystemClock.elapsedRealtime()
                socket.connect(address, timeoutMs)
                (SystemClock.elapsedRealtime() - started).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
            -1
        }
}
