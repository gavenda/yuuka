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

@Serializable
data class TagsResponse(val tags: List<Tag>)

@Serializable
data class TagResponse(val tag: Tag)

/** `roundUp`, when present, is only ever set on `POST /transactions`'s create response — the destination leg of an auto-generated linked transfer. */
@Serializable
data class TransactionResponse(val transaction: Transaction, val roundUp: Transaction? = null)

@Serializable
data class RoundUpRuleResponse(val roundUpRule: RoundUpRule)

@Serializable
data class TransferResponse(val transferId: String, val transactions: List<Transaction>)

@Serializable
data class BudgetResponse(val budget: Budget)

@Serializable
data class IncomePlanResponse(val incomePlan: IncomePlan)

@Serializable
data class SubscriptionsResponse(val subscriptions: List<Subscription>)

@Serializable
data class SecuritiesResponse(val securities: List<Security>)

@Serializable
data class SecurityResponse(val security: Security)

@Serializable
data class EtfSearchResponse(val results: List<EtfMatch>)

/** `GET /api/fx`: one unit of the source currency in the destination's, and the day it was published for. */
@Serializable
data class ExchangeRate(val rate: Double, val date: String, val source: String)

@Serializable
data class TradesResponse(val trades: List<Trade>)

@Serializable
data class PricesResponse(val prices: List<PricePoint>)
