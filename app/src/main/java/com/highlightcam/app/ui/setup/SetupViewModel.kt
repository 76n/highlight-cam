package com.highlightcam.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.NormalizedPoint
import com.highlightcam.app.domain.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep {
    PLACING_A,
    DECIDING_SECOND_GOAL,
    PLACING_B,
    FINE_TUNING,
    CONFIRMING,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.PLACING_A,
    val goalAPoints: List<NormalizedPoint> = emptyList(),
    val goalBPoints: List<NormalizedPoint> = emptyList(),
    val goalBEnabled: Boolean = true,
    val isReconfiguring: Boolean = false,
    val cameraError: String? = null,
) {
    val decidingSecondGoal: Boolean get() = step == SetupStep.DECIDING_SECOND_GOAL
}

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

        val videoQuality: StateFlow<VideoQuality> =
            userPreferencesRepository.videoQuality
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), VideoQuality.FHD_1080)

        init {
            viewModelScope.launch {
                val existing = userPreferencesRepository.goalZoneSet.first()
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            isReconfiguring = true,
                            step = SetupStep.FINE_TUNING,
                            goalAPoints = existing.goalA.toPoints(),
                            goalBPoints = existing.goalB?.toPoints() ?: emptyList(),
                            goalBEnabled = existing.hasGoalB,
                        )
                    }
                }
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
                            state.copy(goalAPoints = pts, step = SetupStep.DECIDING_SECOND_GOAL)
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

        fun onAddGoalB() {
            _uiState.update { state ->
                if (state.step == SetupStep.DECIDING_SECOND_GOAL) {
                    state.copy(step = SetupStep.PLACING_B, goalBEnabled = true)
                } else {
                    state
                }
            }
        }

        fun onSkipGoalB() {
            _uiState.update { state ->
                if (state.step == SetupStep.DECIDING_SECOND_GOAL) {
                    state.copy(step = SetupStep.FINE_TUNING, goalBEnabled = false, goalBPoints = emptyList())
                } else {
                    state
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
                val a =
                    if (aPoints.size >= GoalZone.VERTEX_COUNT) aPoints else GoalZone.GOAL_A_DEFAULT.toPoints()

                val goalB =
                    if (bPoints.size >= GoalZone.VERTEX_COUNT) {
                        GoalZone("b", "Goal B", bPoints[0], bPoints[1], bPoints[2], bPoints[3])
                    } else {
                        null
                    }

                return GoalZoneSet(
                    goalA = GoalZone("a", "Goal A", a[0], a[1], a[2], a[3]),
                    goalB = goalB,
                )
            }
        }
    }
