package com.highlightcam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraPreviewManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _frameFlow =
            MutableSharedFlow<Bitmap>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val frameFlow: SharedFlow<Bitmap> = _frameFlow.asSharedFlow()

        private val bindMutex = Mutex()
        private var isBound = false

        val isCameraBound: Boolean get() = isBound

        private val preview = Preview.Builder().build()

        @Suppress("DEPRECATION")
        private val imageAnalysis =
            ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

        val recorder: Recorder =
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build()

        val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

        @Volatile
        var lastFrameTimeMs = 0L
            private set

        private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val instanceHash: Int = System.identityHashCode(this)

        init {
            Timber.d("CameraPreviewManager instance created: %x", instanceHash)
        }

        suspend fun bindOnce(owner: LifecycleOwner) {
            if (isBound) return
            bindMutex.withLock {
                if (isBound) return
                try {
                    val provider = getProvider()
                    provider.unbindAll()
                    val camera =
                        provider.bindToLifecycle(
                            owner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                            videoCapture,
                        )
                    camera.cameraControl.setZoomRatio(1.0f)

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastFrameTimeMs >= FRAME_THROTTLE_MS) {
                            lastFrameTimeMs = now
                            _frameFlow.tryEmit(proxy.toBitmap())
                        }
                        proxy.close()
                    }

                    isBound = true
                    _error.value = null
                    Timber.d("Camera bound to Activity lifecycle")

                    startWatchdog()
                } catch (e: Exception) {
                    Timber.e(e, "Camera bind failed")
                    _error.value = e.message ?: "Camera bind failed"
                }
            }
        }

        fun attachSurface(surfaceProvider: Preview.SurfaceProvider) {
            preview.setSurfaceProvider(surfaceProvider)
        }

        fun detachSurface() {
            preview.setSurfaceProvider(null)
        }

        private fun startWatchdog() {
            watchdogScope.launch {
                while (true) {
                    delay(WATCHDOG_INTERVAL_MS)
                    if (isBound) {
                        val elapsed = System.currentTimeMillis() - lastFrameTimeMs
                        if (elapsed > WATCHDOG_STALE_THRESHOLD_MS) {
                            Timber.e(
                                "CAMERA WATCHDOG: no frames for %dms, surface may be detached",
                                elapsed,
                            )
                        }
                    }
                }
            }
        }

        private suspend fun getProvider(): ProcessCameraProvider =
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        try {
                            continuation.resume(future.get())
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }

        companion object {
            private const val FRAME_THROTTLE_MS = 300L
            private const val WATCHDOG_INTERVAL_MS = 2000L
            private const val WATCHDOG_STALE_THRESHOLD_MS = 5000L
        }
    }
