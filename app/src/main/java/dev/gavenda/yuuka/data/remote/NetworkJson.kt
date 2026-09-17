package dev.gavenda.yuuka.data.remote

import kotlinx.serialization.json.Json

/** The one [Json] configuration shared between the Retrofit converter and error-body parsing. */
val apiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
