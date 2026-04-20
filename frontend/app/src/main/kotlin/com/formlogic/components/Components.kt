package com.formlogic.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.formlogic.models.*

// ── FormScoreRing ─────────────────────────────────────────────────────────────

@Composable
fun FormScoreRing(score: Float, size: Dp = 100.dp, strokeWidth: Dp = 10.dp, showLabel: Boolean = true) {
    val prog  by animateFloatAsState(score / 100f, tween(700, easing = FastOutSlowInEasing), label = "ring")
    val color  = scoreColor(score)
    val glow   = color.copy(alpha = 0.25f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            // track
            drawArc(Color(0xFF1C1D30), -90f, 360f, false, style = Stroke(sw, cap = StrokeCap.Round))
            // glow halo
            drawArc(glow, -90f, 360f * prog, false, style = Stroke(sw + 6.dp.toPx(), cap = StrokeCap.Round))
            // arc
            drawArc(color, -90f, 360f * prog, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${score.toInt()}%", fontSize = (size.value * 0.18f).sp, fontWeight = FontWeight.ExtraBold, color = color)
            if (showLabel) Text(scoreLabel(score), fontSize = (size.value * 0.09f).sp, color = Color(0xFF8080A0))
        }
    }
}

// ── FormScoreBadge ────────────────────────────────────────────────────────────

@Composable
fun FormScoreBadge(score: Float) {
    var trigger by remember { mutableIntStateOf(0) }
    var prev    by remember { mutableFloatStateOf(score) }
    val scale   by animateFloatAsState(if (trigger % 2 == 1) 1.18f else 1f, spring(0.35f, 380f), label = "badge") { trigger = 0 }
    LaunchedEffect(score) { if (score != prev) { prev = score; trigger = 1 } }
    val color = scoreColor(score)
    Box(
        Modifier.scale(scale)
            .background(color.copy(0.12f), RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${score.toInt()}%", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color)
            Text(scoreLabel(score), fontSize = 10.sp, color = color.copy(0.8f))
        }
    }
}

// ── RepCounter ────────────────────────────────────────────────────────────────

