package dev.gavenda.yuuka.data.remote

import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.dto.AccountResponse
import dev.gavenda.yuuka.data.remote.dto.AccountTypeResponse
import dev.gavenda.yuuka.data.remote.dto.AccountTypesResponse
import dev.gavenda.yuuka.data.remote.dto.AccountsResponse
import dev.gavenda.yuuka.data.remote.dto.BudgetResponse
import dev.gavenda.yuuka.data.remote.dto.BudgetsResponse
import dev.gavenda.yuuka.data.remote.dto.CategoriesResponse
import dev.gavenda.yuuka.data.remote.dto.CategoryResponse
import dev.gavenda.yuuka.data.remote.dto.IncomePlanResponse
import dev.gavenda.yuuka.data.remote.dto.MeResponse
import dev.gavenda.yuuka.data.remote.dto.PayeesResponse
import dev.gavenda.yuuka.data.remote.dto.SettingsResponse
import dev.gavenda.yuuka.data.remote.dto.TransactionResponse
import dev.gavenda.yuuka.data.remote.dto.TransferResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("categories")
    suspend fun listCategories(@Query("includeArchived") includeArchived: Boolean = false): CategoriesResponse

    @POST("categories")
    suspend fun createCategory(@Body body: JsonObject): CategoryResponse

    @PATCH("categories/{id}")
    suspend fun updateCategory(@Path("id") id: String, @Body body: JsonObject): CategoryResponse

    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String)

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
}
