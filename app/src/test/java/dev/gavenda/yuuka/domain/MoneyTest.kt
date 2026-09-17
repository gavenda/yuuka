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
}
