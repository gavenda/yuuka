package dev.gavenda.yuuka.data.remote

import dev.gavenda.yuuka.auth.AuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the current access token to every request, refreshing it first if
 * it is close to expiring — mirroring the per-request `tokenProvider` in the
 * web app's `src/lib/api.ts`, which asks Auth0's SDK for a token fresh each
 * time rather than holding one. A 401 response means the API itself rejected
 * the token, so the local session is dropped the same way `onUnauthorized`
 * does there.
 */
class AuthInterceptor(private val authManager: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { authManager.getValidAccessToken() }

        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()

        val response = chain.proceed(request)
        if (response.code == 401) authManager.signOutLocally()
        return response
    }
}
