package dev.gavenda.yuuka.auth

import android.app.Activity
import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import dev.gavenda.yuuka.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val credentials: Credentials) : AuthState
}

/**
 * Owns the Auth0 session: sign-in, sign-out, restoring credentials on launch,
 * and handing the network layer a fresh access token per request (refreshing
 * it first if it is close to expiring). A Koin singleton, so every screen and
 * the Retrofit auth interceptor observe the same session state.
 *
 * Login and logout need an [Activity] to host the browser tab Auth0 signs in
 * through; everything else — restoring a session, reading the current token —
 * needs only the application [Context] this is constructed with.
 */
class AuthManager(private val context: Context) {
    private val account: Auth0 by lazy {
        Auth0.getInstance(
            context.getString(R.string.com_auth0_client_id),
            context.getString(R.string.com_auth0_domain),
        )
    }

    private val credentialsManager: SecureCredentialsManager by lazy {
        SecureCredentialsManager(context, account, SharedPreferencesStorage(context))
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    init {
        restoreSession()
    }

    private fun restoreSession() {
        credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) {
                _authState.value = AuthState.Authenticated(result)
            }

            override fun onFailure(error: CredentialsManagerException) {
                _authState.value = AuthState.Unauthenticated
            }
        })
    }

    fun login(activity: Activity, screenHint: String? = null) {
        WebAuthProvider.login(account)
            .withScheme(context.getString(R.string.com_auth0_scheme))
            // offline_access: requests a refresh token for session persistence.
            .withScope("openid profile email offline_access")
            // The API only accepts tokens addressed to it; without this the token
            // Auth0 issues cannot be verified against AUTH0_AUDIENCE server-side.
            .withAudience(context.getString(R.string.auth0_audience))
            .withParameters(buildMap { screenHint?.let { put("screen_hint", it) } })
            .start(
                activity,
                object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        credentialsManager.saveCredentials(result)
                        _authState.value = AuthState.Authenticated(result)
                    }

                    override fun onFailure(error: AuthenticationException) {
                        _errors.tryEmit(error.getDescription())
                    }
                },
            )
    }

    fun logout(activity: Activity) {
        WebAuthProvider.logout(account)
            .withScheme(context.getString(R.string.com_auth0_scheme))
            .start(
                activity,
                object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) {
                        credentialsManager.clearCredentials()
                        _authState.value = AuthState.Unauthenticated
                    }

                    override fun onFailure(error: AuthenticationException) {
                        // A failed remote logout should not trap the user signed in locally.
                        credentialsManager.clearCredentials()
                        _authState.value = AuthState.Unauthenticated
                        _errors.tryEmit(error.getDescription())
                    }
                },
            )
    }

    /**
     * Drops the local session without contacting Auth0 — for when the API itself
     * rejects the access token as unauthorised (expired beyond refresh, revoked),
     * mirroring the `onUnauthorized` handler `src/lib/api.ts` installs.
     */
    fun signOutLocally() {
        credentialsManager.clearCredentials()
        _authState.value = AuthState.Unauthenticated
    }

    /** A valid access token, refreshed first if it is close to expiring, or null if there is no session. */
    suspend fun getValidAccessToken(): String? {
        if (!credentialsManager.hasValidCredentials()) return null

        return suspendCancellableCoroutine { continuation ->
            credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
                override fun onSuccess(result: Credentials) {
                    continuation.resume(result.accessToken)
                }

                override fun onFailure(error: CredentialsManagerException) {
                    continuation.resume(null)
                }
            })
        }
    }
}
