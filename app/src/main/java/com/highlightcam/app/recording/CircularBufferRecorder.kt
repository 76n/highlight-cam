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
import java.util.concurrent.atomic.AtomicBoolean
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
        private val protectedPaths = mutableSetOf<String>()

        @Volatile
        private var config: RecordingConfig = RecordingConfig()

        @Volatile
        private var recordingStartedAtMs = 0L

        private val isSaving = AtomicBoolean(false)

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

        @Suppress("ReturnCount")
        suspend fun saveClip(
            secondsBefore: Int,
            secondsAfter: Int,
        ): Result<Uri> {
            if (!isSaving.compareAndSet(false, true)) {
                Timber.w("Save already in progress, ignoring")
                return Result.failure(IllegalStateException("Save already in progress"))
            }

            try {
                sessionRepository.updateRecorderState(RecorderState.SavingClip(0f))

                val triggerTimeMs = System.currentTimeMillis()

                if (secondsAfter > 0) {
                    delay(secondsAfter * 1000L)
                }

                val clipStartTimeMs = triggerTimeMs - secondsBefore * 1000L
                val clipEndTimeMs = triggerTimeMs + secondsAfter * 1000L

                val allSegments =
                    synchronized(segmentLock) {
                        val selected =
                            segments
                                .filter { seg ->
                                    seg.startTimeMs + seg.durationMs > clipStartTimeMs &&
                                        seg.startTimeMs < clipEndTimeMs &&
                                        seg.file.exists() &&
                                        seg.file.length() > 0
                                }
                                .toList()
                        selected.forEach { protectedPaths.add(it.file.absolutePath) }
                        selected
                    }

                if (allSegments.isEmpty()) {
                    Timber.w(
                        "No segments available for clip [%d before, %d after]",
                        secondsBefore,
                        secondsAfter,
                    )
                    return Result.failure(IllegalStateException("No segments available"))
                }

                val actualPreMs = triggerTimeMs - allSegments.first().startTimeMs
                if (actualPreMs < secondsBefore * 1000L) {
                    Timber.w(
                        "Only %.1fs of pre-trigger footage available, requested %ds",
                        actualPreMs / 1000.0,
                        secondsBefore,
                    )
                }

                val trimLeadingUs =
                    maxOf(0L, clipStartTimeMs - allSegments.first().startTimeMs) * 1000L
                val desiredDurationUs =
                    (secondsBefore.toLong() + secondsAfter.toLong()) * 1_000_000L
                val segmentDurationsUs = allSegments.map { it.durationMs * 1000L }

                val result =
                    clipAssembler.assemble(
                        segmentFiles = allSegments.map { it.file },
                        segmentDurationsUs = segmentDurationsUs,
                        trimLeadingUs = trimLeadingUs,
                        maxDurationUs = desiredDurationUs,
                    )

                result.onSuccess {
                    sessionRepository.incrementClipsSaved()
                }

                return result
            } catch (e: Throwable) {
                Timber.e(e, "Error saving clip")
                return Result.failure(e)
            } finally {
                synchronized(segmentLock) { protectedPaths.clear() }
                isSaving.set(false)
                sessionRepository.updateRecorderState(
                    RecorderState.Recording(recordingStartedAtMs),
                )
            }
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
                if (isSaving.get()) config.bufferSegments * 2 else config.bufferSegments
            while (segments.size > maxSize) {
                val oldest = segments.first()
                if (oldest.file.absolutePath in protectedPaths) break
                segments.removeFirst()
                oldest.file.delete()
            }
        }
    }
