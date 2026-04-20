@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.formlogic.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formlogic.components.FormScoreRing
import com.formlogic.models.ExerciseOption
import kotlinx.coroutines.delay

// ── ExerciseTutorialScreen ────────────────────────────────────────────────────

data class TutStep(val num: Int, val instruction: String, val tip: String? = null)
val TUTORIALS: Map<String, List<TutStep>> = mapOf(
    "squat"  to listOf(TutStep(1,"Stand with feet shoulder-width apart, toes slightly outward."),TutStep(2,"Keep chest up and core engaged throughout."),TutStep(3,"Push knees out in line with toes as you descend.","Imagine spreading the floor with your feet"),TutStep(4,"Lower until thighs are parallel or deeper.","Full depth = hip crease below knee"),TutStep(5,"Drive through heels to return to standing.")),
    "pushup" to listOf(TutStep(1,"Start in plank, hands slightly wider than shoulders."),TutStep(2,"Keep body straight from head to heels."),TutStep(3,"Lower chest to just above floor, elbows at ~45°.","Do not flare elbows"),TutStep(4,"Push back up.")),
    "lunge"  to listOf(TutStep(1,"Stand tall with feet hip-width apart."),TutStep(2,"Step one foot forward about 60–90 cm."),TutStep(3,"Lower back knee toward floor, front knee at 90°."),TutStep(4,"Push through front heel to return."),TutStep(5,"Alternate legs.")),
)

@Composable
fun ExerciseTutorialScreen(exercise: ExerciseOption, onStartWorkout: () -> Unit, onBack: () -> Unit) {
    val steps = TUTORIALS[exercise.id] ?: emptyList()
    Scaffold(topBar = { TopAppBar(title = { Text("How to: ${exercise.name}") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text(exercise.emoji, fontSize = 48.sp); Spacer(Modifier.width(16.dp)); Column { Text(exercise.name, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(exercise.muscleGroups.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)); Text("${exercise.targetSets} sets × ${exercise.targetReps} reps", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) } } } }
            item { Text("Step-by-Step Guide", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            if (steps.isEmpty()) item { Text("Perform with controlled form.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(steps) { s -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp)) { Surface(Modifier.size(32.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) { Box(contentAlignment = Alignment.Center) { Text("${s.num}", color = Color.White, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(12.dp)); Column { Text(s.instruction, fontWeight = FontWeight.Medium); s.tip?.let { Spacer(Modifier.height(4.dp)); Text("💡 $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) } } } } }
            item { Spacer(Modifier.height(8.dp)); Button(onStartWorkout, Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start ${exercise.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
        }
    }
}

// ── WarmupCooldownScreen ──────────────────────────────────────────────────────

data class WarmupEx(val name: String, val emoji: String, val secs: Int, val instruction: String)
val WARMUP_LIST   = listOf(WarmupEx("Jumping Jacks","⭐",30,"Light and rhythmic"),WarmupEx("Arm Circles","🔄",20,"10 forward, 10 backward"),WarmupEx("Hip Circles","💫",20,"Slow and controlled"),WarmupEx("High Knees","🦵",20,"Knees to waist height"),WarmupEx("Leg Swings","🌊",20,"Hold wall for balance"))
val COOLDOWN_LIST = listOf(WarmupEx("Quad Stretch","🦵",30,"Hold each leg 15s"),WarmupEx("Forward Fold","🙇",30,"Breathe and relax"),WarmupEx("Child's Pose","🧘",45,"Knees wide, arms forward"),WarmupEx("Chest Opener","💛",30,"Clasp hands behind back"))

@Composable
fun WarmupCooldownScreen(isWarmup: Boolean = true, onComplete: () -> Unit, onBack: () -> Unit) {
    val exercises = if (isWarmup) WARMUP_LIST else COOLDOWN_LIST
    var idx by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(exercises.firstOrNull()?.secs ?: 30) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(idx, running) { if (!running) return@LaunchedEffect; timeLeft = exercises[idx].secs; while (timeLeft > 0) { delay(1000); timeLeft-- }; if (idx < exercises.lastIndex) idx++ else onComplete() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (isWarmup) "Warm-Up" else "Cool-Down") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (idx < exercises.size) { val ex = exercises[idx]; Text(ex.emoji, fontSize = 72.sp); Text(ex.name, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(ex.instruction, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${timeLeft}s", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary); Text("${idx + 1} / ${exercises.size}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.weight(1f))
            Button({ if (!running) running = true else idx = (idx + 1).coerceAtMost(exercises.lastIndex) }, Modifier.fillMaxWidth().height(54.dp)) { Text(if (!running) "Start" else "Skip →", fontSize = 16.sp) }
            TextButton(onComplete) { Text("Skip ${if (isWarmup) "Warm-Up" else "Cool-Down"}") }
        }
    }
}

