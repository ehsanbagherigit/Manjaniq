package com.manjaniq.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.manjaniq.AetherBridge
import com.manjaniq.MainActivity
import com.manjaniq.R
import com.manjaniq.core.AetherConfigStore
import com.manjaniq.core.AetherHelpers
import com.manjaniq.core.AetherJobPolling
import com.manjaniq.core.AetherNetworkCheck
import com.manjaniq.core.AetherRegistrationOutcome
import com.manjaniq.core.AetherRepository
import com.manjaniq.core.models.AetherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Foreground service that owns the Aether native lifecycle.
 *
 * Phase 1: SOCKS5 only (no VpnService/TUN). It starts the Aether core through
 * `aether_core_start` - the core's own command line - and keeps it running while
 * the user is in other apps. That is the only start path: it applies the whole
 * configuration (protocol, scan, Tor/Psiphon layering, Zero Trust, routing,
 * upstream, DNS, statistics and marks), so no setting is stored-but-ignored.
 * RSTA EngineService remains separate; this service is independent and must not
 * corrupt RSTA's EngineRepository.
 *
 * Design:
 * - Uses AetherBridge FFI with Job poll model (Running -> Done).
 * - Single active job at a time; duplicate start is rejected.
 * - Stores identity path in app files dir (never logs secrets).
 * - Binds SOCKS only to loopback; never 0.0.0.0.
 */
class AetherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJobId: Long? = null

    /** The start attempt in flight, if any. */
    private var currentAttempt: StartAttempt? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // A start can arrive from the notification without the activity ever
        // having composed, so the helper paths are resolved here too.
        AetherHelpers.configure(applicationContext)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAether()
            ACTION_STOP -> stopAether()
            ACTION_RESTART -> restartAether()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelCurrentAttempt()
        // Best-effort native cleanup: cancel job + free
        try {
            currentJobId?.let { AetherBridge.jobCancel(it) }
            currentJobId?.let { AetherBridge.jobFree(it) }
        } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    /** Marks the start attempt in flight as cancelled, so it reports STOPPED, not ERROR. */
    private fun cancelCurrentAttempt() {
        synchronized(this) { currentAttempt?.cancelled = true }
    }

    /**
     * Writes this route's fragmenting settings - and the carrier they need - into the
     * engine's environment, then says what the environment actually holds.
     *
     * `fragment.rs` has exactly one consumer, `masque_h2.rs` (:239, :284, :402), so the
     * MASQUE-over-HTTP/2 carrier has to be selected for fragments to exist at all;
     * `AetherConfig.usesHttp2Carrier()` pairs the two for every protocol that has that
     * carrier. The CLI does the same pairing with `--h2` and `--fragment`
     * (cli.rs:579, :602-605).
     *
     * Returns the reason the start must not go on, or null to carry on: fragmenting was
     * asked for and could not be put in effect. Starting anyway would run an unfragmented
     * tunnel while the route asks for fragments, which is exactly the silent substitution
     * this service refuses elsewhere. A failure while fragmenting is *off* is only a WARN -
     * but it is still logged, because the environment outlives one tunnel and an
     * unrefreshed one could still hold an earlier route's values. The caller reports the
     * returned message through the start attempt's own `failed()`, which knows whether a
     * stop turned this into a cancellation.
     */
    private suspend fun applyFragmenting(
        cfg: com.manjaniq.core.models.AetherConfig,
        attempt: StartAttempt
    ): String? {
        val applied = AetherBridge.fragmentingApply(
            enabled = cfg.fragmentEnabled,
            size = cfg.fragmentSize,
            delay = cfg.fragmentDelay,
            sniSplit = cfg.fragmentSniSplit,
            http2Carrier = cfg.usesHttp2Carrier()
        )
        if (applied.isFailure) {
            val why = applied.exceptionOrNull()?.message ?: "the bridge did not answer"
            if (cfg.fragmentEnabled) {
                return "Fragmenting could not be applied ($why), so Aether was not started: " +
                    "it would have run without the ClientHello fragmenting this route asks for"
            }
            AetherRepository.log(
                "WARN",
                "Fragmenting state could not be refreshed ($why); the engine keeps whatever " +
                    "carrier and chunk settings its environment already held"
            )
            return null
        }

        val values = applied.getOrNull()!!
        val description = describeFragmenting(values, cfg.fragmentEnabled)
        val carrierOn = values.optString("carrier", "") == "1"
        val fragmentOn = values.optString("fragment", "") == "1"
        if (cfg.fragmentEnabled && (!carrierOn || !fragmentOn)) {
            return "Fragmenting was asked for but the engine reports $description, so Aether " +
                "was not started rather than run unfragmented"
        }
        if (attempt.cancelled) {
            return "fragmenting was applied but no tunnel was started"
        }
        AetherRepository.log("INFO", description)
        return null
    }

    /**
     * What the bridge read back from the engine's environment, worded the same way
     * whether fragmenting is on or off, so the log cannot claim more than was set.
     */
    private fun describeFragmenting(values: JSONObject, enabled: Boolean): String {
        fun value(name: String, unset: String) =
            values.optString(name, "").ifEmpty { unset }
        val carrier = value("carrier", "unset")
        val fragment = value("fragment", "unset")
        val size = value("size", "Aether's own default")
        val delay = value("delay", "Aether's own default")
        val sni = value("sniSplit", "unset")
        val carrierText = when (carrier) {
            "1" -> "HTTP/2 over TCP"
            "0" -> "HTTP/3 (QUIC)"
            else -> "not set, so HTTP/3 (QUIC)"
        }
        return if (enabled) {
            "Fragmenting on: MASQUE carrier $carrierText, ClientHello chunks $size bytes, " +
                "delay $delay ms, SNI split ${if (sni == "1") "on" else "off"} " +
                "(AETHER_MASQUE_HTTP2=$carrier, AETHER_MASQUE_H2_FRAGMENT=$fragment)"
        } else {
            "Fragmenting off: MASQUE carrier $carrierText " +
                "(AETHER_MASQUE_H2_FRAGMENT=$fragment, chunk size and delay left to Aether)"
        }
    }

    /**
     * What waiting for a freshly started tunnel produced.
     *
     * Exactly one of [serving], [ended] or [cancelled] is meaningful:
     *  - [serving]  the local SOCKS endpoint accepted a connection, so Aether has
     *               established and validated the tunnel and bound its listener.
     *  - [ended]    the Aether job finished first; [endState]/[endError] say why.
     *  - [cancelled] the user asked for a stop while the start was in flight.
     *  - none of them: the budget ran out with the job still running, i.e. the
     *    tunnel is up as far as Aether knows but its endpoint never answered.
     */
    private data class TunnelStartOutcome(
        val serving: Boolean = false,
        val ended: Boolean = false,
        val endState: String = "",
        val endError: String? = null,
        val cancelled: Boolean = false
    )

    /**
     * Waits until Aether's local SOCKS5 listener accepts a connection, giving up
     * early when the tunnel job itself ends.
     *
     * `run_masque_tunnel` and the WireGuard equivalent bind the SOCKS listener
     * only *after* the tunnel is established and validated
     * (third_party/Aether/aether/src/lib.rs), so a successful loopback connect is
     * a real "the tunnel is serving" signal rather than an assumption. The probe
     * is loopback-only and never leaves the device.
     *
     * Without this the service published RUNNING and logged "Tunnel established"
     * the moment `aether_tunnel_start` handed back a job id, i.e. before any
     * handshake had happened.
     */
    private suspend fun awaitTunnelServing(
        jobId: Long,
        bind: String,
        attempt: StartAttempt,
        budgetMs: Long
    ): TunnelStartOutcome {
        val host = bind.substringBeforeLast(":").trim().ifEmpty { "127.0.0.1" }
        val port = bind.substringAfterLast(":").trim().toIntOrNull()
        val deadline = AetherJobPolling.deadlineFor(System.currentTimeMillis(), budgetMs)
        while (AetherJobPolling.shouldKeepPolling(System.currentTimeMillis(), deadline)) {
            if (attempt.cancelled) return TunnelStartOutcome(cancelled = true)

            // A job that ends before its endpoint came up is a failure (or a
            // cancellation); sitting out the rest of the budget would only delay
            // the message the user needs.
            val poll = AetherBridge.jobPoll(jobId)
            if (poll.isFailure) {
                return TunnelStartOutcome(
                    ended = true,
                    endError = poll.exceptionOrNull()?.message ?: "job poll failed"
                )
            }
            val pollObj = poll.getOrNull()
            if (pollObj != null && pollObj.optString("state") == "done") {
                val result = pollObj.optJSONObject("result")
                val error = if (result != null && !result.optBoolean("ok", false)) {
                    result.optString("error", "the tunnel job ended with an error")
                } else {
                    null
                }
                return TunnelStartOutcome(
                    ended = true,
                    endState = result?.optString("state") ?: "",
                    endError = error
                )
            }

            if (port != null && socksAcceptsConnection(host, port)) {
                return TunnelStartOutcome(serving = true)
            }
            delay(SOCKS_PROBE_INTERVAL_MS)
        }
        return if (attempt.cancelled) TunnelStartOutcome(cancelled = true) else TunnelStartOutcome()
    }

    /** True when a loopback TCP connect to Aether's SOCKS listener succeeds. */
    private fun socksAcceptsConnection(host: String, port: Int): Boolean = try {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(host, port), SOCKS_PROBE_TIMEOUT_MS)
            socket.isConnected
        }
    } catch (_: Throwable) {
        false
    }

    private fun startAether() {
        goForeground(getString(R.string.notification_starting))
        scope.launch {
            // One attempt object per start. A stop cancels *this* attempt, so a
            // restart's new attempt is never confused with the one it replaced.
            val attempt = StartAttempt()
            synchronized(this@AetherService) { currentAttempt = attempt }

            /** True while no newer start attempt has replaced this one. */
            fun isCurrent() = synchronized(this@AetherService) { currentAttempt === attempt }

            /**
             * Reports a failed step of this attempt.
             *
             * Cancellation is cooperative: Aether answers a cancelled job with an
             * error, so a stop the user asked for arrives here as a failure. It is
             * reported as STOPPED with no error; anything else is a real Aether
             * error and is shown as one. An attempt a restart has already replaced
             * publishes nothing at all, so it cannot overwrite the newer state.
             */
            fun failed(message: String) {
                if (!isCurrent()) {
                    AetherRepository.log("INFO", "Superseded Aether start attempt ended: $message")
                    return
                }
                if (attempt.cancelled) {
                    AetherRepository.log("INFO", "Aether stopped while starting: $message")
                    AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
                } else {
                    AetherRepository.onServiceActionFinished(message, AetherState.ERROR)
                }
            }

            // The route's stored configuration. Its base holds everything the
            // settings screen edits, and its own Tor/Psiphon values are the compact
            // form of the engine's flags, so both reach the command line.
            val profileStore = com.manjaniq.core.AetherProfileStore(this@AetherService)
            val profile = try {
                profileStore.getSelectedProfile()
            } catch (_: Throwable) {
                com.manjaniq.core.models.AetherProfile.WARP_DIRECT
            }
            val profileCfg: com.manjaniq.core.models.AetherProfileConfig = try {
                profileStore.loadFor(profile)
            } catch (_: Throwable) {
                com.manjaniq.core.models.AetherProfileConfig(
                    profile.id,
                    AetherConfigStore(this@AetherService).load()
                )
            }
            val cfg = profileCfg.base
            AetherRepository.log(
                "INFO",
                "Active Aether profile: ${profile.id} -> transport=${cfg.transport}"
            )
            val validation = profileCfg.validateForProfile(profile)
            if (validation != null) {
                failed(validation)
                stopForegroundAndSelf()
                return@launch
            }
            // Port conflict with RSTA?
            val rstaPort = try {
                com.manjaniq.core.ConfigStore(this@AetherService).load().listenPort
            } catch (_: Throwable) { -1 }
            val conflict = AetherRepository.detectConflict(cfg, rstaPort)
            if (conflict != null) {
                failed(conflict)
                stopForegroundAndSelf()
                return@launch
            }
            // Duplicate start guard. A start - and the scan and tunnel it runs -
            // is one engine job now, so any live job is this service's own.
            synchronized(this@AetherService) {
                if (currentJobId != null) {
                    // Report the state the service is really in rather than
                    // assuming "tunnel up" for a job that is still starting.
                    val actual = AetherRepository.uiState.value.state
                    AetherRepository.log(
                        "WARN",
                        "Start ignored: Aether job $currentJobId is already active (state=${actual.name})"
                    )
                    AetherRepository.onServiceActionFinished(null, actual)
                    if (actual == AetherState.RUNNING) {
                        goForeground("Aether SOCKS5 ${cfg.socksBind}")
                    }
                    return@launch
                }
            }
            if (!AetherBridge.isNativeAvailable) {
                val err = AetherBridge.getLoadError()?.message ?: "Native lib not available - rebuild with libaether.so"
                failed(err)
                stopForegroundAndSelf()
                return@launch
            }
            // Version check
            val verRes = AetherBridge.version()
            if (verRes.isSuccess) {
                val v = verRes.getOrNull()?.optString("version", "") ?: ""
                AetherRepository.setVersion(v)
                AetherRepository.log("INFO", "Aether version $v")
            }

            // A start begins from this route's argument list and nothing else.
            // The engine keeps its settings in the process environment and this
            // process outlives one tunnel, so a value the previous start wrote would
            // otherwise still be there: a route that turned Tor off would keep
            // AETHER_TOR=chain and tunnel through Tor anyway. Clearing first is what
            // makes the screen and the engine agree on every start.
            val envReset = AetherBridge.envReset()
            if (envReset.isFailure) {
                failed(
                    "The engine environment could not be cleared (" +
                        (envReset.exceptionOrNull()?.message ?: "no reason was given") +
                        "), so Aether was not started: a setting from the previous " +
                        "start could still be in force"
                )
                stopForegroundAndSelf()
                return@launch
            }
            AetherRepository.log(
                "INFO",
                "Engine environment cleared (${envReset.getOrNull()?.optInt("cleared", 0)} " +
                    "AETHER_* variable(s) from the previous start)"
            )

            // Fragmenting and the carrier it needs, applied before anything dials:
            // Aether reads both from its environment when a connection is made
            // (fragment.rs:32-61, masque_h2.rs:141-149) and the scanner's verification
            // follows the same carrier choice (prober.rs:414-431), so a later write
            // would leave the scan and the tunnel disagreeing.
            val fragmentProblem = applyFragmenting(cfg, attempt)
            if (fragmentProblem != null) {
                failed(fragmentProblem)
                stopForegroundAndSelf()
                return@launch
            }

            // `aether_core_start` runs the core's own command line, which is the
            // only path that applies the whole configuration: the protocol flags,
            // the Tor and Psiphon layering, Zero Trust, routing, the upstream
            // proxy, the in-tunnel DNS list, statistics and marks. There is no
            // second start path, so what the settings screen stores is what the
            // engine is handed.
            startEngine(
                cfg = cfg,
                profileCfg = profileCfg,
                attempt = attempt,
                onFail = { message -> failed(message) },
                isCurrent = { isCurrent() }
            )
        }
    }

    /**
     * Starts Aether through `aether_core_start` - the core's own command line -
     * and holds the job until it ends.
     *
     * The argument list is [com.manjaniq.core.models.AetherConfig.toAetherArgs],
     * i.e. the same model the settings screen edits, so nothing is translated
     * twice and every control in the UI has a flag behind it: gool and mim
     * (`--gool`, `--mim`), the Tor and Psiphon layering (`--tor*`,
     * `--psiphon*`), Zero Trust (`--team`, `--access-*`, `--gateway`), routing
     * (`--routes`, `--route-block`, `--route-direct`), `--upstream`, `--dns`,
     * `--quick-reconnect`, `--stats`, `--mark` and `--register`.
     *
     * The readiness signal: `awaitTunnelServing` polls the job and probes the
     * loopback SOCKS listener, and Aether binds that listener only after the
     * tunnel is established and validated. Scanning happens inside the same job,
     * so the budget covers the engine's own scan deadline plus the readiness
     * window rather than either alone.
     *
     * On failure the reason goes to [onFail], which knows whether a stop the user
     * asked for turned this into a cancellation. Nothing here invents state: the
     * job id is published while the job exists and released before returning.
     */
    private suspend fun startEngine(
        cfg: com.manjaniq.core.models.AetherConfig,
        profileCfg: com.manjaniq.core.models.AetherProfileConfig,
        attempt: StartAttempt,
        onFail: (String) -> Unit,
        isCurrent: () -> Boolean
    ) {
        val filesDir = applicationContext.filesDir.absolutePath
        val helpers = AetherHelpers.paths(applicationContext)

        val args = profileCfg.toAetherArgs(filesDir, helpers)
        // Only the flags are logged: values can hold a Zero Trust secret
        // (--access-secret, --access-token) or a bridge line.
        val flags = args.filter { it.startsWith("-") }.distinct()
        AetherRepository.log(
            "INFO",
            "Aether start: ${args.size} arguments via aether_core_start, " +
                "flags ${flags.joinToString(" ")}"
        )

        val started = AetherBridge.coreStart(args)
        if (started.isFailure) {
            onFail(
                "Aether refused the configuration: " +
                    (started.exceptionOrNull()?.message ?: "no reason was given")
            )
            stopForegroundAndSelf()
            return
        }
        val startedObject = started.getOrNull()!!
        val jobId = startedObject.optLong("job", -1L)
        if (jobId == -1L) {
            onFail("aether_core_start returned no job id: $startedObject")
            stopForegroundAndSelf()
            return
        }
        AetherRepository.setActiveJob(jobId)
        synchronized(this@AetherService) { currentJobId = jobId }

        // `--register` registers identities and exits without scanning or
        // tunnelling (cli.rs), so it is a one-shot job with a registration
        // budget, not a tunnel that will ever answer on SOCKS.
        if (cfg.engine.api.register.isNotBlank()) {
            val registerBudget = AetherJobPolling.identityTimeoutMs()
            AetherRepository.log(
                "INFO",
                "Register-only run: Aether registers '${cfg.engine.api.register}' and exits " +
                    "(up to ${registerBudget / 1000}s for the WARP API)"
            )
            val done = pollJob(jobId, registerBudget)
            AetherBridge.jobFree(jobId)
            synchronized(this@AetherService) { if (currentJobId == jobId) currentJobId = null }
            AetherRepository.setActiveJob(null)
            when {
                !isCurrent() -> AetherRepository.log(
                    "INFO",
                    "Superseded Aether registration finished"
                )
                done == null || done.isFailure -> {
                    val raw = done?.exceptionOrNull()?.message
                    // When registration could not reach the API, say what this
                    // device could see at that moment: read-only platform facts,
                    // no connection of our own.
                    when (AetherRegistrationOutcome.classify(raw)) {
                        AetherRegistrationOutcome.Failure.NO_ANSWER,
                        AetherRegistrationOutcome.Failure.TLS,
                        AetherRegistrationOutcome.Failure.ECH,
                        AetherRegistrationOutcome.Failure.TIMEOUT -> {
                            val facts = AetherNetworkCheck.factsOf(applicationContext)
                            AetherRepository.log("INFO", AetherNetworkCheck.summarize(facts))
                            if (facts != null && !AetherNetworkCheck.looksReachable(facts)) {
                                AetherRepository.log(
                                    "WARN",
                                    "The device itself was not on a usable network, so this is " +
                                        "not an Aether fault: bring the device online and try again"
                                )
                            }
                        }
                        else -> Unit
                    }
                    onFail(
                        AetherRegistrationOutcome.report(raw, registerBudget) +
                            " No identity was registered."
                    )
                }
                else -> {
                    AetherRepository.log(
                        "OK",
                        "Identities registered for '${cfg.engine.api.register}'; " +
                            "the files are beside the configured identity path"
                    )
                    AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
                }
            }
            if (isCurrent()) stopForegroundAndSelf()
            return
        }

        val readinessBudget =
            AetherJobPolling.scanTimeoutFor(cfg.transport, cfg.scanMode) + SOCKS_READY_BUDGET_MS
        AetherRepository.log(
            "INFO",
            "Aether job $jobId started; waiting up to ${readinessBudget / 1000}s for " +
                "SOCKS5 ${cfg.socksBind} to accept connections (scan, tunnel and validation " +
                "all run inside this one job)"
        )
        val outcome = awaitTunnelServing(jobId, cfg.socksBind, attempt, readinessBudget)

        if (outcome.ended || outcome.cancelled) {
            AetherBridge.jobFree(jobId)
            synchronized(this@AetherService) { if (currentJobId == jobId) currentJobId = null }
            AetherRepository.setActiveJob(null)
            when {
                !isCurrent() -> AetherRepository.log(
                    "INFO",
                    "Superseded Aether start attempt ended while starting"
                )
                attempt.cancelled || outcome.cancelled || outcome.endState == "stopped" -> {
                    AetherRepository.log("INFO", "Aether stopped before the tunnel came up")
                    AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
                }
                else -> {
                    val message = outcome.endError
                        ?: "Aether ended before SOCKS5 ${cfg.socksBind} came up " +
                        "(state=${outcome.endState})"
                    AetherRepository.log("ERROR", "Aether failed: $message")
                    AetherRepository.onServiceActionFinished(
                        "Aether failed: $message",
                        AetherState.ERROR
                    )
                }
            }
            if (isCurrent()) stopForegroundAndSelf()
            return
        }

        if (!outcome.serving) {
            // The job is still running: Aether has not given up, the loopback
            // probe simply has not been answered yet. Say exactly that and keep
            // holding the tunnel rather than inventing a failure.
            AetherRepository.log(
                "WARN",
                "SOCKS5 ${cfg.socksBind} did not answer within ${readinessBudget / 1000}s; " +
                    "job $jobId is still running and its outcome is reported here"
            )
        } else {
            AetherRepository.log("OK", "SOCKS5 ${cfg.socksBind} is accepting connections")
        }

        if (!isCurrent()) {
            AetherRepository.log("INFO", "Superseded Aether start attempt finished the readiness check")
            AetherBridge.jobFree(jobId)
            return
        }

        AetherRepository.updateState(AetherState.RUNNING)
        AetherRepository.setEndpoint(cfg.peer.trim().ifEmpty { "scan" }, cfg.socksBind)
        goForeground("Aether SOCKS5 ${cfg.socksBind} [${cfg.transport}, ${cfg.scanMode}]")
        AetherRepository.onServiceActionFinished(null, AetherState.RUNNING)
        AetherRepository.log(
            "OK",
            "Tunnel running socks=${cfg.socksBind} transport=${cfg.transport} " +
                "mode=${cfg.scanMode} noize=${cfg.noizeProfile}"
        )

        val exit = pollJob(jobId, timeoutMs = null)
        AetherBridge.jobFree(jobId)
        synchronized(this@AetherService) { if (currentJobId == jobId) currentJobId = null }
        AetherRepository.setActiveJob(null)

        val exitState = exit?.getOrNull()?.optString("state") ?: ""
        val exitError = exit?.exceptionOrNull()?.message
        when {
            !isCurrent() -> AetherRepository.log(
                "INFO",
                "Superseded Aether attempt ended (state=$exitState)"
            )
            attempt.cancelled || exitState == "stopped" -> {
                AetherRepository.log("INFO", "Tunnel stopped")
                AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
            }
            exitError != null -> {
                AetherRepository.log("ERROR", "Tunnel failed: $exitError")
                AetherRepository.onServiceActionFinished("Tunnel failed: $exitError", AetherState.ERROR)
            }
            else -> {
                AetherRepository.log("INFO", "Tunnel closed by the gateway (state=$exitState)")
                AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
            }
        }
        if (isCurrent()) {
            stopForegroundAndSelf()
        } else {
            AetherRepository.log("INFO", "Superseded Aether attempt cleaned up")
        }
    }

    private fun stopAether() {
        scope.launch {
            AetherRepository.log("INFO", "Stopping Aether...")
            // A start in flight must report STOPPED, not an Aether error.
            cancelCurrentAttempt()
            // Cancel active job first
            val jId = synchronized(this@AetherService) { currentJobId }
            if (jId != null) {
                try { AetherBridge.jobCancel(jId) } catch (_: Throwable) {}
                // Wait briefly for poll to notice
                delay(300)
                try { AetherBridge.jobFree(jId) } catch (_: Throwable) {}
                synchronized(this@AetherService) { currentJobId = null }
            }
            AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
            AetherRepository.setActiveJob(null)
            stopForegroundAndSelf()
        }
    }

    private fun restartAether() {
        scope.launch {
            AetherRepository.log("INFO", "Restarting Aether...")
            // Cancel the attempt in flight first so its cancellation is not
            // reported as an error, then start a fresh one.
            cancelCurrentAttempt()
            // Cancel current then start
            val jId = synchronized(this@AetherService) { currentJobId }
            if (jId != null) {
                try { AetherBridge.jobCancel(jId); AetherBridge.jobFree(jId) } catch (_: Throwable) {}
                synchronized(this@AetherService) { currentJobId = null }
            }
            delay(200)
            startAether()
        }
    }

    private suspend fun pollJob(jobId: Long, timeoutMs: Long? = AetherJobPolling.DEFAULT_TIMEOUT_MS): com.manjaniq.core.models.Result<JSONObject>? {
        // `timeoutMs == null` means "poll until the job finishes". Passing
        // Long.MAX_VALUE here used to overflow the deadline into a negative
        // number, so the tunnel poll returned at once and the service freed —
        // and therefore cancelled — the tunnel it had just started.
        val deadline = AetherJobPolling.deadlineFor(System.currentTimeMillis(), timeoutMs)
        while (AetherJobPolling.shouldKeepPolling(System.currentTimeMillis(), deadline)) {
            val poll = AetherBridge.jobPoll(jobId)
            if (poll.isFailure) {
                AetherRepository.setLastPoll("poll failed: ${poll.exceptionOrNull()?.message}")
                return poll
            }
            val obj = poll.getOrNull()!!
            AetherRepository.setLastPoll(obj.toString())
            val state = obj.optString("state")
            if (state == "done") {
                val result = obj.optJSONObject("result")
                return if (result != null) {
                    if (result.optBoolean("ok", false)) com.manjaniq.core.models.Result.success(result)
                    else com.manjaniq.core.models.Result.failure(result.optString("error", "job done error"))
                } else {
                    com.manjaniq.core.models.Result.success(obj)
                }
            }
            // still running
            delay(AetherJobPolling.POLL_INTERVAL_MS)
        }
        return null // timeout
    }

    private fun goForeground(statusText: String) {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this, 1, Intent(this, AetherService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_engine)
            .setContentTitle(getString(R.string.notification_title_aether))
            .setContentText(statusText)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stopAction)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name_aether),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notification_channel_desc_aether) }
        manager.createNotificationChannel(channel)
    }

    /**
     * One start attempt. The cancellation flag belongs to the attempt, not to
     * the service, so a restart cannot be read as a cancellation of the start it
     * is replacing (and vice versa).
     */
    private class StartAttempt {
        @Volatile
        var cancelled: Boolean = false
    }

    companion object {
        const val ACTION_START = "com.manjaniq.action.START_AETHER"
        const val ACTION_STOP = "com.manjaniq.action.STOP_AETHER"
        const val ACTION_RESTART = "com.manjaniq.action.RESTART_AETHER"
        private const val CHANNEL_ID = "aether_engine"
        private const val NOTIFICATION_ID = 40444

        /** Per-probe connect timeout for the loopback SOCKS readiness check. */
        private const val SOCKS_PROBE_TIMEOUT_MS = 400

        /** Delay between two readiness probes. */
        private const val SOCKS_PROBE_INTERVAL_MS = 500L

        /**
         * Total readiness budget. Aether's own MASQUE startup deadline is 30s
         * (`--startup-secs`) plus 10s of data-plane validation
         * (`--validate-secs`), so 45s covers a slow but healthy start.
         */
        private const val SOCKS_READY_BUDGET_MS = 45_000L
    }
}
