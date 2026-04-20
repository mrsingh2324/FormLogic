package com.formlogic.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class QueueItem(
    val id: String,
    val type: String,   // "workout_session" | "meal_log" | "weight_log" | "water_log"
    val payload: String, // JSON-serialised payload
    val createdAt: Long = System.currentTimeMillis(),
    var retryCount: Int = 0,
)

/**
 * OfflineQueue — port of utils/offlineQueue.ts
 * Persists failed API calls to disk and retries them when connectivity returns.
 */
class OfflineQueue(private val context: Context) {

    private val file = File(context.filesDir, "offline_queue.json")
    private val MAX_RETRIES = 5
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: kotlinx.coroutines.Job? = null

    // ─── Init / destroy ───────────────────────────────────────────────────────

    fun init() {
        // Poll every 30 seconds, same as original
        pollJob = scope.launch {
            while (true) {
                delay(30_000)
                drain()
            }
        }
        // Also try immediately
        scope.launch { drain() }
    }

    fun destroy() { pollJob?.cancel() }

    // ─── Queue operations ─────────────────────────────────────────────────────

    fun enqueue(type: String, payload: String) {
        val items = load().toMutableList()
        items.add(QueueItem(id = "${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}", type = type, payload = payload))
        save(items)
    }

    fun pendingCount(): Int = load().size

    fun clear() { file.delete() }

    // ─── Drain ────────────────────────────────────────────────────────────────

    private suspend fun drain(): Pair<Int, Int> {
        if (!isConnected()) return 0 to 0

        val items = load().toMutableList()
        if (items.isEmpty()) return 0 to 0

        val remaining = mutableListOf<QueueItem>()
        var synced = 0; var failed = 0

        for (item in items) {
            try {
                syncItem(item)
                synced++
            } catch (e: Exception) {
                item.retryCount++
                if (item.retryCount < MAX_RETRIES) remaining.add(item) else failed++
            }
        }

        save(remaining)
        return synced to failed
    }

    private suspend fun syncItem(item: QueueItem) {
        // In a real app, inject the API client and call the appropriate endpoint.
        // Example:
        // when (item.type) {
        //     "workout_session" -> api.saveSession("Bearer $token", Json.decodeFromString(item.payload))
        //     "meal_log"        -> api.logMeal("Bearer $token", Json.decodeFromString(item.payload))
        // }
        // For now, just a documented hook point.
        throw NotImplementedError("Wire API client in syncItem for type: ${item.type}")
    }

    // ─── Connectivity ─────────────────────────────────────────────────────────

    private fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun load(): List<QueueItem> = runCatching {
        if (file.exists()) Json.decodeFromString<List<QueueItem>>(file.readText()) else emptyList()
    }.getOrElse { emptyList() }

    private fun save(items: List<QueueItem>) {
        file.writeText(Json.encodeToString(items))
    }
}
