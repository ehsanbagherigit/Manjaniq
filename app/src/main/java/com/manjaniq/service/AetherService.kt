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
import com.manjaniq.core.AetherScanOutcome
import com.manjaniq.core.EngineRepository
import com.manjaniq.core.models.AetherState
import com.manjaniq.core.models.Result
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
 * Phase 1: SOCKS5 only (no VpnService/TUN). It starts the Aether core
 * via `aether_tunnel_start` or `aether_core_start` and keeps it running
 * while the user is in other apps. RSTA EngineService remains separate;
 * this service is independent and must not corrupt RSTA's EngineRepository.
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
    private var currentIdentityId: Long? = null

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
            currentIdentityId?.let { AetherBridge.identityFree(it) }
        } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    /** Marks the start attempt in flight as cancelled, so it reports STOPPED, not ERROR. */
    private fun cancelCurrentAttempt() {
        synchronized(this) { currentAttempt?.cancelled = true }
    }

    /**
     * Logs, truthfully, which configured Aether settings this build cannot
     * forward. Every one of them is a real Aether feature; the gap is that
     * `aether_identity_open` / `aether_scan_start` / `aether_tunnel_start` have
     * no payload field for it and only `aether_core_start` (the full CLI) sets
     * the matching `AETHER_*` environment variable. Nothing here changes what is
     * sent to Aether — it only stops the log from implying more was applied.
     */
    private fun logUnforwardedSettings(cfg: com.manjaniq.core.models.AetherConfig) {
        val transport = com.manjaniq.core.models.AetherTransportChoice.byId(cfg.transport)
        if (transport != null && !transport.selectable) {
            AetherRepository.log(
                "WARN",
                "Transport '${cfg.transport}' cannot be requested through aether_tunnel_start " +
                    "(api::Transport parses only masque|wg); Aether will run plain MASQUE"
            )
        }
        // AetherConfig owns the list, so the log and the settings screen cannot
        // disagree about what this path drops.
        val skipped = cfg.commandLineOnlyFeatures()
        if (skipped.isNotEmpty()) {
            AetherRepository.log(
                "WARN",
                "Saved but not applied on this start path (the identity/scan/tunnel payloads " +
                    "carry no field for them): ${skipped.joinToString(", ")}. Set the start mode " +
                    "to '" + com.manjaniq.core.models.AetherStartMode.FULL_ENGINE.id +
                    "' to apply them."
            )
        }
    }

    /**
     * Writes this route's fragmenting settings - and the carrier they need - into the
     * engine's environment, then says what the environment actually holds.
     *
     * `fragment.rs` has exactly one consumer, `masque_h2.rs` (:239, :284, :402), so the
     * MASQUE-over-HTTP/2 carrier has to be selected for fragments to exist at all;
     * `AetherConfig.usesHttp2Carrier()` pairs the two and `validate()` refuses
     * fragmenting on any other transport. The CLI does the same pairing with `--h2` and
     * `--fragment` (cli.rs:579, :602-605).
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

            // Prefer profile-specific config, fallback to legacy single config
            val cfg: com.manjaniq.core.models.AetherConfig = try {
                val profileStore = com.manjaniq.core.AetherProfileStore(this@AetherService)
                val prof = profileStore.getSelectedProfile()
                val profCfg = profileStore.loadFor(prof)
                // If selected profile is not WARP_DIRECT but has different SOCKS/port, use it
                profCfg.base
            } catch (_: Throwable) {
                val cfgStore = AetherConfigStore(this@AetherService)
                cfgStore.load()
            }
            // Log which profile is active
            try {
                val ps = com.manjaniq.core.AetherProfileStore(this@AetherService).getSelectedProfile()
                AetherRepository.log("INFO", "Active Aether profile: ${ps.id} -> transport=${cfg.transport}")
                if (ps.capabilityInThisBuild() != com.manjaniq.core.models.ProfileCapability.Available) {
                    AetherRepository.log("WARN", "Profile ${ps.id} requires external component or is not verified - tunnel will use base Aether settings only")
                }
            } catch (_: Throwable) {}
            val validation = cfg.validate()
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
            // Say out loud which of the configured settings this path can apply.
            // Aether's FFI payloads carry no field for these (see
            // AetherCapabilities), so they are recorded but not forwarded; the
            // log is where the user can see that instead of guessing.
            logUnforwardedSettings(cfg)

            // Duplicate start guard
            synchronized(this@AetherService) {
                if (currentJobId != null) {
                    // Report the state the service is really in. currentJobId is
                    // also set while a *scan* is running, so claiming RUNNING here
                    // showed "tunnel up" for a start that had only begun scanning.
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

            // Everything else the core accepts reaches it only through its own
            // command line, so a route that asks for those features is started
            // there (aether_core_start) instead of through the three granular
            // calls. The two paths publish the same states and share the same
            // readiness probe, stop and cleanup, so the UI cannot tell them apart
            // except by what the log says was applied.
            if (cfg.fullEngine()) {
                startFullEngine(
                    cfg = cfg,
                    attempt = attempt,
                    onFail = { message -> failed(message) },
                    isCurrent = { isCurrent() }
                )
                return@launch
            }

            // Identity handling: open or create via FFI
            val filesDir = applicationContext.filesDir.absolutePath
            // Build identity open payload
            // path must be absolute for aether
            val identityPath = "$filesDir/${cfg.identityPath}"
            AetherRepository.log("INFO", "Opening identity at $filesDir/${cfg.identityPath} transport=${cfg.transport}")
            // Ensure parent dir exists
            try { java.io.File(identityPath).parentFile?.mkdirs() } catch (_: Exception) {}

            val teamJson = if (cfg.teamName.isNotEmpty()) {
                JSONObject().apply { put("team", cfg.teamName) }
            } else null

            val openRes = AetherBridge.identityOpen(
                path = identityPath,
                transport = cfg.transport,
                team = teamJson
            )
            if (openRes.isFailure) {
                // aether_identity_open itself refused the payload, before any job existed
                failed(
                    AetherRegistrationOutcome.report(
                        openRes.exceptionOrNull()?.message,
                        AetherJobPolling.identityTimeoutMs()
                    )
                )
                stopForegroundAndSelf()
                return@launch
            }
            val openObj = openRes.getOrNull()!!
            // openObj contains job id first, need poll
            val jobId = openObj.optLong("job", -1)
            if (jobId == -1L) {
                failed("identity_open returned no job id: $openObj")
                stopForegroundAndSelf()
                return@launch
            }
            // The engine's own schedule bounds registration: API_ATTEMPTS attempts of
            // API_TIMEOUT each with backoff, plus any Retry-After it is told to sleep
            // (account.rs:248-260, 293-304). A shorter app deadline would free - and so
            // cancel - a job that is still legitimately retrying, and then report the
            // app's timeout instead of the engine's answer.
            // Publish the identity job as the active one, as the scan and tunnel blocks
            // do. Without this `stopAether()` and `onDestroy()` find `currentJobId` null
            // while registration is in flight, so a Stop leaves the engine running its
            // five attempts in the background and the job is never freed.
            AetherRepository.setActiveJob(jobId)
            synchronized(this@AetherService) { currentJobId = jobId }
            val identityTimeout = AetherJobPolling.identityTimeoutMs()
            AetherRepository.log(
                "INFO",
                "Waiting for identity job $jobId (Aether allows " +
                    "${AetherJobPolling.API_ATTEMPTS} attempts of " +
                    "${AetherJobPolling.API_ATTEMPT_TIMEOUT_MS / 1000}s with backoff and any " +
                    "Retry-After wait, so this can take up to ${identityTimeout / 1000}s)"
            )
            val openDone = pollJob(jobId, identityTimeout)
            if (openDone == null || openDone.isFailure) {
                val rawFailure = openDone?.exceptionOrNull()?.message
                // When registration could not reach the API, say what this device could
                // see at that moment: read-only platform facts, no connection of our own.
                when (AetherRegistrationOutcome.classify(rawFailure)) {
                    AetherRegistrationOutcome.Failure.NO_ANSWER,
                    AetherRegistrationOutcome.Failure.TLS,
                    AetherRegistrationOutcome.Failure.ECH,
                    AetherRegistrationOutcome.Failure.TIMEOUT -> {
                        val facts = AetherNetworkCheck.factsOf(applicationContext)
                        AetherRepository.log("INFO", AetherNetworkCheck.summarize(facts))
                        if (facts != null && !AetherNetworkCheck.looksReachable(facts)) {
                            AetherRepository.log(
                                "WARN",
                                "The device itself was not on a usable network, so this is not an " +
                                    "Aether fault: bring the device online and start again"
                            )
                        }
                    }
                    else -> Unit
                }
                failed(AetherRegistrationOutcome.report(rawFailure, identityTimeout))
                AetherBridge.jobFree(jobId)
                synchronized(this@AetherService) { if (currentJobId == jobId) currentJobId = null }
                AetherRepository.setActiveJob(null)
                stopForegroundAndSelf()
                return@launch
            }
            val openResult = openDone.getOrNull()!!
            // openResult should have identity field
            val identityId = openResult.optLong("identity", -1)
            if (identityId == -1L) {
                failed("identity open failed: $openResult")
                AetherBridge.jobFree(jobId)
                synchronized(this@AetherService) { if (currentJobId == jobId) currentJobId = null }
                AetherRepository.setActiveJob(null)
                stopForegroundAndSelf()
                return@launch
            }
            AetherRepository.setIdentity(identityId)
            currentIdentityId = identityId
            AetherBridge.jobFree(jobId)
            synchronized(this@AetherService) { if (currentJobId == jobId) currentJobId = null }
            AetherRepository.setActiveJob(null)
            // open_identity derives the file it writes from the configured path and the
            // transport (api.rs:256-264: aether_identity -> aether_identity-masque), so
            // log the paths the engine reports rather than only the configured one.
            val derivedPath = openResult.optString("path").ifEmpty { identityPath }
            val lastconn = openResult.optString("lastconn_path").ifEmpty { "not reported" }
            val claimedModel = openResult.optString("model").ifEmpty { "engine default" }
            AetherRepository.log(
                "OK",
                "Identity ready id=$identityId at $derivedPath (lastconn $lastconn, model $claimedModel)"
            )

            // Determine peer: if cfg.peer empty, we must scan first else direct tunnel
            val tunnelPeer = cfg.peer.trim().ifEmpty { null }
            var chosenPeer: String? = tunnelPeer

            if (chosenPeer == null) {
                // Scan workflow
                AetherRepository.log(
                    "INFO",
                    "Starting scan mode=${cfg.scanMode} transport=${cfg.transport} ip=${cfg.ipVersion}"
                )
                val scanPayloadJob = AetherBridge.scanStart(
                    identityId = identityId,
                    transport = cfg.transport,
                    mode = cfg.scanMode,
                    // ScanPayload.ip -> prober::IpScan::parse, which accepts exactly
                    // the v4 | v6 | both values AetherConfig.ipVersion holds. It was
                    // never sent, so the IP-version choice had no effect and every
                    // scan ran IPv4-only.
                    ip = cfg.ipVersion.ifBlank { null },
                    profile = cfg.noizeProfile.takeIf { it != "off" },
                    ech = cfg.echEnabled.takeIf { it }
                )
                if (scanPayloadJob.isFailure) {
                    val err = scanPayloadJob.exceptionOrNull()?.message ?: "scan_start failed"
                    failed(err)
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                val scanJobObj = scanPayloadJob.getOrNull()!!
                val scanJobId = scanJobObj.optLong("job", -1)
                if (scanJobId == -1L) {
                    failed("scan returned no job: $scanJobObj")
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                // Aether's own scan budget is per transport and per mode, and
                // three of the ten combinations are longer than the flat 120 s
                // this poll used to apply, so `thorough` and `ironclad` could
                // never succeed through the app: the deadline expired, the job was
                // freed, and `aether_job_free` cancels the scan it removes.
                val scanTimeoutMs = AetherJobPolling.scanTimeoutFor(cfg.transport, cfg.scanMode)
                AetherRepository.log("INFO", "Scan wait budget: ${scanTimeoutMs / 1000}s for ${cfg.transport}/${cfg.scanMode}")
                AetherRepository.setActiveJob(scanJobId)
                synchronized(this@AetherService) { currentJobId = scanJobId }
                val scanDone = pollJob(scanJobId, scanTimeoutMs)
                AetherBridge.jobFree(scanJobId)
                synchronized(this@AetherService) { if (currentJobId == scanJobId) currentJobId = null }
                AetherRepository.setActiveJob(null)

                if (scanDone == null || scanDone.isFailure) {
                    // `null` is this app's own deadline expiring; otherwise the
                    // message is an `AetherError` Display string from the core.
                    // Either way the state ends up STOPPED or ERROR with the job
                    // freed and the identity closed, so a retry starts clean.
                    failed(AetherScanOutcome.report(scanDone?.exceptionOrNull()?.message, scanTimeoutMs))
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                val scanResult = scanDone.getOrNull()!!
                // A successful scan replies
                //   {"endpoint": {"ip": ..., "port": ..., "rtt_ms": ...}, "ok": true}
                // (ffi.rs `aether_scan_start` + api.rs `Endpoint`). Reading the
                // "endpoint" member as a string returned that object's JSON text,
                // which `aether_tunnel_start`'s `socket_of` then rejected with
                // "the peer address ... is not an address:port" - so the endpoint
                // the scan had chosen was discarded even when the scan worked.
                val endpoint = AetherScanOutcome.endpointFrom(scanResult)
                if (endpoint == null) {
                    failed("Scan finished without a usable endpoint (Aether replied: $scanResult)")
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                chosenPeer = endpoint
                AetherRepository.log("OK", "Scan chose endpoint $endpoint")
                AetherRepository.setEndpoint(endpoint, cfg.socksBind)
            }

            // Tunnel start
            AetherRepository.log("INFO", "Connecting tunnel to $chosenPeer via ${cfg.transport} socks=${cfg.socksBind}")
            val tunnelRes = AetherBridge.tunnelStart(
                identityId = identityId,
                peer = chosenPeer!!,
                transport = cfg.transport,
                socks = cfg.socksBind,
                http = cfg.httpProxyBind.trim().ifEmpty { null },
                // `x.takeIf { p } ?: x` is x for every input, so the old expression
                // was a no-op that only obscured the intent: the configured profile
                // is always sent, including "off" (aethernoize::from_profile has an
                // explicit off case).
                profile = cfg.noizeProfile.ifBlank { null },
                keepalive = 5,
                ech = cfg.echEnabled.takeIf { it }
            )
            if (tunnelRes.isFailure) {
                val err = tunnelRes.exceptionOrNull()?.message ?: "tunnel_start failed"
                failed(err)
                cleanupIdentity(identityId)
                stopForegroundAndSelf()
                return@launch
            }
            val tunnelJobObj = tunnelRes.getOrNull()!!
            val tunnelJobId = tunnelJobObj.optLong("job", -1)
            if (tunnelJobId == -1L) {
                failed("tunnel returned no job: $tunnelJobObj")
                cleanupIdentity(identityId)
                stopForegroundAndSelf()
                return@launch
            }
            AetherRepository.setActiveJob(tunnelJobId)
            synchronized(this@AetherService) { currentJobId = tunnelJobId }
            AetherRepository.log(
                "INFO",
                "Tunnel job $tunnelJobId started; waiting for SOCKS5 ${cfg.socksBind} to accept connections"
            )

            // Aether binds the SOCKS listener only after the tunnel is established
            // and validated, so the endpoint answering a loopback connect is the
            // real "the tunnel is serving" signal. Without this check the service
            // published RUNNING and logged "Tunnel established" the moment
            // aether_tunnel_start handed back a job id, before any handshake.
            val startOutcome = awaitTunnelServing(tunnelJobId, cfg.socksBind, attempt, SOCKS_READY_BUDGET_MS)

            if (startOutcome.ended || startOutcome.cancelled) {
                // The tunnel never came up: release the job and say why.
                AetherBridge.jobFree(tunnelJobId)
                synchronized(this@AetherService) { if (currentJobId == tunnelJobId) currentJobId = null }
                AetherRepository.setActiveJob(null)
                when {
                    !isCurrent() -> AetherRepository.log(
                        "INFO",
                        "Superseded Aether start attempt ended while starting"
                    )
                    // `attempt.cancelled` is re-read live: a stop frees the job, and
                    // a poll that loses that race answers "there is no job N", which
                    // must not be shown as an Aether failure.
                    attempt.cancelled || startOutcome.cancelled || startOutcome.endState == "stopped" -> {
                        AetherRepository.log("INFO", "Tunnel stopped before it came up")
                        AetherRepository.onServiceActionFinished(null, AetherState.STOPPED)
                    }
                    else -> {
                        val msg = startOutcome.endError
                            ?: "tunnel ended before SOCKS5 ${cfg.socksBind} came up (state=${startOutcome.endState})"
                        AetherRepository.log("ERROR", "Tunnel failed: $msg")
                        AetherRepository.onServiceActionFinished("Tunnel failed: $msg", AetherState.ERROR)
                    }
                }
                cleanupIdentity(identityId)
                if (isCurrent()) stopForegroundAndSelf()
                return@launch
            }

            if (startOutcome.serving) {
                AetherRepository.log("OK", "SOCKS5 ${cfg.socksBind} is accepting connections")
            } else {
                AetherRepository.log(
                    "WARN",
                    "SOCKS5 ${cfg.socksBind} did not answer within ${SOCKS_READY_BUDGET_MS / 1000}s; " +
                        "tunnel job $tunnelJobId is still running and is reported here if it fails"
                )
            }
            if (!isCurrent()) {
                AetherRepository.log("INFO", "Superseded Aether start attempt finished the readiness check")
                AetherBridge.jobFree(tunnelJobId)
                cleanupIdentity(identityId)
                return@launch
            }

            AetherRepository.updateState(AetherState.RUNNING)
            // Promote notification to running proxy status
            goForeground("Aether SOCKS5 ${cfg.socksBind} → $chosenPeer [${cfg.transport}]")
            AetherRepository.onServiceActionFinished(null, AetherState.RUNNING)
            AetherRepository.setEndpoint(chosenPeer, cfg.socksBind)
            AetherRepository.log("OK", "Tunnel running socks=${cfg.socksBind} peer=$chosenPeer")

            // Hold the tunnel: poll until it exits (closed or error).
            // This suspends this coroutine; stopAether cancels the job.
            // timeoutMs = null means unbounded: a tunnel job only reports "done"
            // when the tunnel exits, so any finite deadline would end the tunnel.
            val tunnelExit = pollJob(tunnelJobId, timeoutMs = null)
            // If we get here, the tunnel job is done (or the poll itself failed).
            AetherBridge.jobFree(tunnelJobId)
            synchronized(this@AetherService) { if (currentJobId == tunnelJobId) currentJobId = null }
            AetherRepository.setActiveJob(null)

            // api::connect answers {"state":"stopped"} for a cancelled tunnel and
            // {"state":"closed"} for one that ended by itself; anything else is an
            // error string. A stop the user asked for is not an error.
            val exitState = tunnelExit?.getOrNull()?.optString("state") ?: ""
            val exitError = tunnelExit?.exceptionOrNull()?.message
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
            cleanupIdentity(identityId)
            if (isCurrent()) {
                stopForegroundAndSelf()
            } else {
                // A restart owns the service now; stopping it would kill the newer start.
                AetherRepository.log("INFO", "Superseded Aether attempt cleaned up")
            }
        }
    }

    /**
     * Starts Aether through `aether_core_start` - the core's own command line -
     * and holds the job until it ends.
     *
     * This is the only path that applies the whole configuration: gool and mim
     * (`--gool`, `--mim`), the Tor and Psiphon layering (`--tor*`,
     * `--psiphon*`), Zero Trust (`--team`, `--access-*`, `--gateway`), routing
     * (`--routes`, `--route-block`, `--route-direct`), `--upstream`, `--dns`,
     * `--quick-reconnect`, `--stats`, `--mark` and `--register`. The argument
     * list is [com.manjaniq.core.models.AetherConfig.toAetherArgs], i.e. the same
     * model the settings screen edits, so nothing is translated twice.
     *
     * The readiness signal is unchanged: `awaitTunnelServing` polls the job and
     * probes the loopback SOCKS listener, and Aether binds that listener only
     * after the tunnel is established and validated. Scanning now happens inside
     * the same job, so the budget covers the engine's own scan deadline plus the
     * readiness window rather than either alone.
     *
     * On failure the reason goes to [onFail], which knows whether a stop the user
     * asked for turned this into a cancellation. Nothing here invents state: the
     * job id is published while the job exists and released before returning.
     */
    private suspend fun startFullEngine(
        cfg: com.manjaniq.core.models.AetherConfig,
        attempt: StartAttempt,
        onFail: (String) -> Unit,
        isCurrent: () -> Boolean
    ) {
        val filesDir = applicationContext.filesDir.absolutePath
        val helpers = AetherHelpers.paths(applicationContext)
        AetherRepository.log("INFO", AetherHelpers.summary())

        val args = cfg.toAetherArgs(filesDir, helpers)
        // Only the flags are logged: values can hold a Zero Trust secret
        // (--access-secret, --access-token) or a bridge line.
        val flags = args.filter { it.startsWith("-") }.distinct()
        AetherRepository.log(
            "INFO",
            "Full-engine start: ${args.size} arguments, flags ${flags.joinToString(" ")}"
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
            "Full-engine job $jobId started; waiting up to ${readinessBudget / 1000}s for " +
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
        goForeground("Aether SOCKS5 ${cfg.socksBind} [${cfg.transport}, full engine]")
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
            val iId = synchronized(this@AetherService) { currentIdentityId }
            if (iId != null) {
                try { AetherBridge.identityFree(iId) } catch (_: Throwable) {}
                synchronized(this@AetherService) { currentIdentityId = null }
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
            val iId = synchronized(this@AetherService) { currentIdentityId }
            if (iId != null) {
                try { AetherBridge.identityFree(iId) } catch (_: Throwable) {}
                synchronized(this@AetherService) { currentIdentityId = null }
            }
            delay(200)
            startAether()
        }
    }

    private fun cleanupIdentity(id: Long) {
        try { AetherBridge.identityFree(id) } catch (_: Throwable) {}
        synchronized(this@AetherService) {
            // Only clear the published identity when it is the one this call owns,
            // so an attempt a restart already replaced cannot blank the newer one.
            if (currentIdentityId == id) {
                currentIdentityId = null
                AetherRepository.setIdentity(null)
            }
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
