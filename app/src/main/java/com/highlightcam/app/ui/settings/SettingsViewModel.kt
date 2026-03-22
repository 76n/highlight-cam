package com.highlightcam.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModel() {
        val sensitivity: StateFlow<Float> =
            userPreferencesRepository.detectionSensitivity
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.5f)

        val recordingConfig: StateFlow<RecordingConfig> =
            userPreferencesRepository.recordingConfig
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), RecordingConfig())

        val goalZoneSet: StateFlow<GoalZoneSet?> =
            userPreferencesRepository.goalZoneSet
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

        val debugMode: StateFlow<Boolean> =
            userPreferencesRepository.debugModeEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

        val soundOnSave: StateFlow<Boolean> =
            userPreferencesRepository.soundOnSave
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

        fun updateSensitivity(value: Float) {
            viewModelScope.launch { userPreferencesRepository.updateDetectionSensitivity(value) }
        }

        fun updateSecondsBefore(value: Int) {
            viewModelScope.launch {
                val current = recordingConfig.value
                userPreferencesRepository.updateRecordingConfig(current.copy(bufferSegments = value / current.segmentDurationSeconds))
            }
        }

        fun updateSecondsAfter(value: Int) {
            viewModelScope.launch { userPreferencesRepository.updateRecordingConfig(recordingConfig.value.copy(secondsAfterEvent = value)) }
        }

        fun updateQuality(quality: VideoQuality) {
            viewModelScope.launch { userPreferencesRepository.updateRecordingConfig(recordingConfig.value.copy(videoQuality = quality)) }
        }

        fun updateDebugMode(enabled: Boolean) {
            viewModelScope.launch { userPreferencesRepository.updateDebugMode(enabled) }
        }

        fun updateSoundOnSave(enabled: Boolean) {
            viewModelScope.launch { userPreferencesRepository.updateSoundOnSave(enabled) }
        }

        companion object {
            const val SENSITIVITY_CAREFUL = 0.2f
            const val SENSITIVITY_BALANCED = 0.5f
            const val SENSITIVITY_AGGRESSIVE = 0.8f
        }
    }
