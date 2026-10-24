package com.manjaniq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side regression for AetherBridge error contract.
 * Host JVM has no libaether.so, so these tests pin failure behaviour:
 * bridge must not report success, must not throw, and must surface load failure.
 */
class AetherBridgeTest {
    @Test
    fun aetherBridgeDoesNotReportSuccessWithoutNativeLibrary() {
        val result = AetherBridge.testBridge()
        // When not loaded, testBridge returns "Native library not loaded..." or JSON error
        assertFalse("should not report native OK without lib: $result", result == "Manjaniq native bridge OK")
        assertTrue("expected failure message, got: $result", result.contains("not loaded", true) || result.contains("Native", true) || result.contains("failed", true))
    }

    @Test
    fun aetherVersionFailsGracefullyWithoutNative() {
        val res = AetherBridge.version()
        // Either failure (expected on host) or success on device with lib
        // On host without native, must be failure
        if (!AetherBridge.isNativeAvailable) {
            assertTrue("version should fail without native", res.isFailure)
            assertNotNull(res.exceptionOrNull()?.message)
        }
    }

    @Test
    fun aetherBridgeDoesNotThrow() {
        // All calls must handle missing native without exception escape
        AetherBridge.testBridge()
        AetherBridge.version()
        AetherBridge.jobPoll(1)
        AetherBridge.jobFree(1)
        AetherBridge.coreStart(listOf("--bind", "127.0.0.1:1819"))
    }
}
