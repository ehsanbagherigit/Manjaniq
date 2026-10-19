package com.manjaniq

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.manjaniq.service.EngineService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android service lifecycle for the RSTA engine,
 * against the genuine native engine (no mocks):
 *  - start brings the service to the foreground with a
 *    status notification and starts the engine,
 *  - repeated start requests are idempotent successes,
 *  - stop halts the engine and removes the notification,
 *  - the engine state the UI polls matches the service.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ServiceLifecycleTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun launchAppAndStopEngine() {
        // The service must start while the app is in the
        // foreground (Android background-FGS limits).
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        NativeBridge.stopEngine()
    }

    @After
    fun tearDown() {
        context.stopService(
            Intent(context, EngineService::class.java)
                .setAction(EngineService.ACTION_STOP)
        )
        NativeBridge.stopEngine()
        NativeBridge.cleanup()
    }

    @Test
    fun startForegroundServiceRunsEngineWithNotification() {
        startService(EngineService.ACTION_START)

        // The engine comes up (real native start).
        assertTrue("engine must run after service start",
            waitForEngine(true, 15_000))
        assertEquals(
            com.manjaniq.core.models.EngineState.RUNNING,
            NativeBridge.getEngineState()
        )

        // The service must be running in the foreground.
        assertTrue("EngineService must be running",
            waitForServiceRunning(10_000))
        val notification = activeNotification()
        assertNotNull(
            "a foreground status notification must be posted",
            notification
        )
    }

    @Test
    fun repeatedStartIsIdempotentAndKeepsEngineRunning() {
        startService(EngineService.ACTION_START)
        assertTrue(waitForEngine(true, 15_000))

        // A second start request must not stop the
        // engine, must not surface an error, and must
        // keep the notification.
        startService(EngineService.ACTION_START)
        Thread.sleep(1500)
        assertTrue("engine must still run after a repeated start",
            NativeBridge.isEngineRunning())
        assertNotNull("notification must survive a repeated start",
            activeNotification())
        assertEquals(
            com.manjaniq.core.models.EngineState.RUNNING,
            NativeBridge.getEngineState()
        )
    }

    @Test
    fun stopServiceStopsEngineAndRemovesNotification() {
        startService(EngineService.ACTION_START)
        assertTrue(waitForEngine(true, 15_000))

        startService(EngineService.ACTION_STOP)
        assertTrue("engine must stop after ACTION_STOP",
            waitForEngine(false, 15_000))
        assertEquals(
            com.manjaniq.core.models.EngineState.STOPPED,
            NativeBridge.getEngineState()
        )
        // The foreground notification is removed with the
        // service (STOP_FOREGROUND_REMOVE).
        Thread.sleep(1500)
        assertFalse("notification must be removed after stop",
            hasActiveNotification())
    }

    private fun startService(action: String) {
        val intent = Intent(context, EngineService::class.java)
            .setAction(action)
        androidx.core.content.ContextCompat.startForegroundService(
            context, intent
        )
    }

    private fun waitForEngine(running: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (NativeBridge.isEngineRunning() == running) return true
            Thread.sleep(200)
        }
        return NativeBridge.isEngineRunning() == running
    }

    private fun waitForServiceRunning(timeoutMs: Long): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            @Suppress("DEPRECATION")
            val services = am.getRunningServices(10)
            if (services.any { it.service.className == EngineService::class.java.name }) {
                return true
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun activeNotification(): android.app.Notification? =
        (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager)
            .activeNotifications
            .firstOrNull { it.id == EngineService.NOTIFICATION_ID }
            ?.notification

    private fun hasActiveNotification(): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager)
            .activeNotifications
            .any { it.id == EngineService.NOTIFICATION_ID }
}
