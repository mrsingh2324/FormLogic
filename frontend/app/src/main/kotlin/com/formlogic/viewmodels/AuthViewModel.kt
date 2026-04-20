package com.formlogic.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formlogic.BuildConfig
import com.formlogic.services.*
import com.formlogic.store.AuthStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

sealed class AuthState {
    object Idle      : AuthState()
    object Loading   : AuthState()
    object LoggedIn  : AuthState()
    object LoggedOut : AuthState()
    data class Success(val message: String = "") : AuthState()
    data class Error(val message: String)        : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    val store = AuthStore(application)
    val api   = ApiClient.create(BuildConfig.BASE_URL)

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val accessToken = store.accessToken
    val refreshToken = store.refreshToken
    val user        = store.user
    val isLoggedIn  = store.accessToken.map { !it.isNullOrBlank() }
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val token = store.accessToken.first()
            if (!token.isNullOrBlank()) runCatching { loadProfile(token) }
        }
    }

    fun register(name: String, email: String, password: String, age: Int? = null) = viewModelScope.launch {
        _state.value = AuthState.Loading
        runCatching {
            val res = api.register(RegisterRequest(name, email, password, age))
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                val d = body.data!!; store.saveTokens(d.access_token, d.refresh_token, d.user)
                _state.value = AuthState.Success("Please verify your email before logging in.")
            } else {
                val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                val detail = runCatching { raw?.let { json.parseToJsonElement(it).jsonObject["detail"]?.toString()?.trim('"') } }.getOrNull()
                _state.value = AuthState.Error(ApiErrorMapper.fromMessage(body?.error ?: detail, "Registration failed"))
            }
        }.onFailure { _state.value = AuthState.Error(it.message ?: "Network error") }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _state.value = AuthState.Loading
        runCatching {
            val res = api.login(LoginRequest(email, password))
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                val d = body.data!!; store.saveTokens(d.access_token, d.refresh_token, d.user)
                _state.value = AuthState.LoggedIn
            } else {
                val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                val detail = runCatching { raw?.let { json.parseToJsonElement(it).jsonObject["detail"]?.toString()?.trim('"') } }.getOrNull()
                _state.value = AuthState.Error(ApiErrorMapper.fromMessage(body?.error ?: detail, "Login failed"))
            }
        }.onFailure { _state.value = AuthState.Error(it.message ?: "Network error") }
    }

    fun logout() = viewModelScope.launch {
        runCatching { val rt = store.refreshToken.first(); if (!rt.isNullOrBlank()) api.logout(RefreshRequest(rt)) }
        store.clearSession(); _state.value = AuthState.LoggedOut
    }

    fun forgotPassword(email: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        runCatching { api.forgotPassword(mapOf("email" to email)); onResult(true, "Reset link sent if email is registered.") }
            .onFailure { onResult(true, "Reset link sent if email is registered.") }
    }

    fun resetPassword(token: String, password: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        runCatching {
            val res = api.resetPassword(mapOf("token" to token, "password" to password))
            if (res.isSuccessful) onResult(true, "Password reset! Please log in.") else onResult(false, res.body()?.error ?: "Failed")
        }.onFailure { onResult(false, it.message ?: "Network error") }
    }

    fun resendVerification(email: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        runCatching { api.resendVerification(mapOf("email" to email)); onResult(true) }.onFailure { onResult(false) }
    }

    private suspend fun loadProfile(token: String) {
        val res = runCatching { api.getProfile("Bearer $token") }.getOrNull() ?: return
        if (res.isSuccessful) {
            res.body()?.data?.let { store.updateUser(it) }
            return
        }
        if (res.code() == 401) {
            val refreshed = refreshSession()
            if (!refreshed) {
                store.clearSession()
                _state.value = AuthState.LoggedOut
            }
        }
    }

    fun resetState() { _state.value = AuthState.Idle }

    /**
     * Persist body composition / physique data collected in the final onboarding steps.
     * Called before onComplete() so the profile is written before the first plan is generated.
     */
    fun updatePhysiqueProfile(
        currentBodyType:     String,
        currentBodyFatPct:   String,
        currentPhysiqueDesc: String,
        targetLook:          String,
        targetBodyType:      String,
        targetPhysiqueDesc:  String,
    ) = viewModelScope.launch {
        val token = store.accessToken.first() ?: return@launch
        runCatching {
            val profilePatch = buildMap<String, Any> {
                put("profile", buildMap<String, Any> {
                    if (currentBodyType.isNotBlank())     put("current_body_type",               currentBodyType)
                    if (currentBodyFatPct.isNotBlank())   put("estimated_body_fat_pct_range",     currentBodyFatPct)
                    if (currentPhysiqueDesc.isNotBlank()) put("current_physique_description",     currentPhysiqueDesc)
                    if (targetLook.isNotBlank())          put("target_look",                      targetLook)
                    if (targetBodyType.isNotBlank())      put("target_body_type",                 targetBodyType)
                    if (targetPhysiqueDesc.isNotBlank())  put("target_physique_description",      targetPhysiqueDesc)
                })
            }
            val res = api.updateProfile("Bearer $token", profilePatch)
            if (res.isSuccessful) {
                res.body()?.data?.let { store.updateUser(it) }
            }
        }
    }

    suspend fun refreshSession(): Boolean {
        val rt = store.refreshToken.first() ?: return false
        return runCatching {
            val res = api.refresh(RefreshRequest(rt))
            val body = res.body()
            if (!res.isSuccessful || body?.success != true || body.data == null) return@runCatching false
            store.saveTokens(body.data.access_token, body.data.refresh_token, body.data.user)
            true
        }.getOrDefault(false)
    }
}
