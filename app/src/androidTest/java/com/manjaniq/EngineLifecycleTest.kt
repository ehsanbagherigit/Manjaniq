package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.EngineState
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Exercises the genuine Go engine through the real JNI bridge on the
 * device: start, observe a real TCP listener, read engine-reported
 * metrics, stop, and repeat rapidly. No part of this is mocked.
 */
@RunWith(AndroidJUnit4::class)
class EngineLifecycleTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun config(port: Int): RSTAConfig = RSTAConfig.DEFAULT.copy(
        listenHost = "127.0.0.1",
        listenPort = port,
        connectIp = "127.0.0.1",
        connectPort = 9, // nothing is dialled until a client connects
        quietMode = false,
        monitorEnabled = true
    )

    private fun canConnect(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1500); true }
    } catch (e: Exception) {
        false
    }

    private fun waitForState(expected: EngineState, timeoutMs: Long = 5_000): EngineState {
        val deadline = System.currentTimeMillis() + timeoutMs
        var state = NativeBridge.getEngineState()
        while (state != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            state = NativeBridge.getEngineState()
        }
        return state
    }

    @Before
    fun ensureStopped() {
        NativeBridge.stopEngine()
        assertEquals(EngineState.STOPPED, waitForState(EngineState.STOPPED))
    }

    @After
    fun cleanup() {
        NativeBridge.stopEngine()
    }

    @Test
    fun nativeLibraryIsLoadedOnDevice() {
        assertEquals("Manjaniq native bridge OK", NativeBridge.testBridge())
    }

    @Test
    fun startBindsRealListenerAndStopReleasesIt() {
        val port = freePort()
        assertFalse("port must be free before start", canConnect(port))

        val start = NativeBridge.startEngine(config(port))
        assertTrue("start failed: ${start.errorMessage}", start.isSuccess)
        assertEquals(EngineState.RUNNING, waitForState(EngineState.RUNNING))
        assertTrue(NativeBridge.isEngineRunning())

        // A genuine TCP listener answers on the configured port.
        assertTrue("engine should accept TCP connections on $port", canConnect(port))

        // Metrics come from the engine, including the bound address.
        val metricsJson = NativeBridge.metricsJson()
        assertNotNull(metricsJson)
        val metrics = JSONObject(metricsJson!!)
        assertTrue(
            "listen_addr should report port $port, got ${metrics.optString("listen_addr")}",
            metrics.optString("listen_addr").endsWith(":$port")
        )
        assertEquals("127.0.0.1:9", metrics.optString("connect_addr"))

        val stop = NativeBridge.stopEngine()
        assertTrue("stop failed: ${stop.errorMessage}", stop.isSuccess)
        assertEquals(EngineState.STOPPED, waitForState(EngineState.STOPPED))
        assertFalse(NativeBridge.isEngineRunning())
        assertFalse("listener must be closed after stop", canConnect(port))
    }

    @Test
    fun rapidStartStopCyclesStayConsistent() {
        repeat(5) { i ->
            val port = freePort()
            val start = NativeBridge.startEngine(config(port))
            assertTrue("cycle $i start failed: ${start.errorMessage}", start.isSuccess)
            assertEquals("cycle $i", EngineState.RUNNING, waitForState(EngineState.RUNNING))
            val stop = NativeBridge.stopEngine()
            assertTrue("cycle $i stop failed: ${stop.errorMessage}", stop.isSuccess)
            assertEquals("cycle $i", EngineState.STOPPED, waitForState(EngineState.STOPPED))
            assertFalse("cycle $i listener still open", canConnect(port))
        }
    }

    @Test
    fun secondStartWhileRunningIsRejectedNotDuplicated() {
        val port = freePort()
        assertTrue(NativeBridge.startEngine(config(port)).isSuccess)
        assertEquals(EngineState.RUNNING, waitForState(EngineState.RUNNING))

        val again = NativeBridge.startEngine(config(freePort()))
        assertFalse("engine must not start twice", again.isSuccess)
        assertEquals(EngineState.RUNNING, NativeBridge.getEngineState())
        assertTrue("original listener must survive the rejected second start", canConnect(port))

        assertTrue(NativeBridge.stopEngine().isSuccess)
        assertEquals(EngineState.STOPPED, waitForState(EngineState.STOPPED))
    }

    @Test
    fun invalidConfigIsRejectedByEngine() {
        val bad = config(freePort()).copy(connectIp = "")
        val result = NativeBridge.startEngine(bad)
        assertFalse("empty CONNECT_IP must be rejected", result.isSuccess)
        assertNotNull(result.errorMessage)
        // The real engine records a failed start as ERROR (never RUNNING)
        // and must not have bound anything.
        assertEquals(EngineState.ERROR, NativeBridge.getEngineState())
        assertFalse(NativeBridge.isEngineRunning())
        assertFalse(canConnect(bad.listenPort))

        // A genuine start must still be possible after a rejected one.
        val port = freePort()
        assertTrue(NativeBridge.startEngine(config(port)).isSuccess)
        assertEquals(EngineState.RUNNING, waitForState(EngineState.RUNNING))
        assertTrue(canConnect(port))
    }

    @Test
    fun stopIsIdempotent() {
        assertTrue(NativeBridge.stopEngine().isSuccess)
        assertTrue(NativeBridge.stopEngine().isSuccess)
        assertEquals(EngineState.STOPPED, NativeBridge.getEngineState())
    }
}
