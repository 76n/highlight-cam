package com.highlightcam.app.detection

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TFLiteDetector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Closeable {
        private var interpreter: org.tensorflow.lite.Interpreter? = null
        private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null
        private val mutex = Mutex()

        private val _modelAvailable = MutableStateFlow(false)
        val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

        @Volatile
        var lastInferenceTimeMs: Long = 0L
            private set

        private var frameCount = 0

        init {
            loadModel()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadModel() {
            try {
                val modelBuffer = loadAsset(MODEL_FILENAME)
                val options = org.tensorflow.lite.Interpreter.Options()

                try {
                    gpuDelegate = org.tensorflow.lite.gpu.GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    Timber.d("TFLite GPU delegate initialized")
                } catch (e: Throwable) {
                    Timber.w("GPU delegate unavailable, falling back to CPU: %s", e.message)
                    gpuDelegate = null
                }

                options.setNumThreads(NUM_THREADS)
                interpreter = org.tensorflow.lite.Interpreter(modelBuffer, options)
                _modelAvailable.value = true
                Timber.i("TFLite model loaded: %s", MODEL_FILENAME)
            } catch (e: Throwable) {
                Timber.w(e, "TFLite model not available")
                _modelAvailable.value = false
            }
        }

        @Suppress("MagicNumber")
        suspend fun detect(bitmap: Bitmap): List<Detection> =
            withContext(Dispatchers.Default) {
                val interp = interpreter ?: return@withContext emptyList()

                mutex.withLock {
                    val startTime = System.currentTimeMillis()

                    val scaled =
                        Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
                    val inputBuffer = bitmapToByteBuffer(scaled)
                    if (scaled !== bitmap) scaled.recycle()

                    val output = Array(1) { Array(OUTPUT_CLASSES) { FloatArray(OUTPUT_ANCHORS) } }
                    interp.run(inputBuffer, output)

                    lastInferenceTimeMs = System.currentTimeMillis() - startTime
                    frameCount++
                    if (frameCount % LOG_INTERVAL == 0) {
                        Timber.v("Inference time: %dms (frame %d)", lastInferenceTimeMs, frameCount)
                    }

                    parseAndNms(output[0])
                }
            }

        @Suppress("MagicNumber")
        private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
            val buffer =
                ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            buffer.order(ByteOrder.nativeOrder())
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (pixel in pixels) {
                buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                buffer.putFloat((pixel and 0xFF) / 255f)
            }
            buffer.rewind()
            return buffer
        }

        @Suppress("MagicNumber")
        private fun parseAndNms(output: Array<FloatArray>): List<Detection> {
            val rawDetections = mutableListOf<Detection>()

            for (i in 0 until OUTPUT_ANCHORS) {
                val cx = output[0][i]
                val cy = output[1][i]
                val w = output[2][i]
                val h = output[3][i]

                var maxScore = 0f
                var maxClass = 0
                for (c in 0 until NUM_CLASSES) {
                    val score = output[4 + c][i]
                    if (score > maxScore) {
                        maxScore = score
                        maxClass = c
                    }
                }

                if (maxScore < CONF_THRESHOLD) continue
                if (maxClass != GoalEventAnalyzer.CLASS_PERSON &&
                    maxClass != GoalEventAnalyzer.CLASS_SPORTS_BALL
                ) {
                    continue
                }

                val left = ((cx - w / 2f) / INPUT_SIZE).coerceIn(0f, 1f)
                val top = ((cy - h / 2f) / INPUT_SIZE).coerceIn(0f, 1f)
                val right = ((cx + w / 2f) / INPUT_SIZE).coerceIn(0f, 1f)
                val bottom = ((cy + h / 2f) / INPUT_SIZE).coerceIn(0f, 1f)

                rawDetections.add(
                    Detection(
                        classId = maxClass,
                        confidence = maxScore,
                        boundingBox = BoundingBox(left, top, right, bottom),
                    ),
                )
            }

            return nms(rawDetections, NMS_IOU_THRESHOLD)
        }

        private fun loadAsset(filename: String): MappedByteBuffer {
            val fd = context.assets.openFd(filename)
            val inputStream = FileInputStream(fd.fileDescriptor)
            return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength,
            )
        }

        override fun close() {
            interpreter?.close()
            interpreter = null
            gpuDelegate?.close()
            gpuDelegate = null
            _modelAvailable.value = false
        }

        companion object {
            const val MODEL_FILENAME = "yolov8n_float16.tflite"
            const val INPUT_SIZE = 320
            const val OUTPUT_CLASSES = 84
            const val OUTPUT_ANCHORS = 8400
            const val NUM_CLASSES = 80
            const val CONF_THRESHOLD = 0.25f
            const val NMS_IOU_THRESHOLD = 0.45f
            const val NUM_THREADS = 4
            const val LOG_INTERVAL = 10

            fun nms(
                detections: List<Detection>,
                iouThreshold: Float,
            ): List<Detection> {
                val sorted = detections.sortedByDescending { it.confidence }
                val selected = mutableListOf<Detection>()
                val suppressed = BooleanArray(sorted.size)

                for (i in sorted.indices) {
                    if (suppressed[i]) continue
                    selected.add(sorted[i])
                    for (j in i + 1 until sorted.size) {
                        if (suppressed[j]) continue
                        if (iou(sorted[i].boundingBox, sorted[j].boundingBox) > iouThreshold) {
                            suppressed[j] = true
                        }
                    }
                }
                return selected
            }

            fun iou(
                a: BoundingBox,
                b: BoundingBox,
            ): Float {
                val x1 = maxOf(a.left, b.left)
                val y1 = maxOf(a.top, b.top)
                val x2 = minOf(a.right, b.right)
                val y2 = minOf(a.bottom, b.bottom)
                val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
                val areaA = (a.right - a.left) * (a.bottom - a.top)
                val areaB = (b.right - b.left) * (b.bottom - b.top)
                val union = areaA + areaB - intersection
                return if (union > 0f) intersection / union else 0f
            }
        }
    }
