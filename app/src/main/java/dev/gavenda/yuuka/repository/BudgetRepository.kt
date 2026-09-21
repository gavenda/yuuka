package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.Ids
import dev.gavenda.yuuka.data.local.Provisional
import dev.gavenda.yuuka.data.local.dao.BudgetDao
import dev.gavenda.yuuka.data.local.dao.IncomePlanDao
import dev.gavenda.yuuka.data.local.dao.SummaryDao
import dev.gavenda.yuuka.data.local.entity.BudgetEntity
import dev.gavenda.yuuka.data.local.entity.IncomePlanEntity
import dev.gavenda.yuuka.data.local.entity.SummaryEntity
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Budget
import dev.gavenda.yuuka.data.model.IncomePlan
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.data.remote.apiJson
import dev.gavenda.yuuka.sync.Outbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mirrors the web app's `budget` Pinia store (`src/stores/budget.ts`): the
 * selected month plus what it summarises to.
 *
 * A plan set here shows at once and is sent when there is a connection. The
 * month's summary is not recomputed locally — it depends on every transaction
 * in the month and on which of them a category's children carry, which is the
 * server's arithmetic, not a guess worth making. The figure the user just typed
 * appears in the plan straight away; what it is measured against catches up
 * when the batch lands.
 */
class BudgetRepository(
    private val api: YuukaApi,
    private val outbox: Outbox,
    private val budgetDao: BudgetDao,
    private val incomePlanDao: IncomePlanDao,
    private val summaryDao: SummaryDao,
) {
    fun observeSummary(month: String): Flow<Summary?> =
        summaryDao.observe(month).map { entity -> entity?.let { apiJson.decodeFromString(Summary.serializer(), it.json) } }

    fun observeBudgets(month: String): Flow<List<Budget>> = budgetDao.observeForMonth(month).map { list -> list.map { it.toDomain() } }

    fun observeIncomePlan(month: String): Flow<IncomePlan?> = incomePlanDao.observe(month).map { it?.toDomain() }

    suspend fun refreshSummary(month: String): Summary {
        val summary = apiCall { api.summary(month) }
        summaryDao.upsert(SummaryEntity(month, apiJson.encodeToString(Summary.serializer(), summary), System.currentTimeMillis()))
        return summary
    }

    suspend fun refreshBudgets(month: String) {
        val response = apiCall { api.listBudgets(month) }
        budgetDao.replaceMonth(month, response.budgets.map { it.toEntity(month) })
    }

    suspend fun refreshIncomePlan(month: String) {
        val response = apiCall { api.getIncomePlan(month) }
        incomePlanDao.upsert(response.incomePlan.toEntity(month))
    }

    suspend fun setBudgetAmount(categoryId: String, month: String, amount: Long) = setBudget(categoryId, month, amount = amount, percent = null)

    suspend fun setBudgetPercent(categoryId: String, month: String, percent: Double) = setBudget(categoryId, month, amount = 0, percent = percent)

    /**
     * An upsert: a category carries one plan per queried month, so setting it
     * again replaces whatever was there. The id is only used when there is no
     * plan yet, which is why a queued create and a queued change are one call.
     */
    private suspend fun setBudget(categoryId: String, month: String, amount: Long, percent: Double?) {
        val existing = budgetDao.forCategory(month, categoryId)
        val id = existing?.id ?: Ids.new(Ids.BUDGET)

        budgetDao.upsert(
            BudgetEntity(
                id = id,
                categoryId = categoryId,
                month = existing?.month ?: month,
                amount = amount,
                percent = percent,
                queriedMonth = month,
                createdAt = existing?.createdAt ?: Provisional.touchedAt(),
                updatedAt = Provisional.touchedAt(),
            ),
        )

        val body = buildJsonObject {
            put("id", id)
            put("categoryId", categoryId)
            put("month", month)
            if (percent == null) put("amount", amount) else put("percent", percent)
        }
        outbox.enqueue("PUT", "/api/budgets", body)
    }

    suspend fun setIncomePlan(month: String, amount: Long, mode: String, grossAmount: Long?) {
        incomePlanDao.upsert(
            IncomePlanEntity(
                queriedMonth = month,
                month = month,
                amount = amount,
                mode = mode,
                grossAmount = grossAmount,
                createdAt = Provisional.touchedAt(),
                updatedAt = Provisional.touchedAt(),
            ),
        )

        val body = buildJsonObject {
            put("month", month)
            put("amount", amount)
            put("mode", mode)
            grossAmount?.let { put("grossAmount", it) }
        }
        outbox.enqueue("PUT", "/api/income-plan", body)
    }
}
