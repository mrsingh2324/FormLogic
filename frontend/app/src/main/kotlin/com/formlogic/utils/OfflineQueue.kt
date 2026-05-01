package com.formlogic.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
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
 *
 * Usage:
 *   1. Call `setSyncHandler { item -> ... }` once at startup (e.g. in MainActivity)
 *      to wire up the Retrofit API client and auth token retrieval.
 *   2. Call `init()` to start the background drain loop.
 *   3. Call `enqueue(type, jsonPayload)` anywhere to buffer an offline operation.
 *
 * Without a sync handler registered, items are safely kept on disk indefinitely
 * and will be drained once a handler is wired in.
 */
class OfflineQueue(private val context: Context) {

    private val tag = "OfflineQueue"
    private val file = File(context.filesDir, "offline_queue.json")
    private val MAX_RETRIES = 10        // generous — keeps items alive across restarts
    private val POLL_INTERVAL_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: kotlinx.coroutines.Job? = null

    /** Suspend lambda called for each item. Throw to signal failure; return normally to mark synced. */
    private var syncHandler: (suspend (QueueItem) -> Unit)? = null

    /** Register the API sync callback. Call this once after the Retrofit client is ready. */
    fun setSyncHandler(handler: suspend (QueueItem) -> Unit) {
        syncHandler = handler
    }

    // ─── Init / destroy ───────────────────────────────────────────────────────

    fun init() {
        pollJob = scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                drain()
            }
        }
        scope.launch { drain() }
    }

    fun destroy() { pollJob?.cancel() }

    // ─── Queue operations ─────────────────────────────────────────────────────

    fun enqueue(type: String, payload: String) {
        val items = load().toMutableList()
        items.add(
            QueueItem(
                id = "${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}",
                type = type,
                payload = payload,
            )
        )
        save(items)
        Log.d(tag, "Enqueued offline item type=$type (queue size=${items.size})")
        // Attempt drain immediately — will no-op if no handler or no connectivity
        scope.launch { drain() }
    }

    fun pendingCount(): Int = load().size

    fun clear() { file.delete() }

    // ─── Drain ────────────────────────────────────────────────────────────────

    private suspend fun drain(): Pair<Int, Int> {
        val handler = syncHandler ?: run {
            // No sync handler registered yet — keep all items, don't lose anything
            val count = pendingCount()
            if (count > 0) Log.d(tag, "drain skipped — no sync handler registered ($count items waiting)")
            return 0 to 0
        }

        if (!isConnected()) return 0 to 0

        val items = load().toMutableList()
        if (items.isEmpty()) return 0 to 0

        Log.d(tag, "drain starting — ${items.size} items")
        val remaining = mutableListOf<QueueItem>()
        var synced = 0
        var failed = 0

        for (item in items) {
            try {
                handler(item)
                synced++
                Log.d(tag, "synced item id=${item.id} type=${item.type}")
            } catch (e: Exception) {
                item.retryCount++
                if (item.retryCount < MAX_RETRIES) {
                    remaining.add(item)
                    Log.w(tag, "sync failed for type=${item.type} retry=${item.retryCount}/${MAX_RETRIES}: ${e.message}")
                } else {
                    failed++
                    Log.e(tag, "dropping item id=${item.id} type=${item.type} after $MAX_RETRIES retries: ${e.message}")
                }
            }
        }

        save(remaining)
        Log.d(tag, "drain done — synced=$synced failed=$failed remaining=${remaining.size}")
        return synced to failed
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
