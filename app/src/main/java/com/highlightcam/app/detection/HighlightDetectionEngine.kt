package com.highlightcam.app.detection

import com.highlightcam.app.camera.CameraPreviewManager
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.DetectionEvent
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.tracking.AutoFollowConfig
import com.highlightcam.app.tracking.AutoFollowEngine
import com.highlightcam.app.tracking.CropWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightDetectionEngine
    @Inject
    constructor(
        private val tfliteDetector: TFLiteDetector,
        private val goalEventAnalyzer: GoalEventAnalyzer,
        private val audioAnalyzer: AudioAnalyzer,
        private val cameraPreviewManager: CameraPreviewManager,
        private val sessionRepository: SessionRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val autoFollowEngine: AutoFollowEngine,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var collectionJob: Job? = null
        private var goalZoneSet: GoalZoneSet = GoalZoneSet.DEFAULT
        val stateMachine = DetectionStateMachine()

        @Volatile
        var autoFollowConfig: AutoFollowConfig = AutoFollowConfig()

        private val mutableCropWindow = MutableStateFlow(CropWindow.FULL_FRAME)
        val cropWindowFlow: StateFlow<CropWindow> = mutableCropWindow.asStateFlow()

        private val _eventFlow =
            MutableSharedFlow<DetectionEvent>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val eventFlow: SharedFlow<DetectionEvent> = _eventFlow.asSharedFlow()

        private val _debugInfo = MutableStateFlow(DebugInfo())
        val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()

        private val recentInferenceTimes = ArrayDeque<Long>(MAX_INFERENCE_HISTORY)

        fun start(goalZoneSet: GoalZoneSet) {
            this.goalZoneSet = goalZoneSet
            stateMachine.start()
            audioAnalyzer.start()

            collectionJob?.cancel()
            collectionJob =
                scope.launch {
                    launch { collectAudio() }
                    collectFrames()
                }
        }

        fun stop() {
            collectionJob?.cancel()
            collectionJob = null
            audioAnalyzer.stop()
            stateMachine.stop()
            mutableCropWindow.value = CropWindow.FULL_FRAME
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun collectFrames() {
            cameraPreviewManager.frameFlow.collect { bitmap ->
                try {
                    val detections = tfliteDetector.detect(bitmap)
                    val sensitivity = userPreferencesRepository.detectionSensitivity.first()
                    val result = goalEventAnalyzer.analyze(detections, goalZoneSet, sensitivity)

                    trackInferenceTime(tfliteDetector.lastInferenceTimeMs)
                    updateDebugVisual(result)

                    val cfg = autoFollowConfig
                    if (cfg.enabled) {
                        mutableCropWindow.value =
                            autoFollowEngine.computeNextCrop(
                                detections = detections,
                                activeZones = goalZoneSet.activeZones,
                                currentCrop = mutableCropWindow.value,
                                config = cfg,
                            )
                    } else if (mutableCropWindow.value != CropWindow.FULL_FRAME) {
                        mutableCropWindow.value = CropWindow.FULL_FRAME
                    }

                    val action = stateMachine.onVisualResult(result, System.currentTimeMillis())
                    handleAction(action, result.goalZoneId)
                } catch (e: Throwable) {
                    Timber.e(e, "Frame processing error")
                    _eventFlow.tryEmit(
                        DetectionEvent.DetectionError(e.message ?: "Frame processing error"),
                    )
                }
            }
        }

        private suspend fun collectAudio() {
            audioAnalyzer.audioEventFlow.collect { event ->
                stateMachine.lastAudioEvent = event
                updateDebugAudio(event)

                val action = stateMachine.onAudioEvent(event, System.currentTimeMillis())
                handleAction(action, null)
            }
        }

        private fun handleAction(
            action: DetectionAction,
            goalZoneId: String?,
        ) {
            when (action) {
                is DetectionAction.TriggerSave -> {
                    _eventFlow.tryEmit(
                        DetectionEvent.ClipSaveTriggered(
                            confidence = action.confidence,
                            reason = action.reason,
                            goalZoneId = goalZoneId,
                        ),
                    )
                    updateDebugEvent(action.reason)
                }
                is DetectionAction.EmitCandidate -> {
                    _eventFlow.tryEmit(
                        DetectionEvent.CandidateDetected(
                            confidence = action.confidence,
                            goalZoneId = goalZoneId,
                        ),
                    )
                }
                is DetectionAction.None -> {}
            }
            _debugInfo.value =
                _debugInfo.value.copy(
                    stateMachineState = stateMachine.stateLabel,
                    modelAvailable = tfliteDetector.modelAvailable.value,
                )
        }

        private fun trackInferenceTime(ms: Long) {
            if (recentInferenceTimes.size >= MAX_INFERENCE_HISTORY) recentInferenceTimes.removeFirst()
            recentInferenceTimes.addLast(ms)
        }

        private fun updateDebugVisual(result: AnalysisResult) {
            _debugInfo.value =
                _debugInfo.value.copy(
                    ballDetected = result.ballDetected,
                    ballInZone = result.ballInZone,
                    playerCountInZone = result.playerCountInZone,
                    recentInferenceTimesMs = recentInferenceTimes.toList(),
                )
        }

        private fun updateDebugAudio(event: AudioEvent) {
            _debugInfo.value = _debugInfo.value.copy(currentRms = event.currentRms, baselineRms = event.baselineRms)
        }

        private fun updateDebugEvent(reason: String) {
            _debugInfo.value = _debugInfo.value.copy(lastEventTime = System.currentTimeMillis(), lastEventReason = reason)
        }

        companion object {
            private const val MAX_INFERENCE_HISTORY = 10
        }
    }
