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
import com.manjaniq.MainActivity
import com.manjaniq.NativeBridge
import com.manjaniq.core.ConfigStore
import com.manjaniq.core.EngineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the native engine lifecycle so the
 * local proxy keeps running while the user is in other apps.
 *
 * It does not redirect device traffic: apps must be pointed at the
 * local listen address themselves, exactly as with the original
 * RSTA program.
 */
class EngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEngine()
            ACTION_STOP -> stopEngine()
            ACTION_RESTART -> restartEngine()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // If the system destroys the service, do not leave the
        // native listener running unattended in a zombie state.
        try {
            NativeBridge.stopEngine()
        } catch (t: Throwable) {
            // Best effort during teardown.
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startEngine() {
        // Must go foreground promptly after startForegroundService.
        goForeground(getString(R.string.notification_starting))
        scope.launch {
            val config = ConfigStore(this@EngineService).load()
            val problem = config.validate()
            if (problem != null) {
                EngineRepository.onEngineActionFinished(problem)
                stopForegroundAndSelf()
                return@launch
            }
            val result = NativeBridge.startEngine(config)
            if (result.isSuccess) {
                EngineRepository.onEngineActionFinished(null)
                goForeground(
                    getString(
                        R.string.notification_proxy_format,
                        "${config.listenHost}:${config.listenPort}",
                        "${config.connectIp}:${config.connectPort}",
                        config.bypassMethod
                    )
                )
            } else {
                EngineRepository.onEngineActionFinished(
                    result.errorMessage ?: "Engine failed to start"
                )
                stopForegroundAndSelf()
            }
        }
    }

    private fun stopEngine() {
        scope.launch {
            val result = NativeBridge.stopEngine()
            EngineRepository.onEngineActionFinished(
                if (result.isSuccess) null else result.errorMessage
            )
            stopForegroundAndSelf()
        }
    }

    private fun restartEngine() {
        goForeground(getString(R.string.notification_restarting))
        scope.launch {
            NativeBridge.stopEngine()
            val config = ConfigStore(this@EngineService).load()
            val problem = config.validate()
            if (problem != null) {
                EngineRepository.onEngineActionFinished(problem)
                stopForegroundAndSelf()
                return@launch
            }
            val result = NativeBridge.startEngine(config)
            if (result.isSuccess) {
                EngineRepository.onEngineActionFinished(null)
                goForeground(
                    getString(
                        R.string.notification_proxy_format,
                        "${config.listenHost}:${config.listenPort}",
                        "${config.connectIp}:${config.connectPort}",
                        config.bypassMethod
                    )
                )
            } else {
                EngineRepository.onEngineActionFinished(
                    result.errorMessage ?: "Engine failed to restart"
                )
                stopForegroundAndSelf()
            }
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(statusText: String) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this,
            1,
            Intent(this, EngineService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stopAction)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.manjaniq.action.START_ENGINE"
        const val ACTION_STOP = "com.manjaniq.action.STOP_ENGINE"
        const val ACTION_RESTART = "com.manjaniq.action.RESTART_ENGINE"
        private const val CHANNEL_ID = "rsta_engine"
        private const val NOTIFICATION_ID = 40443
    }
}
