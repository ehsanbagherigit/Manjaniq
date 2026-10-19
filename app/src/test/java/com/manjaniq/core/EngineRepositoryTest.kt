package com.manjaniq.core

import com.manjaniq.core.models.EngineState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Host-side tests for the repository that connects the UI to
 * the engine. On the host JVM the native library is absent,
 * so these pin the degradation contract: polling must never
 * throw, must report the engine as STOPPED, and must expose
 * no fabricated metrics, logs or connection statistics.
 */
class EngineRepositoryTest {

    @Test
    fun refreshWithoutNativeLibraryNeverThrows() {
        runBlocking {
            // Must complete without an exception even though
            // libmanjaniq_native is not loadable on the JVM.
            EngineRepository.refresh()
        }
    }

    @Test
    fun stateIsStoppedWithoutNativeLibrary() {
        runBlocking { EngineRepository.refresh() }
        assertEquals(EngineState.STOPPED, EngineRepository.uiState.value.state)
    }

    @Test
    fun noFabricatedMetricsOrLogsWithoutNativeLibrary() {
        runBlocking { EngineRepository.refresh() }
        val metrics = EngineRepository.uiState.value.metrics
        assertEquals(0, metrics.activeConns)
        assertEquals(0L, metrics.totalConns)
        assertEquals(0L, metrics.bypassOK)
        assertEquals(0L, metrics.blocked)
        assertEquals(0L, metrics.bytesUp)
        assertEquals(0L, metrics.bytesDown)
        assertEquals(0L, metrics.uptimeSeconds)
        // No log line may be invented when the engine is absent.
        assertEquals(emptyList<EngineLog>(), EngineRepository.logs.value)
    }

    @Test
    fun clearErrorRestartsFromAnErrorState() {
        EngineRepository.onEngineActionFinished("synthetic test error")
        assertNotNull(EngineRepository.uiState.value.lastError)
        EngineRepository.clearError()
        assertNull(EngineRepository.uiState.value.lastError)
        // Leave the shared state clean for other tests.
        EngineRepository.onEngineActionFinished(null)
    }

    @Test
    fun actionFinishedKeepsPreviousErrorWhenNewErrorIsNull() {
        EngineRepository.onEngineActionFinished("first error")
        EngineRepository.onEngineActionFinished(null)
        assertEquals("first error", EngineRepository.uiState.value.lastError)
        EngineRepository.clearError()
        EngineRepository.onEngineActionFinished(null)
    }

    @Test
    fun busyFlagClearsAfterActionFinishes() {
        EngineRepository.onEngineActionFinished(null)
        assertEquals(false, EngineRepository.uiState.value.busy)
    }
}
