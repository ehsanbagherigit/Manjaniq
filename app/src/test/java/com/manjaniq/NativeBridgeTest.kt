package com.manjaniq

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side regression tests for the native bridge error contract.
 *
 * The host JVM has no `libmanjaniq_native`, so these tests pin down the
 * failure behaviour: the bridge must never report success, must not throw,
 * and must surface the underlying library-load failure instead of hiding it.
 */
class NativeBridgeTest {

    @Test
    fun testBridgeDoesNotReportSuccessWithoutNativeLibrary() {
        val result = NativeBridge.testBridge()
        assertNotEquals("Manjaniq native bridge OK", result)
        assertTrue("expected failure message, got: $result", result.startsWith("Native call failed"))
    }

    @Test
    fun testBridgeSurfacesLibraryLoadFailure() {
        val result = NativeBridge.testBridge()
        assertTrue(
            "expected captured library-load failure in: $result",
            result.contains("library load failed")
        )
    }

    @Test
    fun testBridgeDoesNotThrow() {
        NativeBridge.testBridge()
    }
}
