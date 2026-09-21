package dev.gavenda.yuuka.data.remote

import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.dto.*
import kotlinx.serialization.json.JsonObject
import retrofit2.http.*

/**
 * All routes are under `/api`, mirroring the `api` object in the web app's
 * `src/lib/api.ts`. Partial request bodies (`PATCH`, and the settable fields of
 * `POST`/`PUT`) are passed as a [JsonObject] built by the caller, rather than a
 * fixed request class per endpoint, since the API accepts a subset of fields on
 * every write.
 */
interface YuukaApi {
    @GET("auth/me")
    suspend fun me(): MeResponse

    @GET("payees")
    suspend fun listPayees(@Query("search") search: String? = null, @Query("limit") limit: Int = 20): PayeesResponse

    @DELETE("payees/{id}")
    suspend fun forgetPayee(@Path("id") id: String)

    @GET("settings")
    suspend fun getSettings(): SettingsResponse

    @PATCH("settings")
    suspend fun updateSettings(@Body body: JsonObject): SettingsResponse

    @GET("round-up")
    suspend fun getRoundUpRule(): RoundUpRuleResponse

    @PATCH("round-up")
    suspend fun updateRoundUpRule(@Body body: JsonObject): RoundUpRuleResponse

    @GET("account-types")
    suspend fun listAccountTypes(@Query("includeArchived") includeArchived: Boolean = false): AccountTypesResponse

    @POST("account-types")
    suspend fun createAccountType(@Body body: JsonObject): AccountTypeResponse

    @PATCH("account-types/{id}")
    suspend fun updateAccountType(@Path("id") id: String, @Body body: JsonObject): AccountTypeResponse

    @DELETE("account-types/{id}")
    suspend fun deleteAccountType(@Path("id") id: String)

    @GET("accounts")
    suspend fun listAccounts(@Query("includeArchived") includeArchived: Boolean = false): AccountsResponse

    @POST("accounts")
    suspend fun createAccount(@Body body: JsonObject): AccountResponse

    @PATCH("accounts/{id}")
    suspend fun updateAccount(@Path("id") id: String, @Body body: JsonObject): AccountResponse

    @DELETE("accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: String, @Query("includeTransactions") includeTransactions: Boolean = false)

    @POST("accounts/{id}/adjust")
    suspend fun adjustAccountBalance(@Path("id") id: String, @Body body: JsonObject): TransactionResponse

    @GET("categories")
    suspend fun listCategories(@Query("includeArchived") includeArchived: Boolean = false): CategoriesResponse

    @POST("categories")
    suspend fun createCategory(@Body body: JsonObject): CategoryResponse

    @PATCH("categories/{id}")
    suspend fun updateCategory(@Path("id") id: String, @Body body: JsonObject): CategoryResponse

    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String)

    @GET("tags")
    suspend fun listTags(): TagsResponse

    @POST("tags")
    suspend fun createTag(@Body body: JsonObject): TagResponse

    @PATCH("tags/{id}")
    suspend fun updateTag(@Path("id") id: String, @Body body: JsonObject): TagResponse

    @DELETE("tags/{id}")
    suspend fun deleteTag(@Path("id") id: String)

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

    @POST("transactions")
    suspend fun createTransaction(@Body body: JsonObject): TransactionResponse

    @PATCH("transactions/{id}")
    suspend fun updateTransaction(@Path("id") id: String, @Body body: JsonObject): TransactionResponse

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String)

    @POST("transactions/transfer")
    suspend fun createTransfer(@Body body: JsonObject): TransferResponse

    @PATCH("transactions/transfer/{transferId}")
    suspend fun updateTransfer(@Path("transferId") transferId: String, @Body body: JsonObject): TransferResponse

    @GET("subscriptions")
    suspend fun listSubscriptions(): SubscriptionsResponse

    @POST("subscriptions")
    suspend fun createSubscription(@Body body: JsonObject): SubscriptionResponse

    @PATCH("subscriptions/{id}")
    suspend fun updateSubscription(@Path("id") id: String, @Body body: JsonObject): SubscriptionResponse

    @DELETE("subscriptions/{id}")
    suspend fun deleteSubscription(@Path("id") id: String)

    @GET("budgets")
    suspend fun listBudgets(@Query("month") month: String): BudgetsResponse

    @PUT("budgets")
    suspend fun setBudget(@Body body: JsonObject): BudgetResponse

    @DELETE("budgets/{id}")
    suspend fun deleteBudget(@Path("id") id: String)

    @GET("income-plan")
    suspend fun getIncomePlan(@Query("month") month: String): IncomePlanResponse

    @PUT("income-plan")
    suspend fun setIncomePlan(@Body body: JsonObject): IncomePlanResponse

    @GET("summary")
    suspend fun summary(@Query("month") month: String): Summary

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
