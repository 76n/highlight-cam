package com.highlightcam.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.NormalizedPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep {
    PLACING_A,
    PLACING_B,
    FINE_TUNING,
    CONFIRMING,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.PLACING_A,
    val goalAPoints: List<NormalizedPoint> = emptyList(),
    val goalBPoints: List<NormalizedPoint> = emptyList(),
    val isReconfiguring: Boolean = false,
    val cameraError: String? = null,
)

sealed class SetupNavEvent {
    data object NavigateToRecording : SetupNavEvent()
}

@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val userPreferencesRepository: UserPreferencesRepository,
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SetupUiState())
        val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

        private val _navEvents = Channel<SetupNavEvent>(Channel.BUFFERED)
        val navEvents = _navEvents.receiveAsFlow()

        init {
            viewModelScope.launch {
                val existing = userPreferencesRepository.goalZoneSet.first()
                _uiState.update { it.copy(isReconfiguring = existing != null) }
            }
        }

        fun onCanvasTap(
            normalizedX: Float,
            normalizedY: Float,
        ) {
            _uiState.update { state ->
                val point = NormalizedPoint(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f))
                when (state.step) {
                    SetupStep.PLACING_A -> {
                        val pts = state.goalAPoints + point
                        if (pts.size >= GoalZone.VERTEX_COUNT) {
                            state.copy(goalAPoints = pts, step = SetupStep.PLACING_B)
                        } else {
                            state.copy(goalAPoints = pts)
                        }
                    }
                    SetupStep.PLACING_B -> {
                        val pts = state.goalBPoints + point
                        if (pts.size >= GoalZone.VERTEX_COUNT) {
                            state.copy(goalBPoints = pts, step = SetupStep.FINE_TUNING)
                        } else {
                            state.copy(goalBPoints = pts)
                        }
                    }
                    else -> state
                }
            }
        }

        fun onHandleDrag(
            goalId: String,
            pointIndex: Int,
            normalizedX: Float,
            normalizedY: Float,
        ) {
            _uiState.update { state ->
                val point = NormalizedPoint(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f))
                when (goalId) {
                    "a" -> {
                        if (pointIndex in state.goalAPoints.indices) {
                            val updated = state.goalAPoints.toMutableList().also { it[pointIndex] = point }
                            state.copy(goalAPoints = updated)
                        } else {
                            state
                        }
                    }
                    "b" -> {
                        if (pointIndex in state.goalBPoints.indices) {
                            val updated = state.goalBPoints.toMutableList().also { it[pointIndex] = point }
                            state.copy(goalBPoints = updated)
                        } else {
                            state
                        }
                    }
                    else -> state
                }
            }
        }

        fun advanceToConfirm() {
            _uiState.update { it.copy(step = SetupStep.CONFIRMING) }
        }

        fun confirmZones() {
            viewModelScope.launch {
                val state = _uiState.value
                val zoneSet = buildGoalZoneSet(state.goalAPoints, state.goalBPoints)
                userPreferencesRepository.updateGoalZoneSet(zoneSet)
                sessionRepository.setGoalZoneSet(zoneSet)
                _navEvents.send(SetupNavEvent.NavigateToRecording)
            }
        }

        fun useDefaults() {
            viewModelScope.launch {
                userPreferencesRepository.updateGoalZoneSet(GoalZoneSet.DEFAULT)
                sessionRepository.setGoalZoneSet(GoalZoneSet.DEFAULT)
                _navEvents.send(SetupNavEvent.NavigateToRecording)
            }
        }

        fun keepCurrentZones() {
            viewModelScope.launch { _navEvents.send(SetupNavEvent.NavigateToRecording) }
        }

        fun redraw() {
            _uiState.update { SetupUiState(isReconfiguring = it.isReconfiguring) }
        }

        fun updateCameraError(error: String?) {
            _uiState.update { it.copy(cameraError = error) }
        }

        companion object {
            fun buildGoalZoneSet(
                aPoints: List<NormalizedPoint>,
                bPoints: List<NormalizedPoint>,
            ): GoalZoneSet {
                fun pts(list: List<NormalizedPoint>) = if (list.size >= GoalZone.VERTEX_COUNT) list else GoalZone.GOAL_A_DEFAULT.toPoints()

                val a = pts(aPoints)
                val b = if (bPoints.size >= GoalZone.VERTEX_COUNT) bPoints else GoalZone.GOAL_B_DEFAULT.toPoints()

                return GoalZoneSet(
                    goalA = GoalZone("a", "Goal A", a[0], a[1], a[2], a[3]),
                    goalB = GoalZone("b", "Goal B", b[0], b[1], b[2], b[3]),
                )
            }
        }
    }
