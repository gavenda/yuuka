package dev.gavenda.yuuka.di

import dev.gavenda.yuuka.repository.*
import dev.gavenda.yuuka.sync.DeviceId
import dev.gavenda.yuuka.sync.NetworkMonitor
import dev.gavenda.yuuka.sync.Outbox
import dev.gavenda.yuuka.sync.PushRegistrar
import dev.gavenda.yuuka.sync.SyncWorker
import dev.gavenda.yuuka.update.AppUpdater
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val repositoryModule = module {
    // The offline queue every write goes through, and the two things that
    // decide when it drains: the network coming back, and a push saying the
    // ledger moved somewhere else.
    single { Outbox(get(), get()) }
    single { DeviceId(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single { PushRegistrar(get(), get()) }

    single { LedgerRepository(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { TransactionRepository(get(), get(), get(), get(), get(), get()) }
    single { BudgetRepository(get(), get(), get(), get(), get()) }
    single { PayeeRepository(get(), get()) }
    single { SubscriptionRepository(get(), get(), get(), get(), get()) }
    single { InvestmentRepository(get(), get(), get(), get(), get(), get()) }
    single { SyncRepository(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Offers and installs a newer release from GitHub on a fresh open.
    single { AppUpdater(androidContext()) }

    // Takes the outbox as a constructor argument, which the default factory
    // cannot supply — hence Koin's, installed in `YuukaApplication`.
    workerOf(::SyncWorker)
}
