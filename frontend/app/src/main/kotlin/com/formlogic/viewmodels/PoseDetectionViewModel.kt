package com.formlogic.viewmodels

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import com.formlogic.models.*
import com.formlogic.store.WorkoutStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

// ── VoiceCoach (port of useVoiceCoach.ts) ─────────────────────────────────────

class VoiceCoach(private val tts: TextToSpeech) {
    private val motivational  = listOf("Great form! Keep it up!", "You're doing amazing!", "Perfect rep! Stay strong!", "Outstanding technique!")
    private val encouragement = listOf("You've got this!", "Keep pushing!", "Don't stop now!", "Almost there!")
    private var lastSpeakTime = 0L
    private val MIN_INTERVAL  = 3000L

    private fun speak(message: String, priority: Boolean = false) {
        if (!priority && System.currentTimeMillis() - lastSpeakTime < MIN_INTERVAL) return
        lastSpeakTime = System.currentTimeMillis()
        WorkoutStore.setLastVoiceMessage(message)
        tts.language = Locale("en", "IN")
        tts.setSpeechRate(0.9f)
        tts.speak(message, if (priority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, null)
    }

    fun announceFormIssue(issues: List<String>)    { issues.firstOrNull()?.let { speak(it) } }
    fun announceGoodForm()                          { speak(motivational.random()) }
    fun announceRep(rep: Int, target: Int)          { when { rep == target -> speak("Last rep! Finish strong!", true); rep % 5 == 0 -> speak(encouragement.random()) } }
    fun announceSetComplete(set: Int, total: Int)   { if (set == total) speak("Exercise complete! Well done!", true) else speak("Set $set done. Rest 60 seconds.") }
    fun announceExerciseStart(name: String, r: Int, s: Int) { speak("Starting $name. $s sets of $r reps.", true) }
    fun announceWorkoutComplete()                   { speak("Workout complete! Excellent work today!", true) }
    fun stop()                                      { tts.stop() }
}

// ── PoseDetectionViewModel ─────────────────────────────────────────────────────

data class PoseDetectionState(
    val keypoints:       List<Keypoint>?  = null,
    val angles:          ExerciseAngles   = ExerciseAngles(),
    val formFeedback:    FormFeedback?    = null,
    val currentAngle:    Double           = 180.0,
    val isPersonDetected:Boolean          = false,
    val fps:             Int              = 0,
)

class PoseDetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PoseDetectionState())
    val state = _state.asStateFlow()

    private var repDetector: RepDetector? = null
    private var exerciseId  = "squat"
    private var targetReps  = 15
    private var frameCount  = 0
    private var lastFpsTime = System.currentTimeMillis()

    val repCount get() = repDetector?.repCount ?: 0

    fun setup(exerciseId: String, targetReps: Int) {
        this.exerciseId  = exerciseId
        this.targetReps  = targetReps
        repDetector = RepDetector(exerciseId)
    }

    fun processKeypoints(
        rawKps:         List<Keypoint>,
        onRepComplete:  (Float) -> Unit = {},
        onSetComplete:  () -> Unit      = {},
    ) {
        if (rawKps.size < 33) return
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000) { _state.update { it.copy(fps = frameCount) }; frameCount = 0; lastFpsTime = now }

        val angles   = calculateExerciseAngles(rawKps)
        val primary  = getPrimaryAngle(exerciseId, angles)
        val repDone  = repDetector?.update(primary) ?: false
        val feedback = getFormFeedback(exerciseId, angles)

        if (repDone) {
            WorkoutStore.recordRep(feedback.score)
            onRepComplete(feedback.score)
            if ((repDetector?.repCount ?: 0) >= targetReps) { onSetComplete(); repDetector?.reset() }
        }
        _state.update { it.copy(keypoints = rawKps, angles = angles, formFeedback = feedback, currentAngle = primary, isPersonDetected = true) }
    }

    fun resetDetector() { repDetector?.reset() }
}
