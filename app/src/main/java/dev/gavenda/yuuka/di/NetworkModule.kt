package dev.gavenda.yuuka.di

import dev.gavenda.yuuka.BuildConfig
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.auth.AuthManager
import dev.gavenda.yuuka.data.remote.AuthInterceptor
import dev.gavenda.yuuka.data.remote.UnitConverterFactory
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiJson
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.ThemePreference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

val networkModule = module {
    single { AuthManager(androidContext()) }
    single { AmountVisibility(androidContext()) }
    single { ThemePreference(androidContext()) }

    single {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get()))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                },
            )
            .build()
    }

    single {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(androidContext().getString(R.string.api_base_url))
            .client(get<OkHttpClient>())
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(apiJson.asConverterFactory(contentType))
            .build()
    }

    single { get<Retrofit>().create(YuukaApi::class.java) }
}