// ── PaywallScreen ─────────────────────────────────────────────────────────────

@Composable
fun PaywallScreen(onPurchase: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val features = listOf("♾️ Unlimited workouts","📊 Advanced analytics","🤖 AI plan generation","🥗 Indian food nutrition DB","🏆 All achievements","📴 Offline mode","🎙️ Voice coaching")
    Scaffold { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Maybe later") }
                }
                Text("💪", fontSize = 56.sp)
                Text("Upgrade to Pro", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Train smarter, not harder", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
            items(features) { Row(Modifier.fillMaxWidth()) { Text(it.take(2), fontSize = 18.sp); Spacer(Modifier.width(8.dp)); Text(it.drop(2), fontWeight = FontWeight.Medium) } }
            item { Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("FormLogic Pro", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("₹499 / month", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary); Text("Cancel anytime", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Button(onPurchase, Modifier.fillMaxWidth().height(56.dp)) { Text("Start Free Trial", fontSize = 17.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(8.dp)); TextButton(onRestore) { Text("Restore Purchases") }; Text("Billed monthly. Cancel in Play Store.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ── WorkoutSummaryScreen ──────────────────────────────────────────────────────

@Composable
fun WorkoutSummaryScreen(totalReps: Int, durationSeconds: Int, avgFormScore: Float, caloriesBurned: Int, exerciseName: String, onSave: () -> Unit, onDiscard: () -> Unit) {
    val color = scoreColor(avgFormScore)
    LazyColumn(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF090A11), androidx.compose.ui.graphics.Color(0xFF151529)))),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            // Trophy badge
            Box(Modifier.size(80.dp).background(color.copy(0.15f), androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, color.copy(0.5f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center) { Text("🏆", fontSize = 36.sp) }
            Spacer(Modifier.height(12.dp))
            Text("Workout Complete!", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center)
            Text(exerciseName, fontSize = 15.sp, color = androidx.compose.ui.graphics.Color(0xFF8080A0), textAlign = TextAlign.Center)
        }
        item {
            FormScoreRing(avgFormScore, 160.dp, 12.dp)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SumStat("💪", "$totalReps", "Reps", color, Modifier.weight(1f))
                SumStat("⏱", "%d:%02d".format(durationSeconds/60, durationSeconds%60), "Time", androidx.compose.ui.graphics.Color(0xFF6C63FF), Modifier.weight(1f))
                SumStat("🔥", "$caloriesBurned", "kcal", androidx.compose.ui.graphics.Color(0xFFFF6584), Modifier.weight(1f))
            }
        }
        item {
            // Motivational message
            val msg = when {
                avgFormScore >= 90 -> "🌟 Outstanding form! You're training like a pro."
                avgFormScore >= 75 -> "💪 Great work! Your technique is improving."
                avgFormScore >= 60 -> "📈 Good session. Focus on the feedback cues next time."
                else               -> "🔧 Keep practicing — form comes with reps."
            }
            Box(Modifier.fillMaxWidth().background(color.copy(0.08f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .border(1.dp, color.copy(0.2f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text(msg, color = androidx.compose.ui.graphics.Color(0xFFD0D0F0), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Button(onSave, Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF6C63FF)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Text("💾  Save Workout", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onDiscard, Modifier.fillMaxWidth().height(48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF2A2B40)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Text("Discard", color = androidx.compose.ui.graphics.Color(0xFF8080A0))
            }
        }
    }
}

@Composable private fun SumStat(emoji: String, value: String, label: String, color: androidx.compose.ui.graphics.Color, mod: Modifier) {
    Box(mod.background(color.copy(0.08f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
        .border(1.dp, color.copy(0.2f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 22.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color)
            Text(label, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF8080A0))
        }
    }
}
