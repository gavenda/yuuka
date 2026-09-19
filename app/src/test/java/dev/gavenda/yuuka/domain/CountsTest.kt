package dev.gavenda.yuuka.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CountsTest {
    @Test
    fun `leaves anything under a thousand as it is`() {
        assertEquals(listOf("0", "1", "12", "999"), listOf(0L, 1, 12, 999).map(::formatCount))
    }

    @Test
    fun `shortens thousands, millions and billions with k, m and b`() {
        assertEquals("1k", formatCount(1_000))
        assertEquals("12k", formatCount(12_000))
        assertEquals("1m", formatCount(1_000_000))
        assertEquals("3b", formatCount(3_000_000_000))
    }

    @Test
    fun `keeps one decimal and drops a trailing zero`() {
        assertEquals("1.2k", formatCount(1_200))
        assertEquals("1.3k", formatCount(1_300))
        assertEquals("1k", formatCount(1_050))
        assertEquals("3.4m", formatCount(3_400_000))
        assertEquals("2.5b", formatCount(2_500_000_000))
    }

    @Test
    fun `truncates instead of rounding up, so it never overstates`() {
        assertEquals("1.9k", formatCount(1_999))
        assertEquals("999.9k", formatCount(999_999))
        assertEquals("999.9m", formatCount(999_999_999))
    }

    @Test
    fun `reads a negative count as zero`() {
        assertEquals("0", formatCount(-5))
    }
}
