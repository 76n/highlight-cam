package com.highlightcam.app.ui.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.detection.DebugInfo
import com.highlightcam.app.detection.HighlightDetectionEngine
import com.highlightcam.app.detection.TFLiteDetector
import com.highlightcam.app.domain.DetectionEvent
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val sessionRepository: SessionRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val highlightDetectionEngine: HighlightDetectionEngine,
        private val tfliteDetector: TFLiteDetector,
    ) : ViewModel() {
        val recorderState: StateFlow<RecorderState> = sessionRepository.recorderState
        val clipsSaved: StateFlow<Int> = sessionRepository.clipsSavedThisSession
        val goalZone: StateFlow<GoalZone?> = sessionRepository.goalZone
        val modelAvailable: StateFlow<Boolean> = tfliteDetector.modelAvailable
        val debugInfo: StateFlow<DebugInfo> = highlightDetectionEngine.debugInfo

        val debugModeEnabled: StateFlow<Boolean> =
            userPreferencesRepository.debugModeEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

        val soundOnSave: StateFlow<Boolean> =
            userPreferencesRepository.soundOnSave
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

        private val _lastClipUri = MutableStateFlow<Uri?>(null)
        val lastClipUri: StateFlow<Uri?> = _lastClipUri.asStateFlow()

        private val _candidateDetected = MutableStateFlow(false)
        val candidateDetected: StateFlow<Boolean> = _candidateDetected.asStateFlow()

        private val _lowStorageWarning = MutableStateFlow(false)
        val lowStorageWarning: StateFlow<Boolean> = _lowStorageWarning.asStateFlow()

        private var candidateTimeoutJob: Job? = null

        init {
            viewModelScope.launch {
                RecordingService.clipResultFlow.collect { result ->
                    result.onSuccess { uri -> _lastClipUri.value = uri }
                }
            }

            viewModelScope.launch {
                val zone = userPreferencesRepository.goalZone.first()
                if (zone != null) sessionRepository.setGoalZone(zone)
            }

            viewModelScope.launch {
                highlightDetectionEngine.eventFlow.collect { event ->
                    if (event is DetectionEvent.CandidateDetected) {
                        _candidateDetected.value = true
                        candidateTimeoutJob?.cancel()
                        candidateTimeoutJob =
                            viewModelScope.launch {
                                delay(CANDIDATE_DISPLAY_MS)
                                _candidateDetected.value = false
                            }
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        fun startRecording() {
            viewModelScope.launch {
                try {
                    val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
                    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                    _lowStorageWarning.value = availableBytes < LOW_STORAGE_BYTES
                } catch (_: Exception) {
                    _lowStorageWarning.value = false
                }

                val config = userPreferencesRepository.recordingConfig.first()
                val intent =
                    Intent(appContext, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_START
                        putExtra(RecordingService.EXTRA_QUALITY, config.videoQuality.name)
                    }
                appContext.startForegroundService(intent)
            }
        }

        fun stopRecording() {
            _lowStorageWarning.value = false
            val intent =
                Intent(appContext, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP
                }
            appContext.startService(intent)
        }

        fun requestManualSave() {
            viewModelScope.launch {
                val config = userPreferencesRepository.recordingConfig.first()
                val intent =
                    Intent(appContext, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_SAVE_CLIP
                        putExtra(RecordingService.EXTRA_SECONDS_BEFORE, config.totalBufferSeconds)
                        putExtra(RecordingService.EXTRA_SECONDS_AFTER, config.secondsAfterEvent)
                    }
                appContext.startService(intent)
            }
        }

        companion object {
            private const val CANDIDATE_DISPLAY_MS = 1500L
            private const val LOW_STORAGE_BYTES = 500L * 1024 * 1024
        }
    }
