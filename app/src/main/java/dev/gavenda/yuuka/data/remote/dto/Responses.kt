package dev.gavenda.yuuka.data.remote.dto

import dev.gavenda.yuuka.data.model.*
import kotlinx.serialization.Serializable

/** Envelopes the API wraps its resources in — mirrors the shapes `src/lib/api.ts` unwraps. */

@Serializable
data class MeResponse(val subject: String, val issuer: String? = null, val expiresAt: Long? = null, val permissions: List<String> = emptyList())

@Serializable
data class PayeesResponse(val payees: List<Payee>)

@Serializable
data class SettingsResponse(val settings: Settings)

@Serializable
data class AccountTypesResponse(val accountTypes: List<AccountType>)

@Serializable
data class AccountTypeResponse(val accountType: AccountType)

@Serializable
data class AccountsResponse(val accounts: List<Account>)

@Serializable
data class AccountResponse(val account: Account)

@Serializable
data class CategoriesResponse(val categories: List<Category>)

@Serializable
data class CategoryResponse(val category: Category)

/** `roundUp`, when present, is only ever set on `POST /transactions`'s create response — the destination leg of an auto-generated linked transfer. */
@Serializable
data class TransactionResponse(val transaction: Transaction, val roundUp: Transaction? = null)

@Serializable
data class RoundUpRuleResponse(val roundUpRule: RoundUpRule)

@Serializable
data class TransferResponse(val transferId: String, val transactions: List<Transaction>)

@Serializable
data class BudgetsResponse(val budgets: List<Budget>)

@Serializable
data class BudgetResponse(val budget: Budget)

@Serializable
data class IncomePlanResponse(val incomePlan: IncomePlan)
