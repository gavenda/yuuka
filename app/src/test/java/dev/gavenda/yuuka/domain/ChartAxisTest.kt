package dev.gavenda.yuuka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChartAxisTest {
    private fun day(amount: Long) = DaySeriesEntry(1, "2026-09-01", amount)

    @Test
    fun `the average leaves out days with nothing spent`() {
        assertEquals(3000L, dailyAverage(listOf(day(2000), day(0), day(4000), day(0))))
    }

    @Test
    fun `there is no average when nothing was spent`() {
        assertNull(dailyAverage(listOf(day(0), day(0))))
        assertNull(dailyAverage(emptyList()))
    }

    @Test
    fun `the axis reaches a round number above the tallest bar`() {
        val scale = axisScale(4599)
        assertEquals(2000L, scale.step)
        assertEquals(6000L, scale.top)
        assertEquals(listOf(0L, 2000L, 4000L, 6000L), scale.ticks)
    }

    @Test
    fun `the axis is never lower than the tallest bar and stays within a few steps`() {
        listOf(1L, 99L, 100L, 4599L, 12_345L, 250_000L, 9_999_999L, 123_456_789L).forEach { max ->
            val scale = axisScale(max)
            assertTrue("top ${scale.top} clears $max", scale.top >= max)
            assertEquals(0L, scale.top % scale.step)
            assertTrue("${scale.ticks.size} ticks for $max", scale.ticks.size in 2..5)
        }
    }

    @Test
    fun `an empty chart still has a usable axis`() {
        val scale = axisScale(0)
        assertTrue(scale.top > 0 && scale.step > 0)
    }

    @Test
    fun `axis labels are compact`() {
        assertEquals("0", compactAmount(0, Locale.US))
        assertEquals("2.5", compactAmount(250, Locale.US))
        assertEquals("15k", compactAmount(1_500_000, Locale.US))
        assertEquals("1.5k", compactAmount(150_000, Locale.US))
        assertEquals("2.3M", compactAmount(230_000_000, Locale.US))
    }
}
