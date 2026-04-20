package com.formlogic.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.formlogic.services.RemoteUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "formlogic_auth")

class AuthStore(private val context: Context) {
    companion object {
        val ACCESS_TOKEN  = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_JSON     = stringPreferencesKey("user_json")
    }

    val accessToken:  Flow<String?>     = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val refreshToken: Flow<String?>     = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val user:         Flow<RemoteUser?> = context.dataStore.data.map { prefs ->
        prefs[USER_JSON]?.let { runCatching { Json.decodeFromString<RemoteUser>(it) }.getOrNull() }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String, user: RemoteUser) {
        context.dataStore.edit {
            it[ACCESS_TOKEN]  = accessToken
            it[REFRESH_TOKEN] = refreshToken
            it[USER_JSON]     = Json.encodeToString(user)
        }
    }

    suspend fun updateUser(user: RemoteUser) {
        context.dataStore.edit { it[USER_JSON] = Json.encodeToString(user) }
    }

    suspend fun clearSession() { context.dataStore.edit { it.clear() } }
}
