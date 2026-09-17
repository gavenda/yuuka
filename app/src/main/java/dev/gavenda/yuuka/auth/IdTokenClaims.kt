package dev.gavenda.yuuka.auth

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The subset of standard OIDC claims the app bar's avatar needs. */
@Serializable
data class IdTokenClaims(
    val name: String? = null,
    val email: String? = null,
    val picture: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads the profile claims out of an ID token's payload without verifying its signature —
 * the token was already validated by Auth0 during login, so this is just decoding, mirroring
 * how the web app reads `id_token` claims client-side for the user menu.
 */
fun decodeIdTokenClaims(idToken: String): IdTokenClaims? {
    val payload = idToken.split(".").getOrNull(1) ?: return null
    return try {
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        json.decodeFromString<IdTokenClaims>(String(decoded, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}
