@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.formlogic.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formlogic.BuildConfig
import com.formlogic.components.GlowStatCard
import com.formlogic.models.scoreColor
import com.formlogic.services.*
import com.formlogic.store.CoachStore
import com.formlogic.viewmodels.AuthViewModel
import com.formlogic.viewmodels.WorkoutViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val BG0    = Color(0xFF090A11)
private val BG1    = Color(0xFF151529)
private val CARD   = Color(0xFF12131F)
private val CARD2  = Color(0xFF1A1B2E)
private val PURPLE = Color(0xFF6C63FF)
private val TEAL   = Color(0xFF43D9AD)
private val CORAL  = Color(0xFFFF6584)
private val AMBER  = Color(0xFFFFB547)
private val TXT1   = Color(0xFFF0F0FF)
private val TXT2   = Color(0xFF8080A0)
private val BORDER = Color(0xFF1E1F35)

// ═══════════════════════════════════════════════════════════════════════════════
// TODAY SCREEN  (landing dashboard)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TodayScreen(
    authVm:    AuthViewModel,
    workoutVm: WorkoutViewModel,
    onOpenPlans: () -> Unit,
    onOpenChat:  () -> Unit
) {
    val user    by authVm.user.collectAsStateWithLifecycle(null)
    val stats   by workoutVm.stats.collectAsStateWithLifecycle()
    val weekly  by workoutVm.weekly.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { workoutVm.loadStats(); workoutVm.loadWeeklyProgress() }

    val streak    = stats?.get("current_streak")?.jsonPrimitive?.content ?: "0"
    val avgForm   = stats?.get("avg_form_score")?.jsonPrimitive?.content ?: "0"
    val totalReps = stats?.get("total_reps")?.jsonPrimitive?.content ?: "0"
    val totalCal  = stats?.get("total_calories")?.jsonPrimitive?.content ?: "0"

    val hour     = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val greetEmoji = when { hour < 12 -> "☀️"; hour < 17 -> "💪"; else -> "🌙" }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BG0, BG1)))) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {

            // ── Hero header ──────────────────────────────────────────────────
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.radialGradient(listOf(PURPLE.copy(0.18f), Color.Transparent), radius = 600f))
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Text("$greeting $greetEmoji", color = TXT2, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                user?.name?.split(" ")?.firstOrNull() ?: "Athlete",
                                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TXT1
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("🔥 $streak day streak", AMBER)
                                Pill("$avgForm% form", PURPLE)
                            }
                        }
                        Box(
                            Modifier.size(52.dp)
                                .background(Brush.linearGradient(listOf(PURPLE, TEAL)), CircleShape)
                                .border(2.dp, PURPLE.copy(0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "A", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // ── 4-stat row ───────────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlowStatCard(streak,    "Day Streak", "🔥", AMBER,  Modifier.weight(1f))
                    GlowStatCard("$avgForm%", "Avg Form",  "🎯", PURPLE, Modifier.weight(1f))
                    GlowStatCard(totalReps, "Total Reps", "💪", TEAL,   Modifier.weight(1f))
                    GlowStatCard(totalCal,  "kcal",       "⚡", CORAL,  Modifier.weight(1f))
                }
            }

            // ── Coach action card ─────────────────────────────────────────────
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1A1040), Color(0xFF0D1A30))), RoundedCornerShape(20.dp))
                        .border(1.dp, PURPLE.copy(0.35f), RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 20.sp)
                            Text("Today's Priority", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TXT1)
                        }
                        Text(
                            "Hit your scheduled workout, then log post-workout protein within 60 min for optimal recovery and muscle synthesis.",
                            color = Color(0xFFB0B0D0), fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.horizontalGradient(listOf(PURPLE, Color(0xFF9D97FF))))
                                    .clickable(onClick = onOpenPlans),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Open Plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(PURPLE.copy(0.12f))
                                    .border(1.dp, PURPLE.copy(0.4f), RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenChat),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Ask Coach", color = Color(0xFF9D97FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // ── Weekly report card ────────────────────────────────────────────
            item { WeeklyReportCard(authVm) }

            // ── Weekly trend rows ─────────────────────────────────────────────
            val weeklyItems = weekly?.entries?.toList()?.sortedByDescending { it.key } ?: emptyList()
            if (weeklyItems.isNotEmpty()) {
                item {
                    Text("Weekly Trend", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TXT1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(weeklyItems.take(5)) { (week, payload) ->
                    val obj  = payload.jsonObject
                    val wkts = obj["workouts"]?.jsonPrimitive?.intOrNull ?: 0
                    val fs   = obj["avg_form_score"]?.jsonPrimitive?.floatOrNull ?: 0f
                    val fc   = scoreColor(fs)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                            .background(CARD, RoundedCornerShape(12.dp))
                            .border(1.dp, BORDER, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(week, fontWeight = FontWeight.SemiBold, color = TXT1, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$wkts sessions", color = TXT2, fontSize = 12.sp)
                            Box(Modifier.background(fc.copy(0.15f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text("${fs.toInt()}%", color = fc, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Pill(text: String, color: Color) {
    Box(
        Modifier.background(color.copy(0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COACH CHAT SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

private val QUICK_PROMPTS = listOf(
    "Adjust tomorrow's workout based on my week" to "🔧",
    "Explain my plateau and how to break it"     to "📊",
    "Fix my protein intake for this week"        to "🥩",
    "What should I focus on next week?"          to "🎯",
)

@Composable
fun CoachChatScreen(authVm: AuthViewModel) {
    val token    by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api       = remember { ApiClient.create(BuildConfig.BASE_URL) }
    val messages  = remember { mutableStateListOf<ChatMessageOut>() }
    var threadId by remember { mutableStateOf<String?>(null) }
    var prompt   by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf<String?>(null) }
    val scope     = rememberCoroutineScope()
    val pendingQ  = remember { mutableStateListOf<String>() }
    var streaming by remember { mutableStateOf<String?>(null) }
    val sseClient = remember { OkHttpClient.Builder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build() }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, streaming) {
        if (messages.isNotEmpty() || streaming != null)
            listState.animateScrollToItem(maxOf(0, messages.size))
    }

    suspend fun sendMsg(text: String, retried: Boolean = false) {
        if (token.isNullOrBlank() || text.isBlank()) return
        loading = true; error = null; streaming = "●  Thinking…"
        try {
            val streamed = withContext(Dispatchers.IO) {
                val sb  = StringBuilder()
                val esc = text.replace("\\","\\\\").replace("\"","\\\"")
                val body = """{"content":"$esc","thread_id":null,"context_tags":["weekly_report","active_plan","nutrition_week"]}"""
                val req = Request.Builder()
                    .url("${BuildConfig.BASE_URL.trimEnd('/')}/api/v1/ai/chat/stream")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "text/event-stream")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                sseClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("${resp.code}")
                    val src = resp.body?.source() ?: throw RuntimeException("Empty body")
                    while (!src.exhausted()) {
                        val line = src.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val chunk = line.removePrefix("data: ").trim()
                            if (chunk == "[DONE]") break
                            if (chunk.startsWith("{") || chunk.startsWith("\"")) {
                                runCatching { sb.append(kotlinx.serialization.json.Json.decodeFromString<String>(chunk)) }
                                    .onFailure { sb.append(chunk) }
                            } else if (chunk.isNotBlank()) sb.append(chunk)
                            streaming = sb.toString().trim().ifBlank { "●  Thinking…" }
                        }
                    }
                }
                sb.toString().trim()
            }
            if (streamed.isNotBlank()) {
                messages.add(ChatMessageOut(role = "assistant", content = streamed))
            } else {
                val res = withContext(Dispatchers.IO) {
                    api.coachChat("Bearer $token", ChatMessageRequest(content = text, thread_id = threadId, context_tags = listOf("weekly_report","active_plan","nutrition_week")))
                }
                if (res.code() == 401 && !retried) { authVm.refreshSession(); streaming = null; loading = false; sendMsg(text, true); return }
                res.body()?.data?.let { d -> threadId = d.thread_id; messages.add(d.message); CoachStore.publish(d.message.structured) }
                    ?: run { error = "Coach unavailable. Queued."; if (!retried) pendingQ.add(text) }
            }
        } catch (e: Exception) {
            if (e.message?.contains("401") == true && !retried) { authVm.refreshSession(); streaming = null; loading = false; sendMsg(text, true); return }
            error = "Network error. Message queued."; if (!retried) pendingQ.add(text)
        }
        streaming = null; loading = false
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BG0, BG1)))) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Box(
                Modifier.fillMaxWidth()
                    .background(BG0.copy(0.97f))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("AI Coach", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TXT1)
                    Text("Personalised guidance from your training data", fontSize = 12.sp, color = TXT2)
                }
            }

            // Quick prompt chips
            if (messages.isEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(QUICK_PROMPTS) { (text, emoji) ->
                        Box(
                            Modifier
                                .background(CARD2, RoundedCornerShape(20.dp))
                                .border(1.dp, BORDER, RoundedCornerShape(20.dp))
                                .clickable { prompt = text; scope.launch { messages.add(ChatMessageOut(role = "user", content = text)); val t = text; prompt = ""; sendMsg(t) } }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, fontSize = 14.sp)
                                Text(text, fontSize = 12.sp, color = TXT1, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Messages
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { m ->
                    val isUser = m.role == "user"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isUser) {
                            Box(
                                Modifier.size(32.dp).background(Brush.linearGradient(listOf(PURPLE, TEAL)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text("FL", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            Modifier.widthIn(max = 300.dp)
                                .background(
                                    if (isUser) Brush.horizontalGradient(listOf(PURPLE, Color(0xFF9D97FF)))
                                    else Brush.horizontalGradient(listOf(CARD, CARD2)),
                                    RoundedCornerShape(
                                        topStart = if (isUser) 16.dp else 4.dp,
                                        topEnd = if (isUser) 4.dp else 16.dp,
                                        bottomStart = 16.dp, bottomEnd = 16.dp
                                    )
                                )
                                .then(if (!isUser) Modifier.border(1.dp, BORDER, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)) else Modifier)
                                .padding(12.dp)
                        ) {
                            if (!isUser && m.structured != null) {
                                CoachBubbleContent(m.structured)
                            } else {
                                Text(m.content, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                // Streaming bubble
                streaming?.let { txt ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Box(Modifier.size(32.dp).background(Brush.linearGradient(listOf(PURPLE, TEAL)), CircleShape), contentAlignment = Alignment.Center) {
                                Text("FL", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.widthIn(max = 300.dp)
                                    .background(CARD, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                    .border(1.dp, BORDER, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                    .padding(12.dp)
                            ) {
                                TypingText(txt)
                            }
                        }
                    }
                }
            }

            // Error bar
            error?.let { err ->
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFFF4757).copy(0.1f)).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 13.sp)
                    Text(err, color = Color(0xFFFF4757), fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }

            // Input row
            Row(
                Modifier.fillMaxWidth()
                    .background(BG0.copy(0.97f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask your coach…", color = TXT2, fontSize = 14.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = BORDER, focusedBorderColor = PURPLE,
                        focusedTextColor = TXT1, unfocusedTextColor = TXT1,
                        focusedContainerColor = CARD2, unfocusedContainerColor = CARD2
                    ),
                    maxLines = 4
                )
                Box(
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(if (loading || prompt.isBlank()) CARD2 else Brush.linearGradient(listOf(PURPLE, Color(0xFF9D97FF))))
                        .clickable(enabled = !loading && prompt.isNotBlank()) {
                            val text = prompt.trim()
                            prompt = ""
                            messages.add(ChatMessageOut(role = "user", content = text))
                            scope.launch { sendMsg(text) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // Retry queue
    LaunchedEffect(token, pendingQ.size) {
        while (pendingQ.isNotEmpty()) {
            delay(15_000)
            val next = pendingQ.firstOrNull() ?: continue
            if (loading || token.isNullOrBlank()) continue
            val before = messages.size
            sendMsg(next, retried = false)
            if (messages.size > before) pendingQ.removeAt(0)
        }
    }
}

@Composable private fun TypingText(text: String) {
    val alpha by animateFloatAsState(1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "blink")
    Text(text, color = TXT1.copy(if (text == "●  Thinking…") alpha else 1f), fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable private fun CoachBubbleContent(data: JsonObject) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data["diagnosis"]?.jsonPrimitive?.contentOrNull?.let { d ->
            if (d.isNotBlank()) Text(d, color = TXT1, fontSize = 14.sp, lineHeight = 20.sp)
        }
        CoachSection(data, "weekly_actions", "This Week", TEAL)
        CoachSection(data, "nutrition_corrections", "Nutrition", AMBER)
        CoachSection(data, "recovery_corrections", "Recovery", CORAL)
    }
}

@Composable private fun CoachSection(data: JsonObject, key: String, label: String, color: Color) {
    val items = data[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.take(3) ?: return
    if (items.isEmpty()) return
    Text(label, fontWeight = FontWeight.Bold, color = color, fontSize = 11.sp, letterSpacing = 1.sp)
    items.forEach {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("•", color = color, fontSize = 13.sp)
            Text(it, color = Color(0xFFD0D0F0), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PLAN INTELLIGENCE SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PlanIntelligenceScreen(authVm: AuthViewModel) {
    val token by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api    = remember { ApiClient.create(BuildConfig.BASE_URL) }
    var plans by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var diff by remember { mutableStateOf<List<String>>(emptyList()) }
    var regenerating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coach by CoachStore.latestStructured.collectAsStateWithLifecycle(null)

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val res = withContext(Dispatchers.IO) { api.getPlans("Bearer $token") }
            if (res.isSuccessful) { plans = res.body()?.data ?: emptyList(); selectedId = plans.firstOrNull()?.get("_id")?.jsonPrimitive?.content }
        }.onFailure { error = it.message }
    }

    suspend fun refreshDiff() {
        val id = selectedId ?: return; if (token.isNullOrBlank()) return
        runCatching { val res = withContext(Dispatchers.IO) { api.getPlanDiff("Bearer $token", id) }; if (res.isSuccessful) diff = res.body()?.data?.changes ?: emptyList() }
    }
    LaunchedEffect(selectedId) { refreshDiff() }
    LaunchedEffect(regenerating) {
        if (!regenerating) return@LaunchedEffect
        val id = selectedId ?: run { regenerating = false; return@LaunchedEffect }
        if (token.isNullOrBlank()) { regenerating = false; return@LaunchedEffect }
        runCatching {
            val res = withContext(Dispatchers.IO) { api.regeneratePlan("Bearer $token", RegeneratePlanRequest(plan_id = id, reason = "manual")) }
            if (res.isSuccessful) {
                res.body()?.data?.get("new_plan_id")?.jsonPrimitive?.content?.let { selectedId = it }
                plans = withContext(Dispatchers.IO) { api.getPlans("Bearer $token") }.body()?.data ?: emptyList()
                refreshDiff()
            } else error = res.body()?.error ?: "Regeneration failed"
        }.onFailure { error = it.message }
        regenerating = false
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BG0, BG1)))) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Plan Intelligence", fontWeight = FontWeight.Bold, color = TXT1) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f))
                )
            }
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                item { Text("Select a plan to analyse or regenerate it based on your recent form + completion data.", fontSize = 13.sp, color = TXT2, lineHeight = 18.sp) }

                items(plans.take(6)) { p ->
                    val id     = p["_id"]?.jsonPrimitive?.content ?: return@items
                    val sel    = selectedId == id
                    val active = p["is_active"]?.jsonPrimitive?.booleanOrNull ?: false
                    val goal   = p["goal"]?.jsonPrimitive?.content ?: ""
                    val accent = when (goal) { "muscle_gain" -> CORAL; "weight_loss" -> AMBER; "endurance" -> TEAL; else -> PURPLE }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (sel) accent.copy(0.12f) else CARD, RoundedCornerShape(14.dp))
                            .border(1.5.dp, if (sel) accent.copy(0.5f) else BORDER, RoundedCornerShape(14.dp))
                            .clickable { selectedId = id }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(42.dp).background(accent.copy(0.15f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Text(when (goal) { "muscle_gain" -> "💪"; "weight_loss" -> "🔥"; "endurance" -> "🏃"; else -> "✨" }, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p["name"]?.jsonPrimitive?.content ?: "Plan", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 14.sp)
                            Text("Week ${p["current_week"]?.jsonPrimitive?.content ?: "1"} · ${p["completion_percent"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0}% done", fontSize = 12.sp, color = TXT2)
                        }
                        if (active) Box(Modifier.background(TEAL.copy(0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text("ACTIVE", fontSize = 9.sp, color = TEAL, fontWeight = FontWeight.ExtraBold)
                        }
                        if (sel) Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                }

                if (selectedId != null) item {
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (regenerating) CARD2 else Brush.horizontalGradient(listOf(PURPLE, Color(0xFF9D97FF))))
                            .clickable(enabled = !regenerating) { regenerating = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (regenerating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(if (regenerating) "Regenerating…" else "Regenerate This Week", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (diff.isNotEmpty()) item {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(TEAL.copy(0.08f), RoundedCornerShape(14.dp))
                            .border(1.dp, TEAL.copy(0.25f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Why It Changed", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 14.sp)
                        diff.forEach {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("→", color = TEAL, fontSize = 13.sp)
                                Text(it, color = Color(0xFFD0D0F0), fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }

                coach?.get("plan_updates")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.let { updates ->
                    if (updates.isNotEmpty()) item {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(PURPLE.copy(0.08f), RoundedCornerShape(14.dp))
                                .border(1.dp, PURPLE.copy(0.25f), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("🤖 Coach Recommendations", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 13.sp)
                            updates.take(3).forEach { Text("• $it", color = Color(0xFFD0D0F0), fontSize = 13.sp) }
                        }
                    }
                }

                error?.let { item { Text(it, color = Color(0xFFFF4757), fontSize = 13.sp, modifier = Modifier.padding(4.dp)) } }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// WEEKLY REPORT CARD (used in TodayScreen)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun WeeklyReportCard(authVm: AuthViewModel) {
    val token   by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api      = remember { ApiClient.create(BuildConfig.BASE_URL) }
    var report  by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        loading = true
        runCatching { val res = withContext(Dispatchers.IO) { api.weeklyReport("Bearer $token") }; if (res.isSuccessful) report = res.body()?.data }
        loading = false
    }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(CARD, RoundedCornerShape(16.dp))
            .border(1.dp, BORDER, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = PURPLE, strokeWidth = 2.dp)
                Text("Building weekly coach report…", color = TXT2, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📋", fontSize = 16.sp)
                    Text("Weekly AI Report", fontWeight = FontWeight.ExtraBold, color = TXT1, fontSize = 16.sp)
                }
                val kpis = report?.get("kpis")?.jsonObject
                if (kpis != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf(
                            kpis["workouts"]?.jsonPrimitive?.content to "Workouts",
                            kpis["total_reps"]?.jsonPrimitive?.content to "Reps",
                            "${kpis["adherence_pct"]?.jsonPrimitive?.content ?: "0"}%" to "Adherence",
                            "${kpis["avg_form_score"]?.jsonPrimitive?.content ?: "0"}%" to "Form"
                        ).forEach { (v, l) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v ?: "0", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PURPLE)
                                Text(l, fontSize = 10.sp, color = TXT2)
                            }
                        }
                    }
                }
                val structured = report?.get("coach_structured")?.jsonObject
                if (structured != null) {
                    CoachStore.publish(structured)
                    structured["diagnosis"]?.jsonPrimitive?.contentOrNull?.let { d ->
                        if (d.isNotBlank()) {
                            HorizontalDivider(color = BORDER)
                            Text(d, color = Color(0xFFD0D0F0), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                } else {
                    report?.get("coach_summary")?.jsonPrimitive?.contentOrNull?.let { s ->
                        if (s.isNotBlank()) Text(s, color = Color(0xFFD0D0F0), fontSize = 13.sp, lineHeight = 18.sp)
                    } ?: run {
                        if (!loading) Text("Complete a workout this week to get your personalised AI report.", color = TXT2, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
