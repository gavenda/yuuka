package dev.gavenda.yuuka.data.local.entity

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation

/** A cached transaction with the tags it wears, read live from the `tags` table so a rename shows without refetching it. */
data class TransactionWithTags(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(value = TransactionTagEntity::class, parentColumns = ["transactionId"], entityColumns = ["tagId"]),
    )
    val tags: List<TagEntity>,
)
