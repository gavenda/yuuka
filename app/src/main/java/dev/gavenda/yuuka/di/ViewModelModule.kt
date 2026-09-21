package dev.gavenda.yuuka.di

import dev.gavenda.yuuka.ui.accounts.AccountsViewModel
import dev.gavenda.yuuka.ui.budget.BudgetViewModel
import dev.gavenda.yuuka.ui.categories.CategoriesViewModel
import dev.gavenda.yuuka.ui.dashboard.DashboardViewModel
import dev.gavenda.yuuka.ui.savethechange.SaveTheChangeViewModel
import dev.gavenda.yuuka.ui.settings.SettingsViewModel
import dev.gavenda.yuuka.ui.subscriptions.SubscriptionsViewModel
import dev.gavenda.yuuka.ui.tags.TagsViewModel
import dev.gavenda.yuuka.ui.transactions.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { DashboardViewModel(get(), get(), get(), get()) }
    viewModel { TransactionsViewModel(get(), get(), get(), get()) }
    viewModel { BudgetViewModel(get(), get(), get()) }
    viewModel { AccountsViewModel(get(), get()) }
    viewModel { CategoriesViewModel(get()) }
    viewModel { TagsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { SaveTheChangeViewModel(get()) }
    viewModel { SubscriptionsViewModel(get(), get(), get()) }
}
