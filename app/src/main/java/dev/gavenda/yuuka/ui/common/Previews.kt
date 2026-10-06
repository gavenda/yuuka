package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.DailySpend
import dev.gavenda.yuuka.data.model.IncomePlanMode
import dev.gavenda.yuuka.data.model.SubcategoryBreakdown
import dev.gavenda.yuuka.data.model.Subscription
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionTag
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.ThemePreference
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.mergeTransferRows
import dev.gavenda.yuuka.ui.theme.YuukaTheme
import org.koin.compose.KoinApplicationPreview
import org.koin.dsl.module

/**
 * What a screen needs around it to be drawn in an IDE preview: the theme, the snackbar host the app shell
 * normally provides, and the two local preferences a screen reads through Koin. Nothing here reaches the
 * network or a database — a previewed screen is handed its state directly.
 */
@Composable
fun ScreenPreview(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    KoinApplicationPreview(
        application = {
            modules(
                module {
                    single { AmountVisibility(context) }
                    single { ThemePreference(context) }
                },
            )
        },
    ) {
        YuukaTheme(darkTheme = darkTheme) {
            CompositionLocalProvider(LocalSnackbarHostState provides remember { SnackbarHostState() }) {
                Surface(modifier = Modifier.fillMaxSize(), content = content)
            }
        }
    }
}

/** A small, made-up ledger for previews: enough of everything for each screen to have something to draw. */
object PreviewData {
    const val MONTH = "2026-10"

    val accountTypes = listOf(
        AccountType(id = "typ_checking", name = "Checking", sortOrder = 0, archived = false, accountCount = 1),
        AccountType(id = "typ_savings", name = "Savings", sortOrder = 1, archived = false, accountCount = 1),
        AccountType(id = "typ_cash", name = "Cash", sortOrder = 2, archived = false, accountCount = 1),
        AccountType(id = "typ_credit", name = "Credit Card", sortOrder = 3, archived = false, accountCount = 1),
        AccountType(id = "typ_loan", name = "Loan", sortOrder = 4, archived = true, accountCount = 0),
    )

    val accounts = listOf(
        Account(id = "acc_checking", name = "Everyday", typeId = "typ_checking", typeName = "Checking", currency = "PHP", roundUpSource = true, startingBalance = 5_000_000, balance = 8_245_050, archived = false),
        Account(id = "acc_savings", name = "Rainy Day", typeId = "typ_savings", typeName = "Savings", currency = "PHP", startingBalance = 20_000_000, balance = 24_150_000, archived = false),
        Account(id = "acc_cash", name = "Wallet", typeId = "typ_cash", typeName = "Cash", currency = "PHP", startingBalance = 200_000, balance = 135_000, archived = false),
        Account(id = "acc_credit", name = "Travel Card", typeId = "typ_credit", typeName = "Credit Card", currency = "PHP", startingBalance = 0, balance = -1_289_900, archived = false),
    )

    val categories = listOf(
        Category(id = "cat_salary", name = "Salary", kind = CategoryKind.income, color = "#1baf7a", sortOrder = 0, archived = false),
        Category(id = "cat_food", name = "Food", kind = CategoryKind.expense, color = "#eb6834", sortOrder = 1, archived = false),
        Category(id = "cat_groceries", name = "Groceries", kind = CategoryKind.expense, color = "#eb6834", sortOrder = 2, archived = false, parentId = "cat_food"),
        Category(id = "cat_dining", name = "Dining out", kind = CategoryKind.expense, color = "#d95926", sortOrder = 3, archived = false, parentId = "cat_food"),
        Category(id = "cat_transport", name = "Transport", kind = CategoryKind.expense, color = "#2a78d6", sortOrder = 4, archived = false),
        Category(id = "cat_bills", name = "Bills", kind = CategoryKind.expense, color = "#64748b", sortOrder = 5, archived = false),
        Category(id = "cat_cashflow", name = "Cashflow", kind = CategoryKind.transfer, color = "#3987e5", sortOrder = 6, archived = false),
        Category(id = "cat_investments", name = "Investments", kind = CategoryKind.transfer, color = "#3987e5", sortOrder = 7, archived = false, parentId = "cat_cashflow"),
    )

    val tags = listOf(
        Tag(id = "tag_work", name = "Work", color = "#2a78d6", transactionCount = 12),
        Tag(id = "tag_family", name = "Family", color = "#1baf7a", transactionCount = 7),
        Tag(id = "tag_trip", name = "Cebu trip", color = "#eb6834", transactionCount = 3),
    )

