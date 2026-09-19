package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionTag
import kotlin.math.abs

/** A transfer is two linked rows; every list shows them as the single movement they represent. Mirrors `src/lib/transactionRows.ts`. */
sealed interface TransactionRow {
    data class Single(val transaction: Transaction) : TransactionRow

    data class Transfer(
        val id: String,
        val payee: String,
        val fromAccountName: String?,
        val toAccountName: String?,
        val toAccountId: String,
        val categoryName: String?,
        val categoryColor: String?,
        val notes: String,
        val tags: List<TransactionTag>,
        val amount: Long,
        /** The outflow leg — the row that carries the shared id and category. */
        val leg: Transaction,
    ) : TransactionRow
}

/** Pairs up transfer legs within a set of transactions, leaving ordinary transactions untouched. */
fun mergeTransferRows(group: List<Transaction>): List<TransactionRow> {
    val rows = mutableListOf<TransactionRow>()
    val paired = mutableSetOf<String>()

    for (transaction in group) {
        if (transaction.id in paired) continue

        val transferId = transaction.transferId
        if (transferId != null) {
            val other = group.firstOrNull { it.transferId == transferId && it.id != transaction.id }
            if (other != null) {
                paired.add(transaction.id)
                paired.add(other.id)
                val outflow = if (transaction.amount < 0) transaction else other
                val inflow = if (outflow === transaction) other else transaction
                rows.add(
                    TransactionRow.Transfer(
                        id = transferId,
                        payee = transaction.payee,
                        fromAccountName = outflow.accountName,
                        toAccountName = inflow.accountName,
                        toAccountId = inflow.accountId,
                        categoryName = transaction.categoryName,
                        categoryColor = transaction.categoryColor,
                        notes = transaction.notes,
                        tags = outflow.tags,
                        amount = abs(transaction.amount),
                        leg = outflow,
                    ),
                )
                continue
            }
        }

        rows.add(TransactionRow.Single(transaction))
    }

    return rows
}
