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
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class YuukaApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@YuukaApplication)
            modules(networkModule, databaseModule, repositoryModule, viewModelModule)
        }
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
