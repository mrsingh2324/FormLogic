package com.formlogic.services

import com.formlogic.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

@Serializable data class RegisterRequest(val name: String, val email: String, val password: String, val age: Int? = null)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RefreshRequest(val refresh_token: String)
@Serializable data class TokenBody(val token: String)
@Serializable data class AuthData(val user: RemoteUser, val access_token: String, val refresh_token: String)
@Serializable data class RemoteUser(val id: String, val name: String, val email: String, val is_email_verified: Boolean = false, val profile: RemoteProfile? = null)
@Serializable data class RemoteProfile(val age: Int? = null, val weight: Double? = null, val height: Double? = null, val fitness_level: String? = null, val goals: List<String> = emptyList(), val equipment: List<String> = emptyList(), val timezone: String = "Asia/Kolkata")
@Serializable data class AuthEnvelope(val success: Boolean, val data: AuthData? = null, val error: String? = null)
@Serializable data class UserEnvelope(val success: Boolean, val data: RemoteUser? = null, val error: String? = null)
@Serializable data class ApiEnvelope(val success: Boolean, val data: JsonObject? = null, val error: String? = null)
@Serializable data class ListEnvelope(val success: Boolean, val data: List<JsonObject>? = null, val error: String? = null)
@Serializable data class MessageEnvelope(val success: Boolean, val message: String? = null, val error: String? = null)
@Serializable data class SaveSessionRequest(val duration: Int, val exercises: List<ExerciseInput>, val notes: String? = null, val mood: String? = null)
@Serializable data class ExerciseInput(val exercise_id: String, val exercise_name: String, val reps: Int, val sets: Int = 1, val form_scores: List<Double> = emptyList(), val avg_form_score: Double = 0.0, val completed: Boolean = true)
@Serializable data class GeneratePlanRequest(val name: String, val goal: String, val fitness_level: String, val duration_weeks: Int = 4, val equipment: List<String> = emptyList(), val days_per_week: Int = 3)
@Serializable data class LogMealRequest(val meal_type: String, val food_item_id: String, val quantity: Double, val notes: String? = null)
@Serializable data class WaterLogRequest(val amount_ml: Int)
@Serializable data class WeightLogRequest(val weight_kg: Double, val body_fat_pct: Double? = null, val notes: String? = null)
@Serializable data class DeleteAccountRequest(val confirm_phrase: String)
@Serializable data class ReminderRequest(val enabled: Boolean, val hour: Int? = null, val minute: Int? = null, val workout_name: String? = null)
@Serializable data class ChatMessageRequest(val content: String, val thread_id: String? = null, val context_tags: List<String> = emptyList())
@Serializable data class CreateThreadRequest(val title: String)
@Serializable data class ChatThread(val id: String, val title: String, val updated_at: String? = null, val created_at: String? = null)
@Serializable data class CoachCitation(val source: String, val snippet: String? = null)
@Serializable data class ChatMessageOut(
    val role: String,
    val content: String,
    val ts: String? = null,
    val citations: List<CoachCitation> = emptyList(),
    val model: String? = null,
    val structured: JsonObject? = null
)
@Serializable data class ChatReplyData(val thread_id: String, val message: ChatMessageOut, val streaming: Boolean = false)
@Serializable data class ChatReplyEnvelope(val success: Boolean, val data: ChatReplyData? = null, val error: String? = null)
@Serializable data class ThreadListEnvelope(val success: Boolean, val data: List<ChatThread>? = null, val error: String? = null)
@Serializable data class ThreadEnvelope(val success: Boolean, val data: ChatThread? = null, val error: String? = null)
@Serializable data class RegeneratePlanRequest(val plan_id: String, val reason: String? = "weekly_refresh")
@Serializable data class PlanDiffData(val baseline_plan_id: String? = null, val current_plan_id: String? = null, val changes: List<String> = emptyList())
@Serializable data class PlanDiffEnvelope(val success: Boolean, val data: PlanDiffData? = null, val error: String? = null)
@Serializable data class WeeklyReportEnvelope(val success: Boolean, val data: JsonObject? = null, val error: String? = null)
@Serializable data class MonthlyReportEnvelope(val success: Boolean, val data: JsonObject? = null, val error: String? = null)

