package com.highlightcam.app.recording

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.highlightcam.app.camera.CameraPreviewManager
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.tracking.CropWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.ArrayDeque
import java.util.Collections
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
        private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val savingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var segmentJob: Job? = null
        private var activeRecording: androidx.camera.video.Recording? = null
        private val segments = ArrayDeque<SegmentFile>()
        private val segmentMutex = Mutex()
        private val protectedFiles: MutableSet<File> =
            Collections.synchronizedSet(mutableSetOf())

        @Volatile
        private var config: RecordingConfig = RecordingConfig()

        @Volatile
        private var recordingStartedAtMs = 0L

        private val isSaving = AtomicBoolean(false)

        var cropWindowProvider: (() -> CropWindow)? = null

        private val _saveErrorFlow =
            MutableSharedFlow<String>(extraBufferCapacity = 1)
        val saveErrorFlow: SharedFlow<String> = _saveErrorFlow.asSharedFlow()

        val state: StateFlow<RecorderState> = sessionRepository.recorderState

        private val segmentsDir: File
            get() = File(context.cacheDir, "hc_segments").also { it.mkdirs() }

        @SuppressLint("MissingPermission")
        fun start(config: RecordingConfig) {
            this.config = config
            recordingStartedAtMs = System.currentTimeMillis()

            val recorder = cameraPreviewManager.getRecorder()

            sessionRepository.updateRecorderState(RecorderState.Recording(recordingStartedAtMs))

            segmentJob =
                recordingScope.launch {
                    segmentLoop(recorder)
                }
        }

        suspend fun stop() {
            segmentJob?.cancelAndJoin()
            segmentJob = null
            activeRecording?.stop()
            activeRecording = null

            segmentMutex.withLock {
                segments.forEach { it.file.delete() }
                segments.clear()
            }

            sessionRepository.updateRecorderState(RecorderState.Idle)
        }

        fun requestSave(
            secondsBefore: Int,
            secondsAfter: Int,
        ) {
            if (!isSaving.compareAndSet(false, true)) {
                Timber.w("Save already in progress, ignoring")
                return
            }

            savingScope.launch {
                try {
                    val result = executeSave(secondsBefore, secondsAfter)
                    result.onSuccess { sessionRepository.incrementClipsSaved() }
                    result.onFailure { e ->
                        Timber.e(e, "Clip save failed")
                        _saveErrorFlow.tryEmit(e.message ?: "Save failed")
                    }
                } catch (e: Throwable) {
                    Timber.e(e, "Unexpected error in save coroutine")
                    _saveErrorFlow.tryEmit(e.message ?: "Save failed")
                } finally {
                    isSaving.set(false)
                    if (recordingStartedAtMs > 0L) {
                        sessionRepository.updateRecorderState(
                            RecorderState.Recording(recordingStartedAtMs),
                        )
                    }
                }
            }
        }

        @Suppress("ReturnCount")
        private suspend fun executeSave(
            secondsBefore: Int,
            secondsAfter: Int,
        ): Result<Uri> {
            sessionRepository.updateRecorderState(RecorderState.SavingClip(0f))

            val triggerTimeMs = System.currentTimeMillis()
            val cropSnapshots = mutableListOf<Pair<Long, CropWindow>>()

            if (secondsAfter > 0) {
                val afterMs = secondsAfter * 1000L
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < afterMs) {
                    cropWindowProvider?.invoke()?.let { cw ->
                        cropSnapshots.add(System.currentTimeMillis() to cw)
                    }
                    delay(CROP_SNAPSHOT_INTERVAL_MS)
                }
            }

            val clipStartTimeMs = triggerTimeMs - secondsBefore * 1000L
            val clipEndTimeMs = triggerTimeMs + secondsAfter * 1000L

            val allSegments = waitForSegments(clipStartTimeMs, clipEndTimeMs)

            if (allSegments.isEmpty()) {
                Timber.w(
                    "No segments available after waiting [%d before, %d after]",
                    secondsBefore,
                    secondsAfter,
                )
                return Result.failure(IllegalStateException("No segments after waiting"))
            }

            allSegments.forEach { protectedFiles.add(it.file) }

            try {
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

                val hasNonTrivialCrop =
                    cropSnapshots.any { it.second != CropWindow.FULL_FRAME }
                val timeline = if (hasNonTrivialCrop) cropSnapshots else null

                return clipAssembler.assemble(
                    segmentFiles = allSegments.map { it.file },
                    segmentDurationsUs = segmentDurationsUs,
                    trimLeadingUs = trimLeadingUs,
                    maxDurationUs = desiredDurationUs,
                    cropTimeline = timeline,
                )
            } finally {
                allSegments.forEach { protectedFiles.remove(it.file) }
            }
        }

        private suspend fun waitForSegments(
            clipStartTimeMs: Long,
            clipEndTimeMs: Long,
        ): List<SegmentFile> {
            repeat(MAX_SEGMENT_WAIT_ATTEMPTS) {
                val snapshot =
                    segmentMutex.withLock {
                        segments
                            .filter { seg ->
                                seg.startTimeMs + seg.durationMs > clipStartTimeMs &&
                                    seg.startTimeMs < clipEndTimeMs &&
                                    seg.file.exists() &&
                                    seg.file.length() > 0
                            }
                            .toList()
                    }
                if (snapshot.isNotEmpty()) return snapshot
                delay(SEGMENT_WAIT_INTERVAL_MS)
            }
            return emptyList()
        }

        @SuppressLint("MissingPermission")
        private suspend fun segmentLoop(recorder: Recorder) {
            while (recordingScope.isActive) {
                try {
                    recordOneSegment(recorder)
                } catch (e: Throwable) {
                    Timber.e(e, "Segment recording failed, retrying")
                    delay(SEGMENT_RETRY_DELAY_MS)
                }
            }
        }

        @SuppressLint("MissingPermission")
        private suspend fun recordOneSegment(recorder: Recorder) {
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
                                Timber.w("Segment finalize error: %d", event.error)
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
                segmentMutex.withLock {
                    segments.addLast(SegmentFile(segmentFile, startTimeMs, durationMs))
                    trimBuffer()
                }
            } else {
                segmentFile.delete()
            }
        }

        private fun trimBuffer() {
            val maxSize =
                if (isSaving.get()) config.bufferSegments * 2 else config.bufferSegments
            while (segments.size > maxSize) {
                val oldest = segments.first()
                if (protectedFiles.contains(oldest.file)) break
                segments.removeFirst()
                oldest.file.delete()
            }
        }

        companion object {
            private const val SEGMENT_RETRY_DELAY_MS = 500L
            private const val SEGMENT_WAIT_INTERVAL_MS = 200L
            private const val MAX_SEGMENT_WAIT_ATTEMPTS = 50
            private const val CROP_SNAPSHOT_INTERVAL_MS = 100L
        }
    }
