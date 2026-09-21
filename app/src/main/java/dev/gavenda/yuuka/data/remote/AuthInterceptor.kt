package dev.gavenda.yuuka.data.remote

import dev.gavenda.yuuka.auth.AuthManager
import dev.gavenda.yuuka.sync.DeviceId
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
 *
 * It also names this install on every request. The server uses that to leave
 * this device out when it tells the user's other devices that something
 * changed — we already have the answer, and a push back would only make us
 * refetch what we just wrote.
 */
class AuthInterceptor(
    private val authManager: AuthManager,
    private val deviceId: DeviceId,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { authManager.getValidAccessToken() }

        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
            addHeader("X-Yuuka-Device", deviceId.value)
        }.build()

        val response = chain.proceed(request)
        if (response.code == 401) authManager.signOutLocally()
        return response
    }
}
