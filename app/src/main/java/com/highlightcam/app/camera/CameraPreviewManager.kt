package com.highlightcam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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
        private var cameraProvider: ProcessCameraProvider? = null

        private val _frameFlow =
            MutableSharedFlow<Bitmap>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val frameFlow: SharedFlow<Bitmap> = _frameFlow.asSharedFlow()

        private val _cameraError = MutableStateFlow<String?>(null)
        val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

        @Volatile
        private var lastFrameTimeMs = 0L

        @Suppress("MagicNumber", "DEPRECATION")
        suspend fun bindToLifecycle(
            lifecycleOwner: LifecycleOwner,
            surfaceProvider: Preview.SurfaceProvider,
        ) {
            try {
                val provider = getProvider()
                cameraProvider = provider

                val preview =
                    Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(surfaceProvider) }

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTimeMs >= FRAME_THROTTLE_MS) {
                        lastFrameTimeMs = now
                        _frameFlow.tryEmit(proxy.toBitmap())
                    }
                    proxy.close()
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                _cameraError.value = null
            } catch (e: Exception) {
                Timber.e(e, "Camera bind failed")
                _cameraError.value = e.message ?: "Camera initialization failed"
            }
        }

        fun unbind() {
            cameraProvider?.unbindAll()
            cameraProvider = null
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
        }
    }
