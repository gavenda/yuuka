package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.local.dao.BudgetDao
import dev.gavenda.yuuka.data.local.dao.IncomePlanDao
import dev.gavenda.yuuka.data.local.dao.SummaryDao
import dev.gavenda.yuuka.data.local.entity.SummaryEntity
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Budget
import dev.gavenda.yuuka.data.model.IncomePlan
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.data.remote.apiJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mirrors the web app's `budget` Pinia store (`src/stores/budget.ts`): the selected month plus what it summarises to. */
class BudgetRepository(
    private val api: YuukaApi,
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

    suspend fun setBudgetAmount(categoryId: String, month: String, amount: Long) {
        apiCall { api.setBudget(buildJsonObject { put("categoryId", categoryId); put("month", month); put("amount", amount) }) }
        refreshBudgets(month)
        refreshSummary(month)
    }

    suspend fun setBudgetPercent(categoryId: String, month: String, percent: Double) {
        apiCall { api.setBudget(buildJsonObject { put("categoryId", categoryId); put("month", month); put("percent", percent) }) }
        refreshBudgets(month)
        refreshSummary(month)
    }

    suspend fun setIncomePlan(month: String, amount: Long, mode: String, grossAmount: Long?) {
        apiCall {
            api.setIncomePlan(
                buildJsonObject {
                    put("month", month)
                    put("amount", amount)
                    put("mode", mode)
                    grossAmount?.let { put("grossAmount", it) }
                },
            )
        }
        refreshIncomePlan(month)
        refreshSummary(month)
    }
}
