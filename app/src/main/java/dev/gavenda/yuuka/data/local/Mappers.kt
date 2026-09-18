package dev.gavenda.yuuka.data.local

import dev.gavenda.yuuka.data.local.entity.AccountEntity
import dev.gavenda.yuuka.data.local.entity.AccountTypeEntity
import dev.gavenda.yuuka.data.local.entity.BudgetEntity
import dev.gavenda.yuuka.data.local.entity.CategoryEntity
import dev.gavenda.yuuka.data.local.entity.IncomePlanEntity
import dev.gavenda.yuuka.data.local.entity.PayeeEntity
import dev.gavenda.yuuka.data.local.entity.RoundUpRuleEntity
import dev.gavenda.yuuka.data.local.entity.SettingsEntity
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.model.Budget
import dev.gavenda.yuuka.data.model.BudgetMode
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.data.model.IncomePlan
import dev.gavenda.yuuka.data.model.IncomePlanMode
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.PayeeKind
import dev.gavenda.yuuka.data.model.RoundUpRule
import dev.gavenda.yuuka.data.model.Settings

/** Entity <-> domain mappers. Enum fields are stored in Room as their raw (wire-format) name. */

fun AccountTypeEntity.toDomain() = AccountType(id, name, sortOrder, archived, accountCount, createdAt, updatedAt)

fun AccountType.toEntity() = AccountTypeEntity(id, name, sortOrder, archived, accountCount, createdAt, updatedAt)

fun AccountEntity.toDomain() =
    Account(id, name, typeId, typeName, currency, logoUrl, logoInvertDark, roundUpSource, startingBalance, balance, archived, createdAt, updatedAt)

fun Account.toEntity() =
    AccountEntity(id, name, typeId, typeName, currency, logoUrl, logoInvertDark, roundUpSource, startingBalance, balance, archived, createdAt, updatedAt)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    kind = enumValueOf<CategoryKind>(kind),
    color = color,
    sortOrder = sortOrder,
    archived = archived,
    parentId = parentId,
    appliesTo = enumValueOf<CategoryScope>(appliesTo),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Category.toEntity() = CategoryEntity(id, name, kind.name, color, sortOrder, archived, parentId, appliesTo.name, createdAt, updatedAt)

fun SettingsEntity.toDomain() = Settings(displayCurrency, enumValueOf<BudgetMode>(budgetMode), defaultAccountId, createdAt, updatedAt)

fun Settings.toEntity() = SettingsEntity(0, displayCurrency, budgetMode.name, defaultAccountId, createdAt, updatedAt)

fun RoundUpRuleEntity.toDomain() = RoundUpRule(enabled, roundTo, destinationAccountId, createdAt, updatedAt)

fun RoundUpRule.toEntity() = RoundUpRuleEntity(0, enabled, roundTo, destinationAccountId, createdAt, updatedAt)

fun BudgetEntity.toDomain() = Budget(id, categoryId, month, amount, percent, createdAt, updatedAt)

fun Budget.toEntity(queriedMonth: String) = BudgetEntity(id, categoryId, month, amount, percent, queriedMonth, createdAt, updatedAt)

fun IncomePlanEntity.toDomain() = IncomePlan(month, amount, enumValueOf<IncomePlanMode>(mode), grossAmount, createdAt, updatedAt)

fun IncomePlan.toEntity(queriedMonth: String) = IncomePlanEntity(queriedMonth, month, amount, mode.name, grossAmount, createdAt, updatedAt)

fun PayeeEntity.toDomain() = Payee(
    id = id,
    payee = payee,
    kind = enumValueOf<PayeeKind>(kind),
    accountId = accountId,
    accountName = accountName,
    toAccountId = toAccountId,
    toAccountName = toAccountName,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryColor = categoryColor,
    notes = notes,
    usedCount = usedCount,
    lastUsedAt = lastUsedAt,
)

fun Payee.toEntity() = PayeeEntity(
    id = id,
    payee = payee,
    kind = kind.name,
    accountId = accountId,
    accountName = accountName,
    toAccountId = toAccountId,
    toAccountName = toAccountName,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryColor = categoryColor,
    notes = notes,
    usedCount = usedCount,
    lastUsedAt = lastUsedAt,
)

fun dev.gavenda.yuuka.data.model.Transaction.toEntity() = dev.gavenda.yuuka.data.local.entity.TransactionEntity(
    id = id,
    accountId = accountId,
    accountName = accountName,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryColor = categoryColor,
    amount = amount,
    occurredOn = occurredOn,
    payee = payee,
    notes = notes,
    transferId = transferId,
    runningBalance = runningBalance,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun dev.gavenda.yuuka.data.local.entity.TransactionEntity.toDomain() = dev.gavenda.yuuka.data.model.Transaction(
    id = id,
    accountId = accountId,
    accountName = accountName,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryColor = categoryColor,
    amount = amount,
    occurredOn = occurredOn,
    payee = payee,
    notes = notes,
    transferId = transferId,
    runningBalance = runningBalance,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
