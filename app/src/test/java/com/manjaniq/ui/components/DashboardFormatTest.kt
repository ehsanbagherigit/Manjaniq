package com.manjaniq.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for the statistics formatting shown on the
 * dashboard. Values are formatted deterministically and in
 * a locale-independent way (US English), so the UI never
 * shows a differently-formatted number per device locale.
 */
class DashboardFormatTest {

    @Test
    fun formatBytesUsesBinarySteps() {
        assertEquals("0B", formatBytes(0))
        assertEquals("512B", formatBytes(512))
        assertEquals("1.0KB", formatBytes(1024))
        assertEquals("1.5KB", formatBytes(1536))
        assertEquals("1.00MB", formatBytes(1024 * 1024))
        assertEquals("2.50MB", formatBytes((1024.0 * 1024.0 * 2.5).toLong()))
    }

    @Test
    fun formatDurationRendersHms() {
        assertEquals("—", formatDuration(0))
        assertEquals("—", formatDuration(-5))
        assertEquals("5s", formatDuration(5))
        assertEquals("1m 05s", formatDuration(65))
        assertEquals("2h 03m 04s", formatDuration(2 * 3600 + 3 * 60 + 4))
    }
}
