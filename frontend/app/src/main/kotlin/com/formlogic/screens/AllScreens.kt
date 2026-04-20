@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.formlogic.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formlogic.BuildConfig
import com.formlogic.components.*
import com.formlogic.models.*
import com.formlogic.services.*
import com.formlogic.store.CoachStore
import com.formlogic.viewmodels.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// ─── Shared colours ───────────────────────────────────────────────────────────
private val BG0     = Color(0xFF090A11)
private val BG1     = Color(0xFF151529)
private val CARD    = Color(0xFF12131F)
private val CARD2   = Color(0xFF1A1B2E)
private val PURPLE  = Color(0xFF6C63FF)
private val TEAL    = Color(0xFF43D9AD)
private val CORAL   = Color(0xFFFF6584)
private val AMBER   = Color(0xFFFFB547)
private val TXT1    = Color(0xFFF0F0FF)
private val TXT2    = Color(0xFF8080A0)
private val BORDER  = Color(0xFF1E1F35)

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun ScreenBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(BG0, BG1))),
        content = { content() }
    )
}

@Composable
private fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(CARD, RoundedCornerShape(16.dp))
            .border(1.dp, BORDER, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String, action: String = "", onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TXT1)
        if (action.isNotEmpty()) TextButton(onAction) {
            Text(action, color = PURPLE, fontSize = 12.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AUTH SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

private enum class AuthTab { LOGIN, REGISTER, FORGOT }

@Composable
fun AuthScreen(vm: AuthViewModel, onSuccess: () -> Unit, onRegisterSuccess: () -> Unit) {
    var tab         by remember { mutableStateOf(AuthTab.LOGIN) }
    var name        by remember { mutableStateOf("") }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var showPw      by remember { mutableStateOf(false) }
    var consent     by remember { mutableStateOf(false) }
    var forgotSent  by remember { mutableStateOf(false) }
    val state       by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (state) {
            is AuthState.LoggedIn -> onSuccess()
            is AuthState.Success  -> if (tab == AuthTab.REGISTER) onRegisterSuccess()
            else -> {}
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(BG0, Color(0xFF0D0918)))),
        contentAlignment = Alignment.Center
    ) {
        // Subtle glow orb
        Box(
            Modifier.size(320.dp).offset(y = (-60).dp)
                .background(Brush.radialGradient(listOf(PURPLE.copy(0.12f), Color.Transparent)))
        )

        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Logo
            Box(
                Modifier.size(72.dp)
                    .background(Brush.linearGradient(listOf(PURPLE, TEAL)), CircleShape)
                    .border(2.dp, PURPLE.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("FL", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(4.dp))
            Text("FormLogic", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TXT1)
            Text("Your AI gym trainer, always on", fontSize = 14.sp, color = TXT2)
            Spacer(Modifier.height(4.dp))

            if (tab != AuthTab.FORGOT) {
                // Tab switcher
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF1A1A27), RoundedCornerShape(14.dp))
                        .padding(4.dp)
                ) {
                    listOf(AuthTab.LOGIN to "Sign In", AuthTab.REGISTER to "Sign Up").forEach { (t, l) ->
                        Box(
                            Modifier.weight(1f)
                                .background(if (tab == t) PURPLE else Color.Transparent, RoundedCornerShape(11.dp))
                                .clickable { tab = t; vm.resetState() }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(l, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            when (tab) {
                AuthTab.FORGOT -> {
                    TextButton({ tab = AuthTab.LOGIN }) { Text("← Back", color = TXT2) }
                    Text("Reset Password", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TXT1)
                    Text("We'll send a reset link to your email.", color = TXT2, fontSize = 14.sp)
                    if (forgotSent) {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(TEAL.copy(0.12f), RoundedCornerShape(12.dp))
                                .border(1.dp, TEAL.copy(0.3f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("✅", fontSize = 16.sp)
                            Text("If that email is registered, a reset link has been sent.", color = TEAL, fontSize = 13.sp)
                        }
                    } else {
                        AField(email, { email = it }, "Email", KeyboardType.Email)
                        if (state is AuthState.Error) ErrPill((state as AuthState.Error).message)
                        ABtn("Send Reset Link", state is AuthState.Loading) {
                            vm.forgotPassword(email) { _, _ -> forgotSent = true }
                        }
                    }
                }
                AuthTab.LOGIN -> {
                    AField(email, { email = it }, "Email", KeyboardType.Email)
                    APwField(password, { password = it }, showPw, { showPw = !showPw })
                    TextButton({ tab = AuthTab.FORGOT }, Modifier.align(Alignment.End)) {
                        Text("Forgot password?", color = PURPLE, fontSize = 13.sp)
                    }
                    if (state is AuthState.Error) ErrPill((state as AuthState.Error).message)
                    ABtn("Sign In", state is AuthState.Loading) { vm.login(email.trim(), password) }
                    Divider(color = BORDER)
                    FootLink("Don't have an account? ", "Sign Up") { tab = AuthTab.REGISTER; vm.resetState() }
                }
                AuthTab.REGISTER -> {
                    AField(name, { name = it }, "Full Name")
                    AField(email, { email = it }, "Email", KeyboardType.Email)
                    APwField(password, { password = it }, showPw, { showPw = !showPw }, "Password (min 8 chars)")
                    Row(
                        Modifier.fillMaxWidth()
                            .background(CARD2, RoundedCornerShape(12.dp))
                            .border(1.dp, if (consent) TEAL.copy(0.4f) else BORDER, RoundedCornerShape(12.dp))
                            .clickable { consent = !consent }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier.size(20.dp)
                                .background(if (consent) TEAL.copy(0.2f) else Color.Transparent, RoundedCornerShape(5.dp))
                                .border(1.5.dp, if (consent) TEAL else TXT2, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (consent) Text("✓", color = TEAL, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            "I accept the Privacy Policy & Terms. FormLogic processes your camera on-device only.",
                            fontSize = 12.sp, color = TXT2, lineHeight = 18.sp, modifier = Modifier.weight(1f)
                        )
                    }
                    if (state is AuthState.Error) ErrPill((state as AuthState.Error).message)
                    ABtn("Create Account", state is AuthState.Loading, !consent) {
                        vm.register(name.trim(), email.trim(), password)
                    }
                    Divider(color = BORDER)
                    FootLink("Already have an account? ", "Sign In") { tab = AuthTab.LOGIN; vm.resetState() }
                }
            }

            // Feature teasers shown on register tab
            if (tab == AuthTab.REGISTER) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "📸 Real-time form correction via camera",
                        "🤖 AI coach that learns from your data",
                        "📈 Adaptive weekly plans"
                    ).forEach { feat ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(PURPLE.copy(0.07f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(feat.take(2), fontSize = 15.sp)
                            Text(feat.drop(2), fontSize = 13.sp, color = TXT2)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AField(v: String, onV: (String) -> Unit, label: String, kb: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        v, onV, label = { Text(label, color = TXT2) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = kb, autoCorrect = false),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = BORDER, focusedBorderColor = PURPLE,
            focusedTextColor = TXT1, unfocusedTextColor = TXT1,
            focusedContainerColor = CARD2, unfocusedContainerColor = CARD2
        )
    )
}

@Composable private fun APwField(v: String, onV: (String) -> Unit, show: Boolean, onToggle: () -> Unit, label: String = "Password") {
    OutlinedTextField(
        v, onV, label = { Text(label, color = TXT2) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onToggle) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TXT2) } },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = BORDER, focusedBorderColor = PURPLE,
            focusedTextColor = TXT1, unfocusedTextColor = TXT1,
            focusedContainerColor = CARD2, unfocusedContainerColor = CARD2
        )
    )
}

@Composable private fun ABtn(label: String, loading: Boolean, disabled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(
                if (disabled || loading) Brush.horizontalGradient(listOf(CARD2, CARD2))
                else Brush.horizontalGradient(listOf(PURPLE, Color(0xFF9D97FF)))
            )
            .clickable(enabled = !loading && !disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(label, color = if (disabled) TXT2 else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun ErrPill(msg: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFFFF4757).copy(0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFF4757).copy(0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("⚠️", fontSize = 14.sp)
        Text(msg, color = Color(0xFFFF4757), fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable private fun FootLink(text: String, link: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(text, color = TXT2, fontSize = 13.sp)
        Text(link, color = PURPLE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onClick))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

private data class QuickEx(val id: String, val name: String, val emoji: String, val accent: Color, val muscle: String)
private val QUICK_EXERCISES = listOf(
    QuickEx("squat",    "Squat",    "🦵", PURPLE,             "Legs"),
    QuickEx("pushup",   "Push-Up",  "💪", CORAL,              "Chest"),
    QuickEx("lunge",    "Lunge",    "🏃", TEAL,               "Glutes"),
    QuickEx("plank",    "Plank",    "🧘", AMBER,              "Core"),
    QuickEx("crunch",   "Crunch",   "🔥", Color(0xFF9B59B6),  "Abs"),
    QuickEx("burpee",   "Burpee",   "⚡", Color(0xFFE74C3C),  "Full Body"),
    QuickEx("pullup",   "Pull-Up",  "🔝", Color(0xFF00BCD4),  "Back"),
    QuickEx("rdl",      "Deadlift", "🏋️", Color(0xFFFF9800), "Hamstrings"),
    QuickEx("mountain_climber", "Climber", "🏔️", Color(0xFF26A69A), "Core+Cardio"),
    QuickEx("glute_bridge", "Glute Bridge", "🌉", Color(0xFFEC407A), "Glutes"),
)

@Composable
fun HomeScreen(
    authVm: AuthViewModel,
    workoutVm: WorkoutViewModel,
    onStartWorkout: (String, String) -> Unit,
    onHistory: () -> Unit,
    onPlans: () -> Unit
) {
    val user  by authVm.user.collectAsStateWithLifecycle(null)
    val stats by workoutVm.stats.collectAsStateWithLifecycle()
    val hist  by workoutVm.history.collectAsStateWithLifecycle()
    var showGuide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { workoutVm.loadStats(); workoutVm.loadHistory() }
    if (showGuide) FirstWorkoutGuide({ showGuide = false }) { onStartWorkout("squat", "Bodyweight Squat") }

    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when { hour < 12 -> "Good morning ☀️"; hour < 17 -> "Good afternoon 💪"; else -> "Good evening 🌙" }

    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {

            // ── Hero ─────────────────────────────────────────────────────────
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.radialGradient(listOf(PURPLE.copy(0.18f), Color.Transparent), radius = 700f))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(greeting, color = TXT2, fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                user?.name?.split(" ")?.firstOrNull() ?: "Athlete",
                                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TXT1
                            )
                        }
                        Box(
                            Modifier.size(50.dp)
                                .background(Brush.linearGradient(listOf(PURPLE, TEAL)), CircleShape)
                                .border(2.dp, PURPLE.copy(0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // ── Stats row ─────────────────────────────────────────────────────
            item {
                stats?.let { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlowStatCard(s["current_streak"]?.jsonPrimitive?.content ?: "0", "Streak",   "🔥", AMBER,  Modifier.weight(1f))
                        GlowStatCard("${s["avg_form_score"]?.jsonPrimitive?.content ?: "0"}%", "Form", "🎯", PURPLE, Modifier.weight(1f))
                        GlowStatCard(s["total_workouts"]?.jsonPrimitive?.content ?: "0", "Sessions", "🏋️", TEAL,   Modifier.weight(1f))
                        GlowStatCard(s["total_calories"]?.jsonPrimitive?.content ?: "0", "kcal",     "⚡", CORAL,  Modifier.weight(1f))
                    }
                } ?: run {
                    // Skeleton placeholders
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(4) {
                            Box(Modifier.weight(1f).height(72.dp).background(CARD, RoundedCornerShape(14.dp)))
                        }
                    }
                }
            }

            // ── Quick Start ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Quick Start", "Setup Guide") { showGuide = true }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(QUICK_EXERCISES) { ex ->
                        Box(
                            Modifier
                                .size(width = 90.dp, height = 108.dp)
                                .background(ex.accent.copy(0.1f), RoundedCornerShape(18.dp))
                                .border(1.dp, ex.accent.copy(0.3f), RoundedCornerShape(18.dp))
                                .clickable { onStartWorkout(ex.id, ex.name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(ex.emoji, fontSize = 30.sp)
                                Text(ex.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ex.accent, textAlign = TextAlign.Center, lineHeight = 14.sp)
                                Box(
                                    Modifier.background(ex.accent.copy(0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(ex.muscle, fontSize = 8.sp, color = ex.accent)
                                }
                            }
                        }
                    }
                }
            }

            // ── Recent Workouts ───────────────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }

            if (hist.isNotEmpty()) {
                item { SectionLabel("Recent Workouts", "See All →", onHistory) }
                items(hist.take(4)) { WorkoutHistoryRow(it) }
            } else {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .background(CARD, RoundedCornerShape(18.dp))
                            .border(1.dp, BORDER, RoundedCornerShape(18.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🏋️", fontSize = 44.sp)
                            Text("Start your first workout!", fontWeight = FontWeight.ExtraBold, color = TXT1, fontSize = 16.sp)
                            Text("Tap any exercise above to begin with live AI form coaching.", fontSize = 13.sp, color = TXT2, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }
                    }
                }
            }

            // ── AI Plan CTA ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1A1040), Color(0xFF0D1A40))), RoundedCornerShape(20.dp))
                        .border(1.dp, PURPLE.copy(0.4f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onPlans)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("AI Training Plan", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TXT1)
                            Text("Adapts to your form every week", fontSize = 13.sp, color = Color(0xFF9D97FF), modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(Modifier.size(40.dp).background(PURPLE.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowForward, null, tint = Color(0xFF9D97FF))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun WorkoutHistoryRow(session: JsonObject) {
    val date  = session["date"]?.jsonPrimitive?.content?.take(10) ?: ""
    val reps  = session["total_reps"]?.jsonPrimitive?.content ?: "0"
    val dur   = session["duration"]?.jsonPrimitive?.content?.toIntOrNull()?.let { "%d:%02d".format(it/60, it%60) } ?: ""
    val score = session["avg_form_score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
    val cal   = session["calories_burned"]?.jsonPrimitive?.content?.let { if (it != "0") " · ${it}kcal" else "" } ?: ""
    val color = scoreColor(score)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(CARD, RoundedCornerShape(14.dp))
            .border(1.dp, BORDER, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(46.dp).background(color.copy(0.12f), CircleShape).border(1.dp, color.copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
            Text("${score.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
        Column(Modifier.weight(1f)) {
            Text(date, fontWeight = FontWeight.SemiBold, color = TXT1, fontSize = 14.sp)
            Text("$reps reps · $dur$cal", fontSize = 12.sp, color = TXT2)
        }
        Box(Modifier.background(color.copy(0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(scoreLabel(score), fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// WORKOUTS / HISTORY SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun WorkoutsScreen(vm: WorkoutViewModel, onStart: () -> Unit) {
    val hist    by vm.history.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadHistory() }
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("History", fontWeight = FontWeight.Bold, color = TXT1) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f)),
                    actions = {
                        IconButton(onStart) {
                            Box(Modifier.size(38.dp).background(PURPLE, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, "New", tint = Color.White)
                            }
                        }
                    }
                )
            }
        ) { pad ->
            if (loading) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PURPLE)
                }
            } else if (hist.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🏋️", fontSize = 64.sp)
                        Text("No workouts logged yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TXT1)
                        Text("Your completed sessions will appear here", color = TXT2, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(PURPLE, Color(0xFF9D97FF))))
                                .clickable(onClick = onStart)
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Text("Start First Workout", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                    // Summary header
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val totalSessions = hist.size
                            val avgScore = if (hist.isNotEmpty())
                                hist.mapNotNull { it["avg_form_score"]?.jsonPrimitive?.floatOrNull }.let { if (it.isEmpty()) 0f else it.average().toFloat() }
                            else 0f
                            GlowStatCard("$totalSessions", "Total Sessions", "🏋️", TEAL, Modifier.weight(1f))
                            GlowStatCard("${avgScore.toInt()}%", "Avg Form", "🎯", PURPLE, Modifier.weight(1f))
                        }
                    }
                    items(hist) { WorkoutHistoryRow(it) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROGRESS SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProgressScreen(vm: WorkoutViewModel, authVm: AuthViewModel) {
    val stats   by vm.stats.collectAsStateWithLifecycle()
    val token   by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api      = remember { ApiClient.create(BuildConfig.BASE_URL) }
    var weekly  by remember { mutableStateOf<JsonObject?>(null) }
    var monthly by remember { mutableStateOf<JsonObject?>(null) }
    val coach   by CoachStore.latestStructured.collectAsStateWithLifecycle(null)
    LaunchedEffect(Unit) { vm.loadStats() }
    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        runCatching { weekly  = api.weeklyReport ("Bearer $token").body()?.data }
        runCatching { monthly = api.monthlyReport("Bearer $token").body()?.data }
    }
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Progress", fontWeight = FontWeight.Bold, color = TXT1) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f))
                )
            }
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // All-time stats
                stats?.let { s ->
                    item {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowStatCard(s["total_workouts"]?.jsonPrimitive?.content  ?: "0",  "Workouts",   "🏋️", TEAL,   Modifier.weight(1f))
                            GlowStatCard(s["total_reps"]?.jsonPrimitive?.content      ?: "0",  "Total Reps", "💪", PURPLE, Modifier.weight(1f))
                            GlowStatCard("${s["avg_form_score"]?.jsonPrimitive?.content ?: "0"}%", "Avg Form","🎯", AMBER,  Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowStatCard(s["current_streak"]?.jsonPrimitive?.content  ?: "0",  "Day Streak", "🔥", CORAL,  Modifier.weight(1f))
                            GlowStatCard(s["total_calories"]?.jsonPrimitive?.content  ?: "0",  "Total kcal", "⚡", Color(0xFF9B59B6), Modifier.weight(1f))
                            GlowStatCard(s["total_duration"]?.jsonPrimitive?.intOrNull?.let { "${it/60}m" } ?: "0m", "Total Time", "⏱", TEAL, Modifier.weight(1f))
                        }
                    }
                }

                // Coach updates pill
                coach?.get("progress_updates")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.let { updates ->
                    if (updates.isNotEmpty()) item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .background(PURPLE.copy(0.08f), RoundedCornerShape(16.dp))
                                .border(1.dp, PURPLE.copy(0.25f), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖", fontSize = 16.sp)
                                Text("Coach Progress Update", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 14.sp)
                            }
                            updates.take(3).forEach { Text("• $it", color = Color(0xFFD0D0F0), fontSize = 13.sp, lineHeight = 18.sp) }
                        }
                    }
                }

                // Weekly report
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .background(CARD, RoundedCornerShape(16.dp))
                            .border(1.dp, BORDER, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("This Week", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TXT1)
                        weekly?.get("kpis")?.jsonObject?.let { kpis ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                ProgressKPI("${kpis["adherence_pct"]?.jsonPrimitive?.content ?: "0"}%", "Adherence", TEAL)
                                ProgressKPI(kpis["workouts"]?.jsonPrimitive?.content ?: "0", "Workouts", PURPLE)
                                ProgressKPI("${kpis["avg_form_score"]?.jsonPrimitive?.content ?: "0"}%", "Avg Form", AMBER)
                                ProgressKPI(kpis["total_reps"]?.jsonPrimitive?.content ?: "0", "Reps", CORAL)
                            }
                        } ?: Text("Complete a workout to see your weekly stats.", color = TXT2, fontSize = 13.sp)
                    }
                }

                // Monthly sparkline trend
                monthly?.get("weekly_trends")?.jsonArray?.let { trends ->
                    if (trends.isNotEmpty()) item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .background(CARD, RoundedCornerShape(16.dp))
                                .border(1.dp, BORDER, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Form Trend (4 Weeks)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TXT1)
                            val vals = trends.map { it.jsonObject["avg_form_score"]?.jsonPrimitive?.floatOrNull ?: 0f }
                            FormSparkline(vals, PURPLE, Modifier.fillMaxWidth().height(72.dp))
                            trends.forEach { wk ->
                                val row = wk.jsonObject
                                val fs = row["avg_form_score"]?.jsonPrimitive?.floatOrNull ?: 0f
                                val fc = scoreColor(fs)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(CARD2, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(row["week"]?.jsonPrimitive?.content ?: "", color = TXT2, fontSize = 13.sp)
                                    Text("${row["workouts"]?.jsonPrimitive?.content ?: "0"} sessions", color = TXT2, fontSize = 12.sp)
                                    Box(Modifier.background(fc.copy(0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Text("${fs.toInt()}%", color = fc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ProgressKPI(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = TXT2)
    }
}

@Composable private fun FormSparkline(values: List<Float>, color: Color, modifier: Modifier) {
    if (values.isEmpty()) return
    Canvas(modifier) {
        val min = values.minOrNull() ?: 0f; val max = values.maxOrNull() ?: 1f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = if (values.size <= 1) size.width else size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, size.height - ((v - min) / range * size.height)) }
        val path = Path(); pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, color.copy(0.25f), style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { drawCircle(color, 5f, it); drawCircle(Color.White, 2.5f, it) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PLANS SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PlansScreen(authVm: AuthViewModel) {
    val token   by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api      = remember { ApiClient.create(BuildConfig.BASE_URL) }
    val scope    = rememberCoroutineScope()
    var plans   by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var nameInput  by remember { mutableStateOf("My Plan") }
    var goalInput  by remember { mutableStateOf("weight_loss") }
    var levelInput by remember { mutableStateOf("beginner") }
    var generating by remember { mutableStateOf(false) }
    val coach by CoachStore.latestStructured.collectAsStateWithLifecycle(null)

    fun reload() = scope.launch {
        loading = true
        token?.let { t -> runCatching { plans = api.getPlans("Bearer $t").body()?.data ?: emptyList() } }
        loading = false
    }
    LaunchedEffect(token) { if (token != null) reload() }

    // Create plan dialog
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        containerColor = CARD2,
        title = { Text("Generate AI Plan", fontWeight = FontWeight.Bold, color = TXT1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    nameInput, { nameInput = it }, label = { Text("Plan name", color = TXT2) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = BORDER, focusedBorderColor = PURPLE, focusedTextColor = TXT1, unfocusedTextColor = TXT1)
                )
                Text("Goal", fontWeight = FontWeight.SemiBold, color = TXT2, fontSize = 13.sp)
                listOf("weight_loss" to "🔥 Lose Weight", "muscle_gain" to "💪 Muscle Gain", "endurance" to "🏃 Endurance", "toning" to "✨ Toning").forEach { (v, l) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (goalInput == v) PURPLE.copy(0.15f) else CARD, RoundedCornerShape(10.dp))
                            .border(1.dp, if (goalInput == v) PURPLE.copy(0.4f) else BORDER, RoundedCornerShape(10.dp))
                            .clickable { goalInput = v }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(goalInput == v, { goalInput = v }, colors = RadioButtonDefaults.colors(selectedColor = PURPLE))
                        Text(l, color = TXT1, fontSize = 14.sp)
                    }
                }
                Text("Level", fontWeight = FontWeight.SemiBold, color = TXT2, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("beginner" to "🌱", "intermediate" to "🌿", "advanced" to "🌳").forEach { (v, e) ->
                        Box(
                            Modifier.weight(1f)
                                .background(if (levelInput == v) TEAL.copy(0.15f) else CARD, RoundedCornerShape(10.dp))
                                .border(1.dp, if (levelInput == v) TEAL.copy(0.4f) else BORDER, RoundedCornerShape(10.dp))
                                .clickable { levelInput = v }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(e, fontSize = 20.sp)
                                Text(v.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = if (levelInput == v) TEAL else TXT2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (generating) CARD2 else PURPLE)
                    .clickable(enabled = !generating) {
                        generating = true
                        scope.launch {
                            runCatching { token?.let { t -> api.generatePlan("Bearer $t", GeneratePlanRequest(nameInput, goalInput, levelInput)); reload() } }
                            generating = false; showCreate = false
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                if (generating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Generate ✨", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton({ showCreate = false }) { Text("Cancel", color = TXT2) } }
    )

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Training Plans", fontWeight = FontWeight.Bold, color = TXT1) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f)),
                    actions = {
                        IconButton({ showCreate = true }) {
                            Box(Modifier.size(38.dp).background(PURPLE, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                        }
                    }
                )
            }
        ) { pad ->
            if (loading) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PURPLE) }
            } else if (plans.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📋", fontSize = 64.sp)
                        Text("No training plans yet", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 18.sp)
                        Text("Generate a personalised AI plan to get started", color = TXT2, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(PURPLE, Color(0xFF9D97FF))))
                                .clickable { showCreate = true }
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Text("Generate AI Plan ✨", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    coach?.get("plan_updates")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.let { updates ->
                        if (updates.isNotEmpty()) item {
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(TEAL.copy(0.08f), RoundedCornerShape(14.dp))
                                    .border(1.dp, TEAL.copy(0.25f), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🤖", fontSize = 15.sp); Text("Coach Plan Update", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 13.sp)
                                }
                                updates.take(2).forEach { Text("• $it", color = Color(0xFFD0D0F0), fontSize = 13.sp) }
                            }
                        }
                    }
                    items(plans) { p ->
                        val active = p["is_active"]?.jsonPrimitive?.booleanOrNull ?: false
                        val goal   = p["goal"]?.jsonPrimitive?.content ?: ""
                        val accent = when (goal) { "muscle_gain" -> CORAL; "weight_loss" -> AMBER; "endurance" -> TEAL; else -> PURPLE }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (active) accent.copy(0.1f) else CARD, RoundedCornerShape(16.dp))
                                .border(1.dp, if (active) accent.copy(0.4f) else BORDER, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(46.dp).background(accent.copy(0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text(when (goal) { "muscle_gain" -> "💪"; "weight_loss" -> "🔥"; "endurance" -> "🏃"; else -> "✨" }, fontSize = 22.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(p["name"]?.jsonPrimitive?.content ?: "Plan", fontWeight = FontWeight.Bold, color = TXT1, fontSize = 15.sp)
                                    if (active) Box(Modifier.background(TEAL.copy(0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("ACTIVE", fontSize = 9.sp, color = TEAL, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text("${goal.replace("_"," ").replaceFirstChar{it.uppercase()}} · Week ${p["current_week"]?.jsonPrimitive?.content ?: "1"} of ${p["duration_weeks"]?.jsonPrimitive?.content ?: "4"}", fontSize = 12.sp, color = TXT2)
                                val pct = p["completion_percent"]?.jsonPrimitive?.floatOrNull ?: 0f
                                if (pct > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(progress = { pct / 100f }, Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = accent, trackColor = accent.copy(0.15f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACHIEVEMENTS SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AchievementsScreen(authVm: AuthViewModel) {
    val token by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api   = remember { ApiClient.create(BuildConfig.BASE_URL) }
    var achievements by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    LaunchedEffect(token) {
        token?.let { t -> runCatching { achievements = api.getAchievements("Bearer $t").body()?.data ?: emptyList() } }
        loading = false
    }
    val unlocked = achievements.count { it["unlocked"]?.jsonPrimitive?.booleanOrNull == true }
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Achievements", fontWeight = FontWeight.Bold, color = TXT1) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f))
                )
            }
        ) { pad ->
            if (loading) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PURPLE) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Color(0xFF1A1040), Color(0xFF0D1A40))), RoundedCornerShape(16.dp))
                                .border(1.dp, PURPLE.copy(0.3f), RoundedCornerShape(16.dp))
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("$unlocked / ${achievements.size}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = AMBER)
                                Text("Achievements Unlocked", fontSize = 13.sp, color = TXT2)
                            }
                            Text("🏆", fontSize = 44.sp)
                        }
                    }
                    items(achievements) { a ->
                        val isUnlocked = a["unlocked"]?.jsonPrimitive?.booleanOrNull ?: false
                        val rarity = a["rarity"]?.jsonPrimitive?.content ?: "common"
                        val rarityColor = when (rarity) { "legendary" -> AMBER; "epic" -> PURPLE; "rare" -> TEAL; else -> TXT2 }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (isUnlocked) rarityColor.copy(0.08f) else CARD, RoundedCornerShape(14.dp))
                                .border(1.dp, if (isUnlocked) rarityColor.copy(0.3f) else BORDER, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(52.dp)
                                    .background(if (isUnlocked) rarityColor.copy(0.15f) else CARD2, CircleShape)
                                    .border(1.dp, if (isUnlocked) rarityColor.copy(0.4f) else BORDER, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isUnlocked) a["icon"]?.jsonPrimitive?.content ?: "🏆" else "🔒",
                                    fontSize = 24.sp,
                                    modifier = Modifier.then(if (!isUnlocked) Modifier.alpha(0.4f) else Modifier)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        a["name"]?.jsonPrimitive?.content ?: "",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isUnlocked) TXT1 else TXT2,
                                        fontSize = 14.sp
                                    )
                                    if (isUnlocked) Box(Modifier.background(rarityColor.copy(0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                                        Text(rarity.uppercase(), fontSize = 8.sp, color = rarityColor, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text(a["description"]?.jsonPrimitive?.content ?: "", fontSize = 12.sp, color = TXT2, lineHeight = 16.sp)
                            }
                            if (isUnlocked) Icon(Icons.Default.CheckCircle, null, tint = rarityColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HISTORY SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun HistoryScreen(vm: WorkoutViewModel, onBack: () -> Unit) {
    val hist    by vm.history.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadHistory() }
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Workout History", fontWeight = FontWeight.Bold, color = TXT1) },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TXT1) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f))
                )
            }
        ) { pad ->
            if (loading) Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PURPLE) }
            else LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(hist) { WorkoutHistoryRow(it) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROFILE SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProfileScreen(vm: AuthViewModel, onPrivacy: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle(null)
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Profile", fontWeight = FontWeight.Bold, color = TXT1) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f))
                )
            }
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar card
                item {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1040), Color(0xFF0D1A40))), RoundedCornerShape(20.dp))
                            .border(1.dp, PURPLE.copy(0.3f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(68.dp)
                                    .background(Brush.linearGradient(listOf(PURPLE, TEAL)), CircleShape)
                                    .border(2.dp, PURPLE.copy(0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(user?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(user?.name ?: "Athlete", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TXT1)
                                Text(user?.email ?: "", color = TXT2, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.background(
                                        if (user?.is_email_verified == true) TEAL.copy(0.15f) else AMBER.copy(0.15f),
                                        RoundedCornerShape(6.dp)
                                    ).padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        if (user?.is_email_verified == true) "✓ Verified" else "⚠ Email not verified",
                                        fontSize = 11.sp,
                                        color = if (user?.is_email_verified == true) TEAL else AMBER,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item { Text("Training", fontWeight = FontWeight.Bold, color = TXT2, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
                item { ProfRow(Icons.Default.FitnessCenter, "Fitness Goals & Equipment") {} }
                item { ProfRow(Icons.Default.Restaurant, "Dietary Preferences") {} }
                item { ProfRow(Icons.Default.Notifications, "Notifications & Reminders") {} }
                item { ProfRow(Icons.Default.EmojiEvents, "Achievements") {} }

                item { Text("Account", fontWeight = FontWeight.Bold, color = TXT2, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
                item { ProfRow(Icons.Default.Security, "Privacy & Data", onPrivacy) }
                item { ProfRow(Icons.Default.HelpOutline, "Help Center") {} }
                item { ProfRow(Icons.Default.BugReport, "Report a Bug") {} }
                item { ProfRow(Icons.Default.Share, "Invite Friends") {} }
                item { ProfRow(Icons.Default.Info, "About FormLogic v1.0") {} }

                item {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFF4757).copy(0.1f))
                            .border(1.5.dp, Color(0xFFFF4757).copy(0.5f), RoundedCornerShape(14.dp))
                            .clickable { vm.logout() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Logout, null, tint = Color(0xFFFF4757), modifier = Modifier.size(18.dp))
                            Text("Log Out", color = Color(0xFFFF4757), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ProfRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth()
            .background(CARD, RoundedCornerShape(12.dp))
            .border(1.dp, BORDER, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).background(PURPLE.copy(0.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PURPLE, modifier = Modifier.size(18.dp))
        }
        Text(label, Modifier.weight(1f), color = TXT1, fontSize = 14.sp)
        Icon(Icons.Default.ChevronRight, null, tint = TXT2, modifier = Modifier.size(18.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VERIFY EMAIL + RESET PASSWORD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun VerifyEmailScreen(token: String?, vm: AuthViewModel, onDone: () -> Unit) {
    var message by remember { mutableStateOf("Verifying your email…") }
    var success by remember { mutableStateOf(false) }
    val api = remember { ApiClient.create(BuildConfig.BASE_URL) }
    LaunchedEffect(token) {
        if (token.isNullOrBlank()) { message = "Invalid verification link."; return@LaunchedEffect }
        runCatching { val res = api.verifyEmail(token); if (res.isSuccessful) { message = "Email verified! You can now sign in."; success = true } else message = "Invalid or expired link." }.onFailure { message = "Network error. Try again." }
    }
    ScreenBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
                if (!success) CircularProgressIndicator(color = PURPLE) else Text("✅", fontSize = 56.sp)
                Text(message, textAlign = TextAlign.Center, fontSize = 16.sp, color = TXT1, lineHeight = 22.sp)
                if (success) Box(Modifier.clip(RoundedCornerShape(14.dp)).background(PURPLE).clickable(onClick = onDone).padding(horizontal = 28.dp, vertical = 14.dp)) {
                    Text("Sign In →", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ResetPasswordScreen(token: String?, vm: AuthViewModel, onDone: () -> Unit) {
    var newPw   by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showPw  by remember { mutableStateOf(false) }
    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopAppBar(title = { Text("Reset Password", color = TXT1) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = BG0.copy(0.97f))) }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Enter your new password below.", color = TXT2, fontSize = 14.sp)
                OutlinedTextField(
                    newPw, { newPw = it }, label = { Text("New Password", color = TXT2) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ showPw = !showPw }) { Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TXT2) } },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = BORDER, focusedBorderColor = PURPLE, focusedTextColor = TXT1, unfocusedTextColor = TXT1, focusedContainerColor = CARD2, unfocusedContainerColor = CARD2)
                )
                if (message.isNotEmpty()) Text(message, color = if (message.startsWith("✅")) TEAL else Color(0xFFFF4757), fontSize = 13.sp)
                ABtn("Set New Password", loading) {
                    if (!token.isNullOrBlank()) {
                        loading = true
                        vm.resetPassword(token, newPw) { ok, msg -> message = if (ok) "✅ $msg" else msg; if (ok) onDone(); loading = false }
                    }
                }
            }
        }
    }
}
