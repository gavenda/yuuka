package dev.gavenda.yuuka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `parses a plain decimal amount`() {
        assertEquals(4599L, parseMoney("45.99"))
    }

    @Test
    fun `parses a negative amount`() {
        assertEquals(-4599L, parseMoney("-45.99"))
    }

    @Test
    fun `rejects a non-numeric string`() {
        assertNull(parseMoney("not a number"))
    }

    @Test
    fun `rejects an amount too large to represent, rather than crashing`() {
        assertNull(parseMoney("9".repeat(30)))
    }

    @Test
    fun `rejects an amount that overflows only once multiplied into minor units`() {
        // Below Long.MAX_VALUE (9223372036854775807), but *100 overflows.
        assertNull(parseMoney("92233720368547759"))
    }

    @Test
    fun `converts at a rate to whole minor units`() {
        assertEquals(10_000L, convertAtRate(580_000L, 0.017241))
        assertEquals(5813L, convertAtRate(100L, 58.125))
    }

    @Test
    fun `works the rate out of two amounts`() {
        assertEquals(0.0172414, rateBetween(580_000L, 10_000L)!!, 1e-7)
        assertNull(rateBetween(0L, 10_000L))
    }

    @Test
    fun `writes a rate in six significant figures, as the web does`() {
        assertEquals("0.0172414", formatRate(0.017241379))
        assertEquals("58", formatRate(58.0))
        assertEquals("1.5", formatRate(1.5))
        assertEquals("0.000000123457", formatRate(0.000000123456789))
    }

    @Test
    fun `reads only a positive number as a rate`() {
        assertEquals(0.0172, parseRate(" 0.0172 ")!!, 0.0)
        assertEquals(1000.5, parseRate("1,000.5")!!, 0.0)
        assertNull(parseRate("0"))
        assertNull(parseRate("-1"))
        assertNull(parseRate("abc"))
    }
}
