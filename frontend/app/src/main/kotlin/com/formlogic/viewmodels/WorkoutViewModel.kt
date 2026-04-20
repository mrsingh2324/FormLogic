package com.formlogic.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formlogic.BuildConfig
import com.formlogic.services.*
import com.formlogic.store.AuthStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AuthStore(application)
    private val api   = ApiClient.create(BuildConfig.BASE_URL)

    private val _history  = MutableStateFlow<List<JsonObject>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error    = MutableStateFlow<String?>(null)
    private val _stats    = MutableStateFlow<JsonObject?>(null)
    private val _weekly   = MutableStateFlow<JsonObject?>(null)

    val history:    StateFlow<List<JsonObject>> = _history.asStateFlow()
    val isLoading:  StateFlow<Boolean>          = _isLoading.asStateFlow()
    val error:      StateFlow<String?>          = _error.asStateFlow()
    val stats:      StateFlow<JsonObject?>      = _stats.asStateFlow()
    val weekly:     StateFlow<JsonObject?>      = _weekly.asStateFlow()

    fun loadHistory() = viewModelScope.launch {
        _isLoading.value = true
        runCatching {
            val t = store.accessToken.first() ?: return@runCatching
            val res = api.getHistory("Bearer $t")
            if (res.isSuccessful) _history.value = res.body()?.data ?: emptyList()
            else _error.value = res.body()?.error ?: "Failed to load history"
        }.onFailure { _error.value = it.message }
        _isLoading.value = false
    }

    fun loadStats() = viewModelScope.launch {
        runCatching {
            val t = store.accessToken.first() ?: return@runCatching
            val res = api.getStats("Bearer $t")
            if (res.isSuccessful) _stats.value = res.body()?.data
        }
    }

    fun loadWeeklyProgress() = viewModelScope.launch {
        runCatching {
            val t = store.accessToken.first() ?: return@runCatching
            val res = api.weeklyProgress("Bearer $t")
            if (res.isSuccessful) _weekly.value = res.body()?.data
        }
    }

    fun saveSession(req: SaveSessionRequest, onSuccess: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val t = store.accessToken.first() ?: return@runCatching
            val res = api.saveSession("Bearer $t", req)
            if (res.isSuccessful && res.body()?.success == true) { loadHistory(); onSuccess() }
            else onError(res.body()?.error ?: "Save failed")
        }.onFailure { onError(it.message ?: "Network error") }
    }

    fun deleteSession(id: String) = viewModelScope.launch {
        runCatching {
            val t = store.accessToken.first() ?: return@runCatching
            api.deleteSession("Bearer $t", id)
            _history.value = _history.value.filter { it["_id"]?.jsonPrimitive?.content != id }
        }
    }
}
