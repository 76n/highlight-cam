package com.highlightcam.app.ui.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val sessionRepository: SessionRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModel() {
        val recorderState: StateFlow<RecorderState> = sessionRepository.recorderState
        val clipsSaved: StateFlow<Int> = sessionRepository.clipsSavedThisSession
        val goalZone: StateFlow<GoalZone?> = sessionRepository.goalZone

        private val _lastClipUri = MutableStateFlow<Uri?>(null)
        val lastClipUri: StateFlow<Uri?> = _lastClipUri.asStateFlow()

        init {
            viewModelScope.launch {
                RecordingService.clipResultFlow.collect { result ->
                    result.onSuccess { uri ->
                        _lastClipUri.value = uri
                    }
                }
            }

            viewModelScope.launch {
                val zone = userPreferencesRepository.goalZone.first()
                if (zone != null) {
                    sessionRepository.setGoalZone(zone)
                }
            }
        }

        fun startRecording() {
            viewModelScope.launch {
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
                        putExtra(
                            RecordingService.EXTRA_SECONDS_BEFORE,
                            config.totalBufferSeconds,
                        )
                        putExtra(
                            RecordingService.EXTRA_SECONDS_AFTER,
                            config.secondsAfterEvent,
                        )
                    }
                appContext.startService(intent)
            }
        }
    }
