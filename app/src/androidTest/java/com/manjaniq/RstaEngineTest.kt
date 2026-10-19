package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.manjaniq.core.models.RSTAConfig
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device native integration: the REAL
 * libmanjaniq_native.so through the JNI bridge.
 *
 * These tests prove the JNI symbol contract
 * (Kotlin external fun <-> Go //export), native
 * initialization, config error propagation, and a
 * real start/stop cycle of the genuine RSTA engine
 * on the emulator — with no external endpoint
 * involved (the engine only binds its listen socket
 * until a client connects).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RstaEngineTest {

    private val config: RSTAConfig
        get() = RSTAConfig.DEFAULT.copy(
            // A port that cannot collide with anything
            // else on the runner.
            listenPort = 41943
        )

    @Before
    fun ensureStopped() {
        NativeBridge.stopEngine()
        NativeBridge.clearLogs()
    }

    @After
    fun tearDown() {
        NativeBridge.stopEngine()
        NativeBridge.cleanup()
    }

    @Test
    fun nativeLibraryLoadsAndJniSymbolsResolve() {
        // The regression guard for "built only bridge.go":
        // if the C wrappers were dropped from the .so,
        // dlopen or the first call fails here.
        assertEquals(
            "Manjaniq native bridge OK",
            NativeBridge.testBridge()
        )
        assertEquals(
            "Manjaniq native bridge OK",
            NativeBridge.GoHelloWorld()
        )
    }

    @Test
    fun invalidConfigIsRejectedNotFaked() {
        val broken = config.copy(listenPort = 0)
        val result = NativeBridge.startEngine(broken)
        assertFalse("invalid config must not start", result.isSuccess)
        assertNotNull(result.errorMessage)
        assertFalse(
            "engine must not run after a rejected config",
            NativeBridge.isEngineRunning()
        )
    }

    @Test
    fun validConfigStartsAndStopStops() {
        val start = NativeBridge.startEngine(config)
        assertTrue(
            "engine start failed: ${start.errorMessage}",
            start.isSuccess
        )
        assertTrue(NativeBridge.isEngineRunning())
        assertEquals(
            com.manjaniq.core.models.EngineState.RUNNING,
            NativeBridge.getEngineState()
        )

        val metrics = NativeBridge.metricsJson()
        assertNotNull(metrics)
        val json = JSONObject(metrics!!)
        assertEquals("RUNNING", json.getString("state"))
        assertTrue("listen_addr must be reported",
            json.getString("listen_addr").isNotEmpty())

        val stop = NativeBridge.stopEngine()
        assertTrue("engine stop failed: ${stop.errorMessage}", stop.isSuccess)
        assertFalse(NativeBridge.isEngineRunning())
        assertEquals(
            com.manjaniq.core.models.EngineState.STOPPED,
            NativeBridge.getEngineState()
        )
    }

    @Test
    fun doubleStartIsRejectedByIdempotentServiceLayer() {
        assertTrue(NativeBridge.startEngine(config).isSuccess)
        // The native side rejects the second start; the
        // service layer turns that into an idempotent
        // success (see EngineService). Here we pin the
        // native contract itself: a second start is an
        // error, never a silent fake success.
        val second = NativeBridge.startEngine(config)
        assertFalse(second.isSuccess)
        // And the engine is still running from the
        // first start — never stopped by the rejected one.
        assertTrue(NativeBridge.isEngineRunning())
    }

    @Test
    fun metricsAndLogsJsonAreWellFormed() {
        assertTrue(NativeBridge.startEngine(config).isSuccess)
        val metrics = NativeBridge.metricsJson()
        assertNotNull(metrics)
        JSONObject(metrics!!) // must parse

        val logs = NativeBridge.logsJson()
        assertNotNull("logs must be a JSON array", logs)
        assertTrue("logs must be a JSON array",
            logs!!.trim().startsWith("["))
    }

    @Test
    fun stopWhenNotRunningIsIdempotent() {
        val stop = NativeBridge.stopEngine()
        assertTrue(stop.isSuccess)
        assertFalse(NativeBridge.isEngineRunning())
    }

    @Test
    fun logsClearClearsTheRing() {
        assertTrue(NativeBridge.startEngine(config).isSuccess)
        val before = NativeBridge.logsJson()
        assertNotNull(before)
        NativeBridge.clearLogs()
        val after = NativeBridge.logsJson()
        assertEquals("[]", after)
    }
}
