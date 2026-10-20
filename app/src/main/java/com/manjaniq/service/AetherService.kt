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
import com.manjaniq.core.AetherRepository
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
        // Best-effort native cleanup: cancel job + free
        try {
            currentJobId?.let { AetherBridge.jobCancel(it) }
            currentJobId?.let { AetherBridge.jobFree(it) }
            currentIdentityId?.let { AetherBridge.identityFree(it) }
        } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun startAether() {
        goForeground(getString(R.string.notification_starting))
        scope.launch {
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
                if (ps.capability != com.manjaniq.core.models.ProfileCapability.Available) {
                    AetherRepository.log("WARN", "Profile ${ps.id} requires external component or is not verified - tunnel will use base Aether settings only")
                }
            } catch (_: Throwable) {}
            val validation = cfg.validate()
            if (validation != null) {
                AetherRepository.onServiceActionFinished(validation, AetherState.ERROR)
                stopForegroundAndSelf()
                return@launch
            }
            // Port conflict with RSTA?
            val rstaPort = try {
                com.manjaniq.core.ConfigStore(this@AetherService).load().listenPort
            } catch (_: Throwable) { -1 }
            val conflict = AetherRepository.detectConflict(cfg, rstaPort)
            if (conflict != null) {
                AetherRepository.onServiceActionFinished(conflict, AetherState.ERROR)
                stopForegroundAndSelf()
                return@launch
            }
            // Duplicate start guard
            synchronized(this@AetherService) {
                if (currentJobId != null) {
                    AetherRepository.onServiceActionFinished("Aether already running (job $currentJobId)", AetherState.RUNNING)
                    goForeground("Aether running: ${cfg.socksBind} (SOCKS5)")
                    return@launch
                }
            }
            if (!AetherBridge.isNativeAvailable) {
                val err = AetherBridge.getLoadError()?.message ?: "Native lib not available - rebuild with libaether.so"
                AetherRepository.onServiceActionFinished(err, AetherState.ERROR)
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
                val err = openRes.exceptionOrNull()?.message ?: "identity open failed"
                AetherRepository.onServiceActionFinished(err, AetherState.ERROR)
                stopForegroundAndSelf()
                return@launch
            }
            val openObj = openRes.getOrNull()!!
            // openObj contains job id first, need poll
            val jobId = openObj.optLong("job", -1)
            if (jobId == -1L) {
                AetherRepository.onServiceActionFinished("identity_open returned no job id: $openObj", AetherState.ERROR)
                stopForegroundAndSelf()
                return@launch
            }
            AetherRepository.log("INFO", "Waiting for identity job $jobId...")
            val openDone = pollJob(jobId)
            if (openDone == null || openDone.isFailure) {
                val errMsg = openDone?.exceptionOrNull()?.message ?: "identity job timed out"
                AetherRepository.onServiceActionFinished(errMsg, AetherState.ERROR)
                AetherBridge.jobFree(jobId)
                stopForegroundAndSelf()
                return@launch
            }
            val openResult = openDone.getOrNull()!!
            // openResult should have identity field
            val identityId = openResult.optLong("identity", -1)
            if (identityId == -1L) {
                AetherRepository.onServiceActionFinished("identity open failed: $openResult", AetherState.ERROR)
                AetherBridge.jobFree(jobId)
                stopForegroundAndSelf()
                return@launch
            }
            // Summary includes lastconn_path
            AetherRepository.setIdentity(identityId)
            currentIdentityId = identityId
            AetherBridge.jobFree(jobId)
            AetherRepository.log("OK", "Identity ready id=$identityId summary=${openResult.optJSONObject("summary")}")

            // Determine peer: if cfg.peer empty, we must scan first else direct tunnel
            val tunnelPeer = cfg.peer.trim().ifEmpty { null }
            var chosenPeer: String? = tunnelPeer

            if (chosenPeer == null) {
                // Scan workflow
                AetherRepository.log("INFO", "Starting scan mode=${cfg.scanMode} transport=${cfg.transport}")
                val scanPayloadJob = AetherBridge.scanStart(
                    identityId = identityId,
                    transport = cfg.transport,
                    mode = cfg.scanMode,
                    profile = cfg.noizeProfile.takeIf { it != "off" },
                    ech = cfg.echEnabled.takeIf { it }
                )
                if (scanPayloadJob.isFailure) {
                    val err = scanPayloadJob.exceptionOrNull()?.message ?: "scan_start failed"
                    AetherRepository.onServiceActionFinished(err, AetherState.ERROR)
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                val scanJobObj = scanPayloadJob.getOrNull()!!
                val scanJobId = scanJobObj.optLong("job", -1)
                if (scanJobId == -1L) {
                    AetherRepository.onServiceActionFinished("scan returned no job: $scanJobObj", AetherState.ERROR)
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                AetherRepository.setActiveJob(scanJobId)
                synchronized(this@AetherService) { currentJobId = scanJobId }
                val scanDone = pollJob(scanJobId)
                AetherBridge.jobFree(scanJobId)
                synchronized(this@AetherService) { if (currentJobId == scanJobId) currentJobId = null }
                AetherRepository.setActiveJob(null)

                if (scanDone == null || scanDone.isFailure) {
                    val err = scanDone?.exceptionOrNull()?.message ?: "scan timed out"
                    AetherRepository.onServiceActionFinished("Scan failed: $err", AetherState.ERROR)
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                val scanResult = scanDone.getOrNull()!!
                // endpoint is under "endpoint" key per api.rs scan() → Endpoint
                chosenPeer = scanResult.optString("endpoint", "").ifEmpty { scanResult.optString("peer", "") }
                if (chosenPeer.isEmpty()) {
                    AetherRepository.onServiceActionFinished("Scan returned no endpoint: $scanResult", AetherState.ERROR)
                    cleanupIdentity(identityId)
                    stopForegroundAndSelf()
                    return@launch
                }
                AetherRepository.log("OK", "Scan chose endpoint $chosenPeer")
                AetherRepository.setEndpoint(chosenPeer, cfg.socksBind)
            }

            // Tunnel start
            AetherRepository.log("INFO", "Connecting tunnel to $chosenPeer via ${cfg.transport} socks=${cfg.socksBind}")
            val tunnelRes = AetherBridge.tunnelStart(
                identityId = identityId,
                peer = chosenPeer!!,
                transport = cfg.transport,
                socks = cfg.socksBind,
                http = cfg.httpProxyBind.ifEmpty { null },
                profile = cfg.noizeProfile.takeIf { it != "off" && it != "firewall" } ?: cfg.noizeProfile,
                keepalive = 5,
                ech = cfg.echEnabled.takeIf { it }
            )
            if (tunnelRes.isFailure) {
                val err = tunnelRes.exceptionOrNull()?.message ?: "tunnel_start failed"
                AetherRepository.onServiceActionFinished(err, AetherState.ERROR)
                cleanupIdentity(identityId)
                stopForegroundAndSelf()
                return@launch
            }
            val tunnelJobObj = tunnelRes.getOrNull()!!
            val tunnelJobId = tunnelJobObj.optLong("job", -1)
            if (tunnelJobId == -1L) {
                AetherRepository.onServiceActionFinished("tunnel returned no job: $tunnelJobObj", AetherState.ERROR)
                cleanupIdentity(identityId)
                stopForegroundAndSelf()
                return@launch
            }
            AetherRepository.setActiveJob(tunnelJobId)
            synchronized(this@AetherService) { currentJobId = tunnelJobId }
            AetherRepository.updateState(AetherState.RUNNING)
            // Promote notification to running proxy status
            goForeground("Aether SOCKS5 ${cfg.socksBind} → $chosenPeer [${cfg.transport}]")
            AetherRepository.onServiceActionFinished(null, AetherState.RUNNING)
            AetherRepository.setEndpoint(chosenPeer, cfg.socksBind)
            AetherRepository.log("OK", "Tunnel established socks=${cfg.socksBind} peer=$chosenPeer")

            // Hold the tunnel: poll until it exits (closed or error)
            // This suspends this coroutine; stopAether will cancel job
            var tunnelExit = pollJob(tunnelJobId, timeoutMs = Long.MAX_VALUE) // will wake on cancel or close
            // If we get here, tunnel exited
            AetherBridge.jobFree(tunnelJobId)
            synchronized(this@AetherService) { if (currentJobId == tunnelJobId) currentJobId = null }
            AetherRepository.setActiveJob(null)

            val exitError = tunnelExit?.exceptionOrNull()?.message
            // Distinguish normal cancellation vs failure
            if (exitError != null && exitError.contains("cancelled", true) || exitError == null && tunnelExit?.isFailure == true) {
                // likely cancelled via stopAether -> already handled
            }
            // If still running state, mark stopped
            if (AetherRepository.uiState.value.state == AetherState.RUNNING) {
                AetherRepository.updateState(AetherState.STOPPED)
                AetherRepository.log("INFO", "Tunnel job ended: $tunnelExit")
            }
            cleanupIdentity(identityId)
            stopForegroundAndSelf()
        }
    }

    private fun stopAether() {
        scope.launch {
            AetherRepository.log("INFO", "Stopping Aether...")
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
        synchronized(this@AetherService) { if (currentIdentityId == id) currentIdentityId = null }
        AetherRepository.setIdentity(null)
    }

    private suspend fun pollJob(jobId: Long, timeoutMs: Long = 120_000): com.manjaniq.core.models.Result<JSONObject>? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
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
            delay(250)
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

    companion object {
        const val ACTION_START = "com.manjaniq.action.START_AETHER"
        const val ACTION_STOP = "com.manjaniq.action.STOP_AETHER"
        const val ACTION_RESTART = "com.manjaniq.action.RESTART_AETHER"
        private const val CHANNEL_ID = "aether_engine"
        private const val NOTIFICATION_ID = 40444
    }
}
