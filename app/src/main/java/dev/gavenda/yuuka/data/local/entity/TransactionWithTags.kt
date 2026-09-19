package dev.gavenda.yuuka.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A cached transaction with the tags it wears, read live from the `tags` table so a rename shows without refetching it. */
data class TransactionWithTags(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = TransactionTagEntity::class, parentColumn = "transactionId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)
