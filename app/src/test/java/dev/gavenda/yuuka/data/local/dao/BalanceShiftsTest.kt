package dev.gavenda.yuuka.data.local.dao

import dev.gavenda.yuuka.data.local.Provisional
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceShiftsTest {
    private fun row(id: String, accountId: String, amount: Long, transferId: String? = null) =
        Provisional.transaction(id, accountId, null, null, amount, "2026-11-01", "", "", transferId)

    @Test
    fun `a new row moves its account by its amount`() {
        assertEquals(mapOf("a" to -500L), balanceShifts(emptyList(), listOf(row("t", "a", -500))))
    }

    @Test
    fun `an edit moves by the difference, onto whichever account the row now names`() {
        assertEquals(mapOf("a" to -300L), balanceShifts(listOf(row("t", "a", -500)), listOf(row("t", "a", -800))))
        assertEquals(mapOf("a" to 500L, "b" to -800L), balanceShifts(listOf(row("t", "a", -500)), listOf(row("t", "b", -800))))
    }

    @Test
    fun `an edit that changes no amount moves nothing`() {
        assertEquals(emptyMap<String, Long>(), balanceShifts(listOf(row("t", "a", -500)), listOf(row("t", "a", -500))))
    }

    @Test
    fun `a transfer moves both accounts, and deleting it moves both back`() {
        val legs = listOf(row("f", "a", -2_000, "tfr"), row("t", "b", 2_000, "tfr"))
        assertEquals(mapOf("a" to -2_000L, "b" to 2_000L), balanceShifts(emptyList(), legs))
        assertEquals(mapOf("a" to 2_000L, "b" to -2_000L), balanceShifts(legs, emptyList()))
    }
}
