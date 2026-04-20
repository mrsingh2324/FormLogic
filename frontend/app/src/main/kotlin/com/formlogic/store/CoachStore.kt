package com.formlogic.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

object CoachStore {
    private val _latestStructured = MutableStateFlow<JsonObject?>(null)
    val latestStructured: StateFlow<JsonObject?> = _latestStructured

    fun publish(structured: JsonObject?) {
        if (structured != null) _latestStructured.value = structured
    }
}
