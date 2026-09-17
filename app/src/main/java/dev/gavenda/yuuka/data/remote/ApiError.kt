package dev.gavenda.yuuka.data.remote

import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.io.IOException

/** An error carrying the API's status code and any per-field validation detail. Mirrors `src/lib/api.ts`. */
class ApiError(
    val status: Int,
    message: String,
    val details: Map<String, List<String>>? = null,
) : Exception(message) {
    /** True when the session is missing or expired and the user must sign in again. */
    val isUnauthorized: Boolean get() = status == 401
}

@Serializable
private data class ErrorBody(
    val error: String? = null,
    val details: Map<String, List<String>>? = null,
)

/** Runs a Retrofit suspend call, translating a non-2xx response or a network failure into an [ApiError]. */
suspend fun <T> apiCall(block: suspend () -> T): T {
    return try {
        block()
    } catch (e: HttpException) {
        val raw = e.response()?.errorBody()?.string()
        val parsed = raw?.let { runCatching { apiJson.decodeFromString(ErrorBody.serializer(), it) }.getOrNull() }
        throw ApiError(e.code(), parsed?.error ?: "Request failed (${e.code()}).", parsed?.details)
    } catch (e: ApiError) {
        throw e
    } catch (e: IOException) {
        throw ApiError(0, "Network error. Check your connection.", null)
    }
}
