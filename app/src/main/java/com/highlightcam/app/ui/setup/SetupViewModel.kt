package com.highlightcam.app.ui.setup

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZone
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
import kotlin.math.max
import kotlin.math.min

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

        private var dragStartX = 0f
        private var dragStartY = 0f

        init {
            viewModelScope.launch {
                val existingZone = userPreferencesRepository.goalZone.first()
                _uiState.update { it.copy(isReconfiguring = existingZone != null) }
            }
        }

        fun onDragStart(
            x: Float,
            y: Float,
        ) {
            dragStartX = x
            dragStartY = y
            _uiState.update { it.copy(currentDragRect = null, isRectFinalized = false) }
        }

        fun onDragUpdate(
            x: Float,
            y: Float,
        ) {
            val rect =
                Rect(
                    left = min(dragStartX, x),
                    top = min(dragStartY, y),
                    right = max(dragStartX, x),
                    bottom = max(dragStartY, y),
                )
            _uiState.update { it.copy(currentDragRect = rect) }
        }

        fun onDragEnd() {
            _uiState.update { it.copy(isRectFinalized = true) }
        }

        fun clearRect() {
            _uiState.update { it.copy(currentDragRect = null, isRectFinalized = false) }
        }

        fun updateCameraError(error: String?) {
            _uiState.update { it.copy(cameraError = error) }
        }

        fun confirmZone(zone: GoalZone) {
            viewModelScope.launch {
                userPreferencesRepository.updateGoalZone(zone)
                sessionRepository.setGoalZone(zone)
                _navEvents.send(SetupNavEvent.NavigateToRecording)
            }
        }

        fun skipZone() {
            viewModelScope.launch {
                userPreferencesRepository.updateGoalZone(GoalZone.FULL_FRAME)
                sessionRepository.setGoalZone(GoalZone.FULL_FRAME)
                _navEvents.send(SetupNavEvent.NavigateToRecording)
            }
        }

        fun keepCurrentZone() {
            viewModelScope.launch {
                _navEvents.send(SetupNavEvent.NavigateToRecording)
            }
        }
    }

data class SetupUiState(
    val currentDragRect: Rect? = null,
    val isRectFinalized: Boolean = false,
    val isReconfiguring: Boolean = false,
    val cameraError: String? = null,
)

sealed class SetupNavEvent {
    data object NavigateToRecording : SetupNavEvent()
}
