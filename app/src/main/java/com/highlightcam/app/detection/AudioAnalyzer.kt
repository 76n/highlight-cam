package com.highlightcam.app.detection

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class AudioAnalyzer
    @Inject
    constructor() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var recordJob: Job? = null
        private var audioRecord: AudioRecord? = null

        private val _audioEventFlow =
            MutableSharedFlow<AudioEvent>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val audioEventFlow: SharedFlow<AudioEvent> = _audioEventFlow.asSharedFlow()

        @Volatile
        private var baselineRms: Float = INITIAL_BASELINE

        @Volatile
        private var whistleConsecutiveFrames: Int = 0

        @SuppressLint("MissingPermission")
        fun start() {
            if (recordJob?.isActive == true) return

            val minBuf =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
                Timber.e("AudioRecord buffer size error")
                return
            }

            val bufferSize = minBuf * BUFFER_MULTIPLIER

            val record =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord init failed")
                record.release()
                return
            }

            audioRecord = record
            baselineRms = INITIAL_BASELINE
            whistleConsecutiveFrames = 0

            recordJob =
                scope.launch {
                    record.startRecording()
                    val chunkSize = SAMPLE_RATE / PROCESS_HZ
                    val chunk = ShortArray(chunkSize)

                    while (isActive) {
                        val read = record.read(chunk, 0, chunkSize)
                        if (read <= 0) continue

                        val rms = computeRms(chunk, read)
                        baselineRms = baselineRms * (1f - EMA_ALPHA) + rms * EMA_ALPHA
                        val energySpike =
                            rms > baselineRms * SPIKE_MULTIPLIER && rms > ABSOLUTE_FLOOR

                        val whistleDetected = checkWhistle(chunk, read, rms)

                        _audioEventFlow.tryEmit(
                            AudioEvent(
                                energySpike = energySpike,
                                whistleDetected = whistleDetected,
                                currentRms = rms,
                                baselineRms = baselineRms,
                            ),
                        )
                    }
                }
        }

        fun stop() {
            recordJob?.cancel()
            recordJob = null
            audioRecord?.let {
                it.stop()
                it.release()
            }
            audioRecord = null
        }

        private fun computeRms(
            samples: ShortArray,
            count: Int,
        ): Float {
            var sum = 0.0
            for (i in 0 until count) {
                val normalized = samples[i].toFloat() / Short.MAX_VALUE
                sum += normalized * normalized
            }
            return sqrt(sum / count).toFloat()
        }

        @Suppress("MagicNumber")
        private fun checkWhistle(
            samples: ShortArray,
            count: Int,
            totalRms: Float,
        ): Boolean {
            if (totalRms < ABSOLUTE_FLOOR) {
                whistleConsecutiveFrames = 0
                return false
            }

            val totalPower = totalRms * totalRms
            val power2500 = Goertzel.power(samples, SAMPLE_RATE, 2500f, count)
            val power3200 = Goertzel.power(samples, SAMPLE_RATE, 3200f, count)
            val whistlePower = maxOf(power2500, power3200)
            val ratio = if (totalPower > 0f) whistlePower / (totalPower * count) else 0f

            if (ratio > WHISTLE_RATIO_THRESHOLD) {
                whistleConsecutiveFrames++
            } else {
                whistleConsecutiveFrames = 0
            }

            return whistleConsecutiveFrames in WHISTLE_MIN_FRAMES..WHISTLE_MAX_FRAMES
        }

        companion object {
            const val SAMPLE_RATE = 44100
            const val BUFFER_MULTIPLIER = 4
            const val PROCESS_HZ = 10
            const val EMA_ALPHA = 0.02f
            const val SPIKE_MULTIPLIER = 2.8f
            const val ABSOLUTE_FLOOR = 0.01f
            const val INITIAL_BASELINE = 0.01f
            const val WHISTLE_RATIO_THRESHOLD = 0.55f
            const val WHISTLE_MIN_FRAMES = 1
            const val WHISTLE_MAX_FRAMES = 3
        }
    }

object Goertzel {
    fun power(
        samples: ShortArray,
        sampleRate: Int,
        targetFreqHz: Float,
        numSamples: Int,
    ): Float {
        val k = Math.round(numSamples.toFloat() * targetFreqHz / sampleRate)
        val w = 2.0 * Math.PI * k / numSamples
        val coeff = (2.0 * Math.cos(w)).toFloat()

        var s1 = 0f
        var s2 = 0f

        for (i in 0 until numSamples) {
            val normalized = samples[i].toFloat() / Short.MAX_VALUE
            val s = normalized + coeff * s1 - s2
            s2 = s1
            s1 = s
        }

        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }
}
