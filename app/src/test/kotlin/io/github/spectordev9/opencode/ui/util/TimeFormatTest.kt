package io.github.spectordev9.opencode.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `formats recent relative times`() {
        assertEquals("Just now", formatRelativeTime(now - 30_000, now))
        assertEquals("5m ago", formatRelativeTime(now - 5 * 60_000, now))
        assertEquals("3h ago", formatRelativeTime(now - 3 * 3_600_000, now))
        assertEquals("2d ago", formatRelativeTime(now - 2 * 86_400_000, now))
    }

    @Test
    fun `formats byte and token counts`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1_024))
        assertEquals("1.5 KB", formatBytes(1_536))
        assertEquals("1.5k", formatTokenCount(1_500))
        assertEquals("1.0M", formatTokenCount(1_000_000))
    }

    @Test
    fun `formats costs`() {
        assertEquals("$0.00", formatCost(0.0))
        assertEquals("<$0.01", formatCost(0.005))
        assertEquals("$1.25", formatCost(1.25))
    }
}
