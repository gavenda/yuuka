package dev.gavenda.yuuka.di

import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.repository.PayeeRepository
import dev.gavenda.yuuka.repository.TransactionRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { LedgerRepository(get(), get(), get(), get(), get()) }
    single { TransactionRepository(get(), get()) }
    single { BudgetRepository(get(), get(), get(), get()) }
    single { PayeeRepository(get(), get()) }
}
