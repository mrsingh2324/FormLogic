package com.formlogic.screens

import android.Manifest
import android.speech.tts.TextToSpeech
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.formlogic.components.*
import com.formlogic.models.*
import com.formlogic.utils.MLKitBridge
import com.formlogic.viewmodels.VoiceCoach
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Dev mock ──────────────────────────────────────────────────────────────────

private fun mockPose(progress: Float): List<Keypoint> {
    val kps = MutableList(33) { Keypoint(0.5f, 0.5f, 0.9f) }
    val cx = 0.5f; val by = 0.15f; val sc = 0.6f
    kps[0]  = Keypoint(cx, by)
    kps[11] = Keypoint(cx - 0.1f, by + 0.15f * sc); kps[12] = Keypoint(cx + 0.1f, by + 0.15f * sc)
    kps[23] = Keypoint(cx - 0.07f, by + 0.4f * sc);  kps[24] = Keypoint(cx + 0.07f, by + 0.4f * sc)
    val ky  = by + (0.6f + progress * 0.15f) * sc
    kps[25] = Keypoint(cx - 0.09f, ky);               kps[26] = Keypoint(cx + 0.09f, ky)
    kps[27] = Keypoint(cx - 0.09f, by + 0.85f * sc); kps[28] = Keypoint(cx + 0.09f, by + 0.85f * sc)
    kps[13] = Keypoint(cx - 0.18f, by + 0.25f * sc); kps[14] = Keypoint(cx + 0.18f, by + 0.25f * sc)
    kps[15] = Keypoint(cx - 0.22f, by + 0.35f * sc); kps[16] = Keypoint(cx + 0.22f, by + 0.35f * sc)
    return kps
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * WorkoutCameraScreen
 *
 * onFinish returns the full per-rep form score list alongside aggregate stats
 * so the save layer can persist per-rep data to the backend.
 */
@Composable
fun WorkoutCameraScreen(
    exerciseId:   String = "squat",
    exerciseName: String = "Bodyweight Squat",
    targetReps:   Int    = 15,
    targetSets:   Int    = 3,
    onFinish: (totalReps: Int, duration: Int, avgScore: Float, calories: Int, allScores: List<Float>) -> Unit,
    onBack:   () -> Unit,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Camera permission ─────────────────────────────────────────────────────
    var hasCam       by remember { mutableStateOf(false) }
    var useFrontCam  by remember { mutableStateOf(true) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.CAMERA) }

    // ── Session state ─────────────────────────────────────────────────────────
    var isPaused    by remember { mutableStateOf(false) }
    var sessionSecs by remember { mutableIntStateOf(0) }
    var currentSet  by remember { mutableIntStateOf(1) }
    var repCount    by remember { mutableIntStateOf(0) }
    var formScore   by remember { mutableFloatStateOf(0f) }
    var allScores   by remember { mutableStateOf(listOf<Float>()) }
    var showFinish  by remember { mutableStateOf(false) }
    var keypoints   by remember { mutableStateOf<List<Keypoint>?>(null) }
    var feedback    by remember { mutableStateOf<FormFeedback?>(null) }

    // ── Rest timer ────────────────────────────────────────────────────────────
    var isResting     by remember { mutableStateOf(false) }
    var restSecsLeft  by remember { mutableIntStateOf(60) }
    val REST_DURATION  = 60   // seconds between sets

    // ── Voice coach ───────────────────────────────────────────────────────────
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var voiceCoach by remember { mutableStateOf<VoiceCoach?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = it.also { engine ->
                    engine.language = Locale("en", "IN")
                    voiceCoach = VoiceCoach(engine)
                    voiceCoach?.announceExerciseStart(exerciseName, targetReps, targetSets)
                }
            }
        }
        onDispose { t.stop(); t.shutdown() }
    }

    // ── Rep progress tracking ─────────────────────────────────────────────────
    var prevProgress by remember { mutableFloatStateOf(0f) }
    val repDetector  = remember { RepDetector(exerciseId) }

    fun finishSession(repsOverride: Int? = null) {
        val totalReps = repsOverride ?: (repCount + (currentSet - 1) * targetReps)
        val avg = if (allScores.isEmpty()) 0f else allScores.average().toFloat()
        val cal = (5 * 70 * (sessionSecs / 3600.0) * 1.05).roundToInt()
        voiceCoach?.announceWorkoutComplete()
        onFinish(totalReps, sessionSecs, avg, cal, allScores)
    }

    fun onNewKeypoints(kps: List<Keypoint>) {
        if (isPaused || isResting) return
        keypoints = kps
        val angles  = calculateExerciseAngles(kps)
        val primary = getPrimaryAngle(exerciseId, angles)
        val repDone = repDetector.update(primary)
        val fb      = getFormFeedback(exerciseId, angles)
        feedback    = fb
        formScore   = fb.score

        if (repDone) {
            allScores = allScores + fb.score
            repCount++
            voiceCoach?.announceRep(repCount, targetReps)
            if (fb.issues.isNotEmpty()) voiceCoach?.announceFormIssue(fb.issues)
            else voiceCoach?.announceGoodForm()

            if (repCount >= targetReps) {
                if (currentSet >= targetSets) {
                    finishSession()
                } else {
                    voiceCoach?.announceSetComplete(currentSet, targetSets)
                    isResting = true
                    restSecsLeft = REST_DURATION
                    currentSet++
                    repCount = 0
                    repDetector.reset()
                }
            }
        }
    }

    // Timer
    LaunchedEffect(isPaused, isResting) {
        while (!isPaused && !isResting) { delay(1000); sessionSecs++ }
    }

    // Rest countdown
    LaunchedEffect(isResting) {
        if (!isResting) return@LaunchedEffect
        while (restSecsLeft > 0) { delay(1000); restSecsLeft-- }
        isResting = false
        voiceCoach?.announceExerciseStart(exerciseName, targetReps, targetSets)
    }

    // ── ML Kit real path
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    // ── Fallback mock when ML Kit unavailable
    val useMLKit = MLKitBridge.isNativeAvailable
    LaunchedEffect(isPaused, exerciseId) {
        if (useMLKit) return@LaunchedEffect
        var t = 0.0
        while (!isPaused) {
            t += 0.05
            val p = ((sin(t) + 1) / 2).toFloat()
            onNewKeypoints(mockPose(p))
            delay(50)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // Camera preview + ImageAnalysis
        if (hasCam) {
            val cameraSelector = if (useFrontCam) CameraSelector.DEFAULT_FRONT_CAMERA
                                 else CameraSelector.DEFAULT_BACK_CAMERA
            key(useFrontCam) {   // rebind when camera flips
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            ProcessCameraProvider.getInstance(ctx).addListener({
                                runCatching {
                                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                                    provider.unbindAll()
                                    val preview = Preview.Builder().build()
                                        .apply { setSurfaceProvider(pv.surfaceProvider) }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(480, 640))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build().also { analysis ->
                                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                                MLKitBridge.analyzeFrame(imageProxy) { kps ->
                                                    kps?.let { onNewKeypoints(it) }
                                                }
                                            }
                                        }
                                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF111111)))
        }

        // Skeleton overlay
        SkeletonOverlay(keypoints = keypoints, feedback = feedback)

        if (!useMLKit) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
                .background(Color(0xCCFFA500), CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("⚠ Dev Mock · No ML Kit", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // ── Top HUD ───────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().align(Alignment.TopStart)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
            .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) {
                    Surface(shape = CircleShape, color = Color.White.copy(0.18f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(exerciseName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Set $currentSet / $targetSets  ·  %02d:%02d".format(sessionSecs / 60, sessionSecs % 60),
                        color = Color.White.copy(0.7f), fontSize = 13.sp)
                }
                // Camera flip button
                IconButton(onClick = { useFrontCam = !useFrontCam }) {
                    Surface(shape = CircleShape, color = Color.White.copy(0.18f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FormScoreBadge(formScore)
            }
        }

        // Feedback banner
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 100.dp)) {
            feedback?.let { if (it.issues.isNotEmpty()) FeedbackBanner(it) }
        }

        // ── Rest overlay ──────────────────────────────────────────────────────
        AnimatedVisibility(isResting, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.82f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("REST", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                    Text("$restSecsLeft", color = MaterialTheme.colorScheme.primary, fontSize = 88.sp, fontWeight = FontWeight.Black)
                    Text("Set $currentSet of $targetSets next", color = Color.White.copy(0.7f), fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { restSecsLeft = 0 }, Modifier.height(48.dp)) {
                        Text("Skip Rest →")
                    }
                }
            }
        }

        // ── Pause overlay ─────────────────────────────────────────────────────
        AnimatedVisibility(!isResting && isPaused, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.78f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PAUSED", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp)
                    Spacer(Modifier.height(24.dp))
                    Button({ isPaused = false }, Modifier.height(52.dp)) { Text("▶  Resume", fontSize = 16.sp) }
                }
            }
        }

        // ── Bottom HUD ────────────────────────────────────────────────────────
        if (!isResting) {
            Box(Modifier.fillMaxWidth().align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.92f))))
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 40.dp)) {
                Column {
                    RepCounter(repCount, formScore, targetReps)
                    Spacer(Modifier.height(16.dp))
                    WorkoutControls(isPaused, { isPaused = !isPaused }, { showFinish = true })
                }
            }
        }

        // Finish dialog
        if (showFinish) AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text("Finish Exercise?") },
            text  = { Text("$repCount reps in set $currentSet. End session?") },
            confirmButton = { TextButton({ showFinish = false; finishSession() }) {
                Text("Finish", color = MaterialTheme.colorScheme.error)
            }},
            dismissButton = { TextButton({ showFinish = false }) { Text("Keep Going") } }
        )
    }
}
