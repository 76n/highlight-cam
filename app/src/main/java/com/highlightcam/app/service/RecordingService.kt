package com.highlightcam.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.highlightcam.app.MainActivity
import com.highlightcam.app.R
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.recording.CircularBufferRecorder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : LifecycleService() {
    @Inject lateinit var circularBufferRecorder: CircularBufferRecorder

    @Inject lateinit var sessionRepository: SessionRepository

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    private var wakeLock: PowerManager.WakeLock? = null

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
        val quality =
            runCatching { VideoQuality.valueOf(qualityName) }
                .getOrDefault(VideoQuality.HD_720)

        createNotificationChannel()
        val notification = buildNotification()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        acquireWakeLock()

        lifecycleScope.launch {
            try {
                val config =
                    userPreferencesRepository.recordingConfig.first().copy(
                        videoQuality = quality,
                    )
                circularBufferRecorder.start(config, this@RecordingService)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start recording")
                sessionRepository.updateRecorderState(
                    RecorderState.Error(e.message ?: "Recording failed"),
                )
            }
        }
    }

    private fun handleStop() {
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
        val secondsBefore =
            intent.getIntExtra(EXTRA_SECONDS_BEFORE, RecordingConfig().totalBufferSeconds)
        val secondsAfter =
            intent.getIntExtra(EXTRA_SECONDS_AFTER, RecordingConfig().secondsAfterEvent)

        lifecycleScope.launch {
            val result = circularBufferRecorder.saveClip(secondsBefore, secondsAfter)
            _clipResultFlow.tryEmit(result)

            result.onFailure { e ->
                Timber.e(e, "Failed to save clip")
                sessionRepository.updateRecorderState(
                    RecorderState.Error(e.message ?: "Clip save failed"),
                )
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HighlightCam::Recording").apply {
                acquire()
            }
    }

    @Suppress("MagicNumber")
    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Active recording notification"
            }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HighlightCam")
            .setContentText("Recording in progress")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
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

        private val _clipResultFlow =
            MutableSharedFlow<Result<Uri>>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val clipResultFlow: SharedFlow<Result<Uri>> = _clipResultFlow.asSharedFlow()
    }
}
