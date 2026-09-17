package dev.gavenda.yuuka.data.remote

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * The API answers several endpoints with a bare 204 and no body (mirroring the
 * `if (response.status === 204) return undefined as T` short-circuit in the web
 * app's `src/lib/api.ts`). A JSON converter fails trying to decode that empty
 * body as `Unit`, so this factory intercepts exactly that return type and just
 * consumes the body instead of parsing it. Must be registered before the
 * kotlinx.serialization converter.
 */
class UnitConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type == Unit::class.java) {
            return Converter<ResponseBody, Unit> { body -> body.close() }
        }
        return null
    }
}
