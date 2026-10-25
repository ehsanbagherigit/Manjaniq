package com.manjaniq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.manjaniq.MainActivity
import com.manjaniq.R
import com.manjaniq.core.AetherCore
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherSettingsStorage
import com.manjaniq.core.EngineRepository
import com.manjaniq.core.models.EngineState
import com.manjaniq.ui.server.AetherLogEntry
import com.manjaniq.ui.server.AetherLogText
import com.manjaniq.ui.server.ServerAetherViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class AetherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AetherSettingsStorage.init(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                promoteForeground(getString(R.string.aether_notification_starting))
                startTunnel()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startTunnel() {
        startJob?.cancel()
        _lastError.value = null
        _state.value = EngineState.STARTING

        startJob = scope.launch {
            if (EngineRepository.uiState.value.state != EngineState.STOPPED) {
                EngineRepository.requestStop(applicationContext)
            }

            val profile = AetherSettingsStorage.getActiveProfile(applicationContext)
            val core = AetherCore.of(profile)
            val refusal = AetherCoreManager.refusalOf(
                arguments = core.arguments,
                hasTorTransports = AetherCoreManager.hasTorTransports(applicationContext),
                hasPsiphon = AetherCoreManager.hasPsiphon(applicationContext),
            )
            if (refusal != null) {
                val msg = getString(refusal)
                appendLine(Log.ERROR, msg)
                _lastError.value = msg
                _state.value = EngineState.ERROR
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val invalidPort = AetherCoreManager.invalidPort(core.ports)
            if (invalidPort != null) {
                val msg = getString(R.string.aether_listen_port_invalid, invalidPort.toString())
                appendLine(Log.ERROR, msg)
                _lastError.value = msg
                _state.value = EngineState.ERROR
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val busyPort = AetherCoreManager.firstPortInUse(applicationContext, core.ports)
            if (busyPort != null) {
                val msg = getString(R.string.aether_listen_port_in_use, busyPort)
                appendLine(Log.ERROR, msg)
                _lastError.value = msg
                _state.value = EngineState.ERROR
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val listenAddr = "${AetherCoreManager.LOOPBACK}:${core.port}"
            _listenAddress.value = listenAddr

            val started = AetherCoreManager.start(
                context = applicationContext,
                core = core,
                onOutput = { raw ->
                    val parsed = ServerAetherViewModel.parseCoreLine(raw)
                    if (parsed != null) {
                        appendLine(parsed.first, parsed.second)
                    }
                },
                onUnexpectedExit = {
                    if (_state.value == EngineState.RUNNING || _state.value == EngineState.STARTING) {
                        val stoppedMsg = AetherCoreManager.stoppedMessage(applicationContext)
                        appendLine(Log.ERROR, stoppedMsg)
                        _lastError.value = stoppedMsg
                        _state.value = EngineState.ERROR
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                },
            )

            if (started) {
                _state.value = EngineState.RUNNING
                val pathLabel = AetherCoreManager.pathOf(core.arguments).joinToString(" -> ")
                updateNotification(getString(R.string.aether_notification_running, listenAddr, pathLabel))
            } else {
                val stoppedMsg = AetherCoreManager.stoppedMessage(applicationContext)
                appendLine(Log.ERROR, stoppedMsg)
                _lastError.value = stoppedMsg
                _state.value = EngineState.ERROR
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopTunnel() {
        startJob?.cancel()
        startJob = null
        _state.value = EngineState.STOPPING
        AetherCoreManager.stop(applicationContext)
        _state.value = EngineState.STOPPED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        startJob?.cancel()
        AetherCoreManager.stop(applicationContext)
        if (_state.value != EngineState.ERROR) {
            _state.value = EngineState.STOPPED
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.aether_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.aether_notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aether_logo)
            .setContentTitle(getString(R.string.aether_notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun promoteForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.manjaniq.action.AETHER_START"
        const val ACTION_STOP = "com.manjaniq.action.AETHER_STOP"
        private const val CHANNEL_ID = "manjaniq_aether_channel"
        private const val NOTIFICATION_ID = 2002

        private val _state = MutableStateFlow(EngineState.STOPPED)
        val state: StateFlow<EngineState> = _state.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        private val _listenAddress = MutableStateFlow("${AetherCoreManager.LOOPBACK}:${AetherCoreManager.DEFAULT_SOCKS_PORT}")
        val listenAddress: StateFlow<String> = _listenAddress.asStateFlow()

        private val _liveLogs = MutableStateFlow<List<AetherLogEntry>>(emptyList())
        val liveLogs: StateFlow<List<AetherLogEntry>> = _liveLogs.asStateFlow()

        private val nextLogId = AtomicLong(10_000L)

        fun requestStart(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, AetherService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(app, intent)
        }

        fun requestStop(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, AetherService::class.java).apply { action = ACTION_STOP }
            app.startService(intent)
        }

        fun clearError() {
            _lastError.value = null
            if (_state.value == EngineState.ERROR) {
                _state.value = EngineState.STOPPED
            }
        }

        fun clearLogs() {
            _liveLogs.value = emptyList()
        }

        internal fun appendLine(priority: Int, text: String) {
            val entry = AetherLogEntry(
                id = nextLogId.getAndIncrement(),
                priority = priority,
                text = AetherLogText.Raw(text),
            )
            _liveLogs.update { current ->
                if (current.size >= ServerAetherViewModel.LOG_CAPACITY) {
                    current.drop(current.size - ServerAetherViewModel.LOG_CAPACITY + 1) + entry
                } else {
                    current + entry
                }
            }
        }
    }
}
