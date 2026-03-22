package com.highlightcam.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaActionSound
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.highlightcam.app.MainActivity
import com.highlightcam.app.R
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.detection.AudioAnalyzer
import com.highlightcam.app.detection.HighlightDetectionEngine
import com.highlightcam.app.domain.DetectionEvent
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.recording.CircularBufferRecorder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : LifecycleService() {
    @Inject lateinit var circularBufferRecorder: CircularBufferRecorder

    @Inject lateinit var sessionRepository: SessionRepository

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject lateinit var highlightDetectionEngine: HighlightDetectionEngine

    @Inject lateinit var audioAnalyzer: AudioAnalyzer

    private var wakeLock: PowerManager.WakeLock? = null
    private var detectionJob: Job? = null
    private var mediaActionSound: MediaActionSound? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            ACTION_SAVE_CLIP -> handleSaveClip(intent)
        }
        return START_STICKY
    }

    @android.annotation.SuppressLint("InlinedApi")
    private fun handleStart(intent: Intent) {
        val qualityName = intent.getStringExtra(EXTRA_QUALITY) ?: VideoQuality.HD_720.name
        val quality = runCatching { VideoQuality.valueOf(qualityName) }.getOrDefault(VideoQuality.HD_720)

        mediaActionSound =
            MediaActionSound().also {
                it.load(MediaActionSound.START_VIDEO_RECORDING)
            }

        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        acquireWakeLock()

        lifecycleScope.launch {
            try {
                val config = userPreferencesRepository.recordingConfig.first().copy(videoQuality = quality)
                circularBufferRecorder.start(config, this@RecordingService)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start recording")
                sessionRepository.updateRecorderState(RecorderState.Error(e.message ?: "Recording failed"))
            }
        }

        lifecycleScope.launch {
            val zoneSet = sessionRepository.goalZoneSet.value ?: GoalZoneSet.DEFAULT
            highlightDetectionEngine.start(zoneSet)
        }

        detectionJob =
            lifecycleScope.launch {
                try {
                    highlightDetectionEngine.eventFlow.collect { event ->
                        when (event) {
                            is DetectionEvent.ClipSaveTriggered -> {
                                try {
                                    playSaveSound()
                                    val config = userPreferencesRepository.recordingConfig.first()
                                    circularBufferRecorder.requestSave(config.totalBufferSeconds, config.secondsAfterEvent)
                                } catch (e: Exception) {
                                    Timber.e(e, "Error during auto-save")
                                }
                            }
                            is DetectionEvent.CandidateDetected -> {}
                            is DetectionEvent.DetectionError -> Timber.e("Detection error: %s", event.message)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Detection event collection failed")
                }
            }
    }

    private fun handleStop() {
        detectionJob?.cancel()
        detectionJob = null
        try {
            highlightDetectionEngine.stop()
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop detection engine")
        }

        lifecycleScope.launch {
            try {
                circularBufferRecorder.stop()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop recording")
            }
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleSaveClip(intent: Intent) {
        playSaveSound()
        val secondsBefore = intent.getIntExtra(EXTRA_SECONDS_BEFORE, RecordingConfig().totalBufferSeconds)
        val secondsAfter = intent.getIntExtra(EXTRA_SECONDS_AFTER, RecordingConfig().secondsAfterEvent)
        circularBufferRecorder.requestSave(secondsBefore, secondsAfter)
    }

    private fun playSaveSound() {
        try {
            mediaActionSound?.play(MediaActionSound.START_VIDEO_RECORDING)
        } catch (e: Exception) {
            Timber.w(e, "Failed to play save sound")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HighlightCam::Recording").apply { acquire() }
    }

    @Suppress("MagicNumber")
    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active recording notification"
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID,
        ).setSmallIcon(
            R.drawable.ic_notification,
        ).setContentTitle(
            getString(R.string.notification_recording_title),
        ).setContentText(getString(R.string.notification_recording_text)).setOngoing(true).setContentIntent(pendingIntent).build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        handleStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handleStop()
        try {
            audioAnalyzer.stop()
        } catch (e: Throwable) {
            Timber.w(e, "AudioAnalyzer stop error")
        }
        try {
            mediaActionSound?.release()
        } catch (e: Throwable) {
            Timber.w(e, "MediaActionSound release error")
        }
        mediaActionSound = null
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.highlightcam.app.action.START"
        const val ACTION_STOP = "com.highlightcam.app.action.STOP"
        const val ACTION_SAVE_CLIP = "com.highlightcam.app.action.SAVE_CLIP"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_SECONDS_BEFORE = "extra_seconds_before"
        const val EXTRA_SECONDS_AFTER = "extra_seconds_after"
        private const val CHANNEL_ID = "hc_recording_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
