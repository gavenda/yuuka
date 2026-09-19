package dev.gavenda.yuuka.data.model

import kotlinx.serialization.Serializable

/** A monthly charge (or credit) the Worker posts as an ordinary transaction at 00:00 UTC. */
@Serializable
data class Subscription(
    val id: String,
    val accountId: String,
    val accountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    /** Signed minor units: negative is an outflow. */
    val amount: Long,
    val payee: String,
    val notes: String = "",
    /** The date the schedule was started from; its day of the month is the anchor. */
    val startOn: String,
    val dayOfMonth: Int,
    /** The next date it posts. A short month clamps the day, so this is not always [dayOfMonth]. */
    val nextRunOn: String,
    val lastRunOn: String? = null,
    /** Paused subscriptions post nothing. */
    val enabled: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
)
