package com.highlightcam.app.data

import com.highlightcam.app.domain.GoalZoneSet
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

        private val _goalZoneSet = MutableStateFlow<GoalZoneSet?>(null)
        val goalZoneSet: StateFlow<GoalZoneSet?> = _goalZoneSet.asStateFlow()

        private val _clipsSavedThisSession = MutableStateFlow(0)
        val clipsSavedThisSession: StateFlow<Int> = _clipsSavedThisSession.asStateFlow()

        fun updateRecorderState(state: RecorderState) {
            _recorderState.value = state
        }

        fun incrementClipsSaved() {
            _clipsSavedThisSession.value++
        }

        fun setGoalZoneSet(zoneSet: GoalZoneSet) {
            _goalZoneSet.value = zoneSet
        }
    }
