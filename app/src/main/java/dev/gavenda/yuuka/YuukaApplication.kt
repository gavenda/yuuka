package dev.gavenda.yuuka

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.gavenda.yuuka.di.databaseModule
import dev.gavenda.yuuka.di.networkModule
import dev.gavenda.yuuka.di.repositoryModule
import dev.gavenda.yuuka.di.viewModelModule
import dev.gavenda.yuuka.auth.AuthManager
import dev.gavenda.yuuka.auth.AuthState
import dev.gavenda.yuuka.repository.SyncRepository
import dev.gavenda.yuuka.sync.NetworkMonitor
import dev.gavenda.yuuka.sync.Outbox
import dev.gavenda.yuuka.sync.PushRegistrar
import dev.gavenda.yuuka.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class YuukaApplication : Application(), SingletonImageLoader.Factory {
    /** Outlives any screen: the queue drains whether or not one is open. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@YuukaApplication)
            // `SyncWorker` takes the outbox as a constructor argument, so
            // WorkManager needs Koin's factory to build it — and the manifest
            // removes WorkManager's own initialiser so this one is used.
            workManagerFactory()
            modules(networkModule, databaseModule, repositoryModule, viewModelModule)
        }

        startSyncing()
    }

    /**
     * The two occasions a queue built offline gets sent.
     *
     * The first is the network coming back while the app is running, which is
     * the case a person actually watches: they land, the strip stops saying
     * "waiting for a connection", and their morning's spending is saved. The
     * `drop(1)` is what stops the first emission — the state at startup, not a
     * change — counting as one.
     *
     * The second is WorkManager, which covers the case nobody watches: the app
     * was closed on the plane and never reopened. Its constraint survives the
     * process being killed, so the queue is sent regardless. Both are backed by
     * [Outbox.flush] doing nothing when there is nothing to send.
     */
    private fun startSyncing() {
        val outbox: Outbox by inject()
        val network: NetworkMonitor by inject()
        val sync: SyncRepository by inject()
        val auth: AuthManager by inject()
        val push: PushRegistrar by inject()

        network.isOnline
            .drop(1)
            .filter { it }
            .onEach { outbox.flush() }
            .launchIn(scope)

        SyncWorker.schedule(this)

        // What the server actually has replaces what was guessed locally. The
        // outbox names the slices its batch touched rather than asking for a
        // full sync: a queued tag rename should not cost the whole ledger.
        outbox.synced
            .onEach { slices -> sync.refreshSlices(slices) }
            .launchIn(scope)

        // A registration token is useless before the server knows whose it is,
        // so it is offered once there is a session and given up when there is
        // not — on a shared phone, a push after signing out would be about a
        // ledger the person now holding it cannot see.
        // Only a sign-out the user asked for throws unsent work away; an expired
        // token is the same person and their queue is still theirs to send.
        auth.signedOut
            .onEach { outbox.discard() }
            .launchIn(scope)

        auth.authState
            .map { it is AuthState.Authenticated }
            .distinctUntilChanged()
            .onEach { signedIn ->
                if (signedIn) {
                    push.register()
                    outbox.flush()
                } else {
                    push.unregister()
                }
            }
            .launchIn(scope)
    }

    /**
     * Account logos are hosted on Wikimedia, which 403s any request without a
     * descriptive User-Agent (https://foundation.wikimedia.org/wiki/Policy:User-Agent_policy).
     * Coil's default client sends a generic one, so logos silently fell back to initials.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Yuuka/${BuildConfig.VERSION_NAME} (dev.gavenda.yuuka; +https://yuuka.gavenda.dev)")
                        .build(),
                )
            }
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = client)) }
            .build()
    }
}
