package dev.gavenda.yuuka.data.remote

import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.model.Trend
import dev.gavenda.yuuka.data.remote.dto.*
import retrofit2.http.*

/**
 * All routes are under `/api`, mirroring the `api` object in the web app's
 * `src/lib/api.ts`. Only reads and the sync batch are here: every write goes
 * through the outbox, whose queued operations are replayed by [syncBatch].
 */
interface YuukaApi {
    @GET("auth/me")
    suspend fun me(): MeResponse

    @GET("payees")
    suspend fun listPayees(@Query("search") search: String? = null, @Query("limit") limit: Int = 20): PayeesResponse

    @GET("settings")
    suspend fun getSettings(): SettingsResponse

    @GET("round-up")
    suspend fun getRoundUpRule(): RoundUpRuleResponse

    @GET("account-types")
    suspend fun listAccountTypes(@Query("includeArchived") includeArchived: Boolean = false): AccountTypesResponse

    @GET("accounts")
    suspend fun listAccounts(@Query("includeArchived") includeArchived: Boolean = false): AccountsResponse

    @GET("categories")
    suspend fun listCategories(@Query("includeArchived") includeArchived: Boolean = false): CategoriesResponse

    @GET("tags")
    suspend fun listTags(): TagsResponse

    @GET("transactions")
    suspend fun listTransactions(
        @Query("month") month: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("accountId") accountId: String? = null,
        @Query("categoryId") categoryId: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): dev.gavenda.yuuka.data.model.TransactionPage

    @GET("subscriptions")
    suspend fun listSubscriptions(): SubscriptionsResponse

    @GET("summary")
    suspend fun summary(@Query("month") month: String): Summary

    @GET("trend")
    suspend fun trend(@Query("month") month: String, @Query("months") months: Int): Trend

    /**
     * Drains the offline queue. The operations are replayed in the order they
     * are sent, through the same routes an online call would have reached, and
     * the response says what became of each one. Sending the same batch twice
     * is safe — see `server/sync.ts`.
     */
    @POST("sync/batch")
    suspend fun syncBatch(@Body body: SyncBatchRequest): SyncBatchResponse

    /** Says where to reach this install, so a change made elsewhere can be pushed here. Repeated on every start; FCM rotates tokens. */
    @PUT("devices")
    suspend fun registerDevice(@Body body: DeviceRegistrationRequest)

    /** Signing out. Naming the token rather than the install means a stale caller cannot unregister whatever it holds now. */
    @DELETE("devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String)
}
