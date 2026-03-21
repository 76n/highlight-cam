package com.highlightcam.app.data

import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.RecorderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository
    @Inject
    constructor() {
        private val _recorderState = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val recorderState: StateFlow<RecorderState> = _recorderState.asStateFlow()

        private val _goalZone = MutableStateFlow<GoalZone?>(null)
        val goalZone: StateFlow<GoalZone?> = _goalZone.asStateFlow()

        private val _clipsSavedThisSession = MutableStateFlow(0)
        val clipsSavedThisSession: StateFlow<Int> = _clipsSavedThisSession.asStateFlow()

        fun updateRecorderState(state: RecorderState) {
            _recorderState.value = state
        }

        fun incrementClipsSaved() {
            _clipsSavedThisSession.value++
        }

        fun setGoalZone(zone: GoalZone) {
            _goalZone.value = zone
        }
    }