@Composable
fun RepCounter(count: Int, formScore: Float, target: Int = 0) {
    var prev   by remember { mutableIntStateOf(count) }
    var bounce by remember { mutableIntStateOf(0) }
    val scale  by animateFloatAsState(if (bounce % 2 == 1) 1.28f else 1f, spring(0.28f, 650f), label = "rep") { bounce = 0 }
    LaunchedEffect(count) { if (count != prev) { prev = count; bounce = 1 } }
    val color = scoreColor(formScore)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$count",
            fontSize = 100.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.scale(scale)
        )
        if (target > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(target) { i ->
                    Box(
                        Modifier.size(if (i < count) 8.dp else 6.dp)
                            .background(if (i < count) color else Color(0xFF2A2B40), CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("REPS / $target", fontSize = 12.sp, color = Color(0xFF8080A0), letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text("REPS", fontSize = 12.sp, color = Color(0xFF8080A0), letterSpacing = 4.sp)
        }
    }
}

// ── FeedbackBanner ────────────────────────────────────────────────────────────

@Composable
fun FeedbackBanner(feedback: FormFeedback) {
    val message = feedback.issues.firstOrNull() ?: return
    var visible  by remember { mutableStateOf(false) }
    var prevMsg  by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != prevMsg) {
            prevMsg = message; visible = true
            kotlinx.coroutines.delay(3500); visible = false
        }
    }
    val bgColor = when (feedback.severity) {
        "danger"  -> Color(0xFFFF4757)
        "warning" -> Color(0xFFFFB547)
        else      -> Color(0xFF43D9AD)
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit  = slideOutVertically { -it } + fadeOut()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(bgColor.copy(0.92f), RoundedCornerShape(14.dp))
                .border(1.dp, bgColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (feedback.severity) { "danger" -> "⚠️"; "warning" -> "⚡"; else -> "✅" },
                    fontSize = 18.sp
                )
                Text(message, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

// ── SkeletonOverlay ───────────────────────────────────────────────────────────

@Composable
fun SkeletonOverlay(keypoints: List<Keypoint>?, feedback: FormFeedback?) {
    if (keypoints == null) return
    val affectedJoints = feedback?.affectedJoints?.toSet() ?: emptySet()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // bones
        for ((a, b) in SKELETON_CONNECTIONS) {
            val kpA = keypoints.getOrNull(a); val kpB = keypoints.getOrNull(b)
            if (kpA != null && kpB != null && kpA.score > 0.4f && kpB.score > 0.4f) {
                val isAffected = a in affectedJoints || b in affectedJoints
                drawLine(
                    color     = if (isAffected) Color(0xFFFF4757).copy(0.85f) else Color(0xFF6C63FF).copy(0.75f),
                    start     = Offset(kpA.x * w, kpA.y * h),
                    end       = Offset(kpB.x * w, kpB.y * h),
                    strokeWidth = if (isAffected) 4.dp.toPx() else 3.dp.toPx(),
                    cap       = StrokeCap.Round
                )
            }
        }
        // joints
        for ((idx, kp) in keypoints.withIndex()) {
            if (kp.score < 0.4f) continue
            val isAffected = idx in affectedJoints
            val color = when {
                isAffected         -> Color(0xFFFF4757)
                idx in LEFT_JOINTS -> Color(0xFF6C63FF)
                idx in RIGHT_JOINTS-> Color(0xFF43D9AD)
                else               -> Color(0xFFFFB547)
            }
            // glow
            drawCircle(color.copy(0.25f), 9.dp.toPx(), Offset(kp.x * w, kp.y * h))
            drawCircle(color, 5.dp.toPx(), Offset(kp.x * w, kp.y * h))
        }
    }
}

// ── WorkoutControls ───────────────────────────────────────────────────────────

@Composable
fun WorkoutControls(isPaused: Boolean, onToggle: () -> Unit, onFinish: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onToggle,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPaused) Color(0xFF43D9AD) else Color(0xFF1E1F35)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (isPaused) "▶  Resume" else "⏸  Pause", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (isPaused) Color(0xFF003D2E) else Color.White)
        }
        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier.height(52.dp).width(110.dp),
            border = BorderStroke(1.5.dp, Color(0xFFFF4757).copy(0.6f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Finish", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFF4757))
        }
    }
}

// ── StatCard ──────────────────────────────────────────────────────────────────

@Composable
fun GlowStatCard(value: String, label: String, icon: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color.copy(0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(0.2f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(icon, fontSize = 22.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
            Text(label, fontSize = 11.sp, color = Color(0xFF8080A0), textAlign = TextAlign.Center)
        }
    }
}

// ── SectionHeader ─────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, action: String = "", onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF0F0FF))
        if (action.isNotEmpty()) TextButton(onAction) { Text(action, color = Color(0xFF6C63FF), fontSize = 13.sp) }
    }
}

// ── GradientButton ────────────────────────────────────────────────────────────

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(Color(0xFF6C63FF), Color(0xFF9D97FF)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF2A2B40), Color(0xFF2A2B40)))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else Color(0xFF8080A0),
            fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// ── FirstWorkoutGuide (Dialog) ────────────────────────────────────────────────

@Composable
fun FirstWorkoutGuide(onDismiss: () -> Unit, onStart: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .background(Color(0xFF12131F), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF6C63FF).copy(0.3f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text("🎯 Your First Workout", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onDismiss, Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, tint = Color(0xFF8080A0)) }
                }
                listOf(
                    "📱" to "Point camera so your full body is visible",
                    "💡" to "Good lighting helps pose detection accuracy",
                    "👟" to "Stand ~2–3 metres from the camera",
                    "🔊" to "Enable sound for voice coaching cues",
                ).forEach { (icon, text) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Text(icon, fontSize = 18.sp)
                        Text(text, color = Color(0xFFD0D0F0), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                GradientButton("Start Workout →", Modifier.fillMaxWidth(), onClick = { onDismiss(); onStart() })
            }
        }
    }
}