    val transactions = listOf(
        Transaction(
            id = "txn_1", accountId = "acc_checking", accountName = "Everyday", categoryId = "cat_groceries", categoryName = "Groceries",
            categoryColor = "#eb6834", amount = -245_050, occurredOn = "$MONTH-06T18:20", payee = "Landers", notes = "Weekly shop",
            tags = listOf(TransactionTag("tag_family", "Family", "#1baf7a")), runningBalance = 8_245_050,
        ),
        Transaction(
            id = "txn_2", accountId = "acc_checking", accountName = "Everyday", categoryId = "cat_investments", categoryName = "Investments",
            categoryColor = "#3987e5", amount = -1_000_000, occurredOn = "$MONTH-06T09:00", payee = "Everyday → Rainy Day", transferId = "trf_1",
            runningBalance = 8_490_100,
        ),
        Transaction(
            id = "txn_3", accountId = "acc_savings", accountName = "Rainy Day", categoryId = "cat_investments", categoryName = "Investments",
            categoryColor = "#3987e5", amount = 1_000_000, occurredOn = "$MONTH-06T09:00", payee = "Everyday → Rainy Day", transferId = "trf_1",
            runningBalance = 24_150_000,
        ),
        Transaction(
            id = "txn_4", accountId = "acc_credit", accountName = "Travel Card", categoryId = "cat_bills", categoryName = "Bills",
            categoryColor = "#64748b", amount = -54_900, occurredOn = "$MONTH-05", payee = "Netflix", automated = true, runningBalance = -1_289_900,
        ),
        Transaction(
            id = "txn_5", accountId = "acc_checking", accountName = "Everyday", categoryId = "cat_salary", categoryName = "Salary",
            categoryColor = "#1baf7a", amount = 6_500_000, occurredOn = "$MONTH-05T08:00", payee = "Acme Corp",
            tags = listOf(TransactionTag("tag_work", "Work", "#2a78d6")), runningBalance = 9_490_100,
        ),
    )

    /** [transactions] as the list draws them: a transfer's two legs folded into one row. */
    val rows: List<TransactionRow> = mergeTransferRows(transactions)

    val subscriptions = listOf(
        Subscription(
            id = "sub_1", accountId = "acc_credit", accountName = "Travel Card", categoryId = "cat_bills", categoryName = "Bills",
            categoryColor = "#64748b", amount = -54_900, payee = "Netflix", startOn = "2026-01-05", dayOfMonth = 5, nextRunOn = "2026-11-05",
            lastRunOn = "2026-10-05",
        ),
        Subscription(
            id = "sub_2", accountId = "acc_checking", accountName = "Everyday", categoryId = "cat_bills", categoryName = "Bills",
            categoryColor = "#64748b", amount = -1_800_000, payee = "Rent", notes = "Unit 12B", startOn = "2026-01-31", dayOfMonth = 31,
            nextRunOn = "2026-10-31", lastRunOn = "2026-09-30",
        ),
        Subscription(
            id = "sub_3", accountId = "acc_checking", accountName = "Everyday", amount = -29_900, payee = "Gym", startOn = "2026-03-15",
            dayOfMonth = 15, nextRunOn = "2026-11-15", enabled = false,
        ),
    )

    private val breakdown = listOf(
        CategoryBreakdown(categoryId = "cat_salary", name = "Salary", kind = CategoryKind.income, color = "#1baf7a", planned = 0, actual = 6_500_000, remaining = 0),
        CategoryBreakdown(
            categoryId = "cat_food", name = "Food", kind = CategoryKind.expense, color = "#eb6834", planned = 1_500_000, actual = 1_120_000, remaining = 380_000,
            children = listOf(
                SubcategoryBreakdown("cat_groceries", "Groceries", "#eb6834", 780_000),
                SubcategoryBreakdown("cat_dining", "Dining out", "#d95926", 340_000),
            ),
        ),
        CategoryBreakdown(categoryId = "cat_transport", name = "Transport", kind = CategoryKind.expense, color = "#2a78d6", planned = 400_000, actual = 365_000, remaining = 35_000),
        CategoryBreakdown(categoryId = "cat_bills", name = "Bills", kind = CategoryKind.expense, color = "#64748b", planned = 2_000_000, plannedPercent = 30.0, actual = 2_154_900, remaining = -154_900),
        CategoryBreakdown(categoryId = "cat_cashflow", name = "Cashflow", kind = CategoryKind.transfer, color = "#3987e5", planned = 1_000_000, actual = 1_000_000, remaining = 0),
    )

    val summary = Summary(
        month = MONTH,
        income = 6_500_000,
        expenses = 3_639_900,
        net = 2_860_100,
        plannedIncome = 6_500_000,
        plannedIncomeMode = IncomePlanMode.fixed,
        netWorth = 31_240_150,
        cashflow = 1_000_000,
        totalBudgeted = 4_900_000,
        accounts = accounts,
        categories = breakdown,
        dailySpend = listOf(
            DailySpend("$MONTH-01", 1_800_000),
            DailySpend("$MONTH-02", 85_000),
            DailySpend("$MONTH-03", 420_000),
            DailySpend("$MONTH-04", 135_000),
            DailySpend("$MONTH-05", 54_900),
            DailySpend("$MONTH-06", 245_050),
        ),
    )
}
