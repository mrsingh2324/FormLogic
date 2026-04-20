package com.formlogic.store

import com.formlogic.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WorkoutStore {
    private val _state = MutableStateFlow(WorkoutState())
    val state = _state.asStateFlow()

    fun startSession(exercises: List<ActiveExercise>) {
        _state.update { it.copy(phase = WorkoutPhase.ACTIVE, sessionStartTime = System.currentTimeMillis(), currentExerciseIndex = 0, exercises = exercises, isRecording = true) }
    }

    fun recordRep(formScore: Float) {
        _state.update { s ->
            val u = s.exercises.toMutableList()
            val c = u[s.currentExerciseIndex]
            val newScores = c.formScores + formScore
            u[s.currentExerciseIndex] = c.copy(completedReps = c.completedReps + 1, repHistory = c.repHistory + RepData(c.completedReps + 1, formScore), formScores = newScores, avgFormScore = newScores.average().toFloat())
            s.copy(exercises = u)
        }
    }

    fun nextExercise() {
        _state.update { s ->
            val u = s.exercises.toMutableList(); u[s.currentExerciseIndex] = u[s.currentExerciseIndex].copy(isCompleted = true)
            val next = s.currentExerciseIndex + 1
            if (next >= s.exercises.size) s.copy(exercises = u, phase = WorkoutPhase.COMPLETE)
            else s.copy(exercises = u, currentExerciseIndex = next, phase = WorkoutPhase.REST)
        }
    }

    fun completeSession() {
        _state.update { s ->
            val d = s.sessionStartTime?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
            s.copy(phase = WorkoutPhase.COMPLETE, isRecording = false, totalDuration = d)
        }
    }

    fun pauseSession()  { _state.update { it.copy(isRecording = false) } }
    fun resumeSession() { _state.update { it.copy(isRecording = true) } }
    fun resetSession()  { _state.value = WorkoutState() }
    fun setVoiceEnabled(e: Boolean) { _state.update { it.copy(voiceEnabled = e) } }
    fun setLastVoiceMessage(msg: String) { _state.update { it.copy(lastVoiceMessage = msg, lastVoiceTime = System.currentTimeMillis()) } }
}
