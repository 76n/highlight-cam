package com.highlightcam.app.recording

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.highlightcam.app.camera.CameraPreviewManager
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.domain.RecordingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

data class SegmentFile(
    val file: File,
    val startTimeMs: Long,
    val durationMs: Long,
)

@Singleton
class CircularBufferRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val cameraPreviewManager: CameraPreviewManager,
        private val clipAssembler: ClipAssembler,
        private val sessionRepository: SessionRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var segmentJob: Job? = null
        private var activeRecording: androidx.camera.video.Recording? = null
        private val segments = ArrayDeque<SegmentFile>()
        private val segmentLock = Any()

        @Volatile
        private var config: RecordingConfig = RecordingConfig()

        @Volatile
        private var recordingStartedAtMs = 0L

        @Volatile
        private var saveInProgress = false

        val state: StateFlow<RecorderState> = sessionRepository.recorderState

        private val segmentsDir: File
            get() = File(context.cacheDir, "hc_segments").also { it.mkdirs() }

        @SuppressLint("MissingPermission")
        suspend fun start(
            config: RecordingConfig,
            lifecycleOwner: LifecycleOwner,
        ) {
            this.config = config
            recordingStartedAtMs = System.currentTimeMillis()

            val recorder =
                cameraPreviewManager.bindWithVideoCapture(
                    lifecycleOwner,
                    config.videoQuality,
                )

            sessionRepository.updateRecorderState(RecorderState.Recording(recordingStartedAtMs))

            segmentJob =
                scope.launch {
                    segmentLoop(recorder)
                }
        }

        suspend fun stop() {
            segmentJob?.cancelAndJoin()
            segmentJob = null
            activeRecording?.stop()
            activeRecording = null

            synchronized(segmentLock) {
                segments.forEach { it.file.delete() }
                segments.clear()
            }

            sessionRepository.updateRecorderState(RecorderState.Idle)
        }

        suspend fun saveClip(
            secondsBefore: Int,
            secondsAfter: Int,
        ): Result<Uri> {
            val triggerTimeMs = System.currentTimeMillis()
            val earliestNeeded = triggerTimeMs - secondsBefore * 1000L

            val preSegments =
                synchronized(segmentLock) {
                    segments
                        .filter { it.startTimeMs + it.durationMs > earliestNeeded }
                        .toList()
                }

            saveInProgress = true
            sessionRepository.updateRecorderState(RecorderState.SavingClip(0f))

            if (secondsAfter > 0) {
                delay(secondsAfter * 1000L)
            }

            val postSegments =
                synchronized(segmentLock) {
                    segments.filter { it.startTimeMs >= triggerTimeMs }.toList()
                }

            saveInProgress = false

            val allSegments =
                (preSegments + postSegments)
                    .distinctBy { it.file.absolutePath }
                    .sortedBy { it.startTimeMs }

            if (allSegments.isEmpty()) {
                sessionRepository.updateRecorderState(
                    RecorderState.Recording(recordingStartedAtMs),
                )
                return Result.failure(IllegalStateException("No segments available"))
            }

            val segmentDurationsUs = allSegments.map { it.durationMs * 1000L }
            val totalSegmentDurationUs = segmentDurationsUs.sum()
            val desiredDurationUs = (secondsBefore + secondsAfter) * 1_000_000L
            val trimLeadingUs = maxOf(0L, totalSegmentDurationUs - desiredDurationUs)

            val result =
                clipAssembler.assemble(
                    segmentFiles = allSegments.map { it.file },
                    segmentDurationsUs = segmentDurationsUs,
                    trimLeadingUs = trimLeadingUs,
                )

            sessionRepository.updateRecorderState(
                RecorderState.Recording(recordingStartedAtMs),
            )

            result.onSuccess {
                sessionRepository.incrementClipsSaved()
            }

            return result
        }

        @SuppressLint("MissingPermission")
        private suspend fun segmentLoop(recorder: Recorder) {
            while (scope.isActive) {
                val segmentFile = File(segmentsDir, "seg_${System.currentTimeMillis()}.mp4")
                val startTimeMs = System.currentTimeMillis()
                val finalizeDeferred = CompletableDeferred<Unit>()

                val recording =
                    recorder
                        .prepareRecording(
                            context,
                            FileOutputOptions.Builder(segmentFile).build(),
                        )
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(context)) { event ->
                            if (event is VideoRecordEvent.Finalize) {
                                if (event.hasError()) {
                                    Timber.w(
                                        "Segment finalize error: %d",
                                        event.error,
                                    )
                                }
                                finalizeDeferred.complete(Unit)
                            }
                        }

                activeRecording = recording
                delay(config.segmentDurationSeconds * 1000L)
                recording.stop()
                finalizeDeferred.await()

                val durationMs = System.currentTimeMillis() - startTimeMs

                if (segmentFile.exists() && segmentFile.length() > 0) {
                    addSegment(SegmentFile(segmentFile, startTimeMs, durationMs))
                } else {
                    segmentFile.delete()
                }
            }
        }

        private fun addSegment(segment: SegmentFile) {
            synchronized(segmentLock) {
                segments.addLast(segment)
                trimBuffer()
            }
        }

        private fun trimBuffer() {
            val maxSize =
                if (saveInProgress) config.bufferSegments * 2 else config.bufferSegments
            while (segments.size > maxSize) {
                val oldest = segments.removeFirst()
                oldest.file.delete()
            }
        }
    }