interface FormLogicApi {
    @POST("auth/register")             suspend fun register(@Body b: RegisterRequest): Response<AuthEnvelope>
    @POST("auth/login")                suspend fun login(@Body b: LoginRequest): Response<AuthEnvelope>
    @POST("auth/refresh")              suspend fun refresh(@Body b: RefreshRequest): Response<AuthEnvelope>
    @POST("auth/logout")               suspend fun logout(@Body b: RefreshRequest): Response<MessageEnvelope>
    @GET("auth/verify-email/{token}")  suspend fun verifyEmail(@Path("token") t: String): Response<MessageEnvelope>
    @POST("auth/resend-verification")  suspend fun resendVerification(@Body b: Map<String,String>): Response<MessageEnvelope>
    @POST("auth/forgot-password")      suspend fun forgotPassword(@Body b: Map<String,String>): Response<MessageEnvelope>
    @POST("auth/reset-password")       suspend fun resetPassword(@Body b: Map<String,String>): Response<MessageEnvelope>
    @POST("auth/social/google")        suspend fun googleSignIn(@Body b: Map<String,String>): Response<AuthEnvelope>
    @POST("auth/social/apple")         suspend fun appleSignIn(@Body b: Map<String,String>): Response<AuthEnvelope>
    @GET("users/me")                   suspend fun getProfile(@Header("Authorization") t: String): Response<UserEnvelope>
    @PUT("users/me")                   suspend fun updateProfile(@Header("Authorization") t: String, @Body b: Map<String,@JvmSuppressWildcards Any>): Response<UserEnvelope>
    @GET("users/me/stats")             suspend fun getStats(@Header("Authorization") t: String): Response<ApiEnvelope>
    @POST("workouts/sessions")         suspend fun saveSession(@Header("Authorization") t: String, @Body b: SaveSessionRequest): Response<ApiEnvelope>
    @GET("workouts/sessions")          suspend fun getHistory(@Header("Authorization") t: String, @Query("page") p: Int = 1, @Query("limit") l: Int = 20): Response<ListEnvelope>
    @DELETE("workouts/sessions/{id}")  suspend fun deleteSession(@Header("Authorization") t: String, @Path("id") id: String): Response<MessageEnvelope>
    @GET("workouts/progress/weekly")   suspend fun weeklyProgress(@Header("Authorization") t: String): Response<ApiEnvelope>
    @GET("nutrition/food/search")      suspend fun searchFood(@Query("q") q: String): Response<ListEnvelope>
    @POST("nutrition/meals")           suspend fun logMeal(@Header("Authorization") t: String, @Body b: LogMealRequest): Response<ApiEnvelope>
    @GET("nutrition/meals/daily/{d}")  suspend fun dailySummary(@Header("Authorization") t: String, @Path("d") d: String): Response<ApiEnvelope>
    @GET("nutrition/meals/weekly")     suspend fun weeklyNutrition(@Header("Authorization") t: String): Response<ApiEnvelope>
    @DELETE("nutrition/meals/{id}")    suspend fun deleteMealLog(@Header("Authorization") t: String, @Path("id") id: String): Response<MessageEnvelope>
    @POST("plans/generate")            suspend fun generatePlan(@Header("Authorization") t: String, @Body b: GeneratePlanRequest): Response<ApiEnvelope>
    @GET("plans/")                     suspend fun getPlans(@Header("Authorization") t: String): Response<ListEnvelope>
    @GET("plans/active")               suspend fun getActivePlan(@Header("Authorization") t: String): Response<ApiEnvelope>
    @PUT("plans/{id}/activate")        suspend fun activatePlan(@Header("Authorization") t: String, @Path("id") id: String): Response<ApiEnvelope>
    @DELETE("plans/{id}")              suspend fun deletePlan(@Header("Authorization") t: String, @Path("id") id: String): Response<MessageEnvelope>
    @GET("achievements/")              suspend fun getAchievements(@Header("Authorization") t: String): Response<ListEnvelope>
    @POST("achievements/check")        suspend fun checkAchievements(@Header("Authorization") t: String): Response<ApiEnvelope>
    @POST("tracking/water")            suspend fun logWater(@Header("Authorization") t: String, @Body b: WaterLogRequest): Response<ApiEnvelope>
    @GET("tracking/water/{date}")      suspend fun getWater(@Header("Authorization") t: String, @Path("date") d: String): Response<ApiEnvelope>
    @POST("tracking/weight")           suspend fun logWeight(@Header("Authorization") t: String, @Body b: WeightLogRequest): Response<ApiEnvelope>
    @GET("tracking/weight")            suspend fun getWeight(@Header("Authorization") t: String): Response<ListEnvelope>
    @POST("notifications/token")       suspend fun registerPushToken(@Header("Authorization") t: String, @Body b: TokenBody): Response<MessageEnvelope>
    @POST("notifications/reminder")    suspend fun setReminder(@Header("Authorization") t: String, @Body b: ReminderRequest): Response<MessageEnvelope>
    @GET("privacy/export")             suspend fun exportData(@Header("Authorization") t: String): Response<ApiEnvelope>
    @POST("privacy/account-delete")    suspend fun deleteAccount(@Header("Authorization") t: String, @Body b: DeleteAccountRequest): Response<MessageEnvelope>
    @POST("ai/verify")                 suspend fun verifyTask(@Header("Authorization") t: String, @Body b: Map<String,String>): Response<ApiEnvelope>
    @POST("ai/chat")                   suspend fun coachChat(@Header("Authorization") t: String, @Body b: ChatMessageRequest): Response<ChatReplyEnvelope>
    @GET("ai/chat/threads")            suspend fun getChatThreads(@Header("Authorization") t: String): Response<ThreadListEnvelope>
    @POST("ai/chat/threads")           suspend fun createChatThread(@Header("Authorization") t: String, @Body b: CreateThreadRequest): Response<ThreadEnvelope>
    @POST("plans/regenerate")          suspend fun regeneratePlan(@Header("Authorization") t: String, @Body b: RegeneratePlanRequest): Response<ApiEnvelope>
    @GET("plans/{id}/diff")            suspend fun getPlanDiff(@Header("Authorization") t: String, @Path("id") id: String, @Query("baseline_plan_id") baseId: String? = null): Response<PlanDiffEnvelope>
    @GET("reports/weekly")             suspend fun weeklyReport(@Header("Authorization") t: String): Response<WeeklyReportEnvelope>
    @GET("reports/monthly")            suspend fun monthlyReport(@Header("Authorization") t: String): Response<MonthlyReportEnvelope>
}

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    fun create(baseUrl: String): FormLogicApi = Retrofit.Builder()
        .baseUrl("$baseUrl/api/v1/")
        .client(buildClient())
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
        .build()
        .create(FormLogicApi::class.java)
}

object ApiErrorMapper {
    fun fromMessage(error: String?, fallback: String): String = if (error.isNullOrBlank()) fallback else error
    fun fromDetail(detail: String?, fallback: String): String = if (detail.isNullOrBlank()) fallback else detail
    fun fromJsonError(data: JsonObject?, fallback: String): String {
        if (data == null) return fallback
        val error = runCatching { data["error"]?.jsonPrimitive?.content }.getOrNull()
        if (!error.isNullOrBlank()) return error
        val detail = runCatching { data["detail"]?.jsonPrimitive?.content }.getOrNull()
        if (!detail.isNullOrBlank()) return detail
        val message = runCatching { data["message"]?.jsonPrimitive?.content }.getOrNull()
        if (!message.isNullOrBlank()) return message
        return fallback
    }
}
