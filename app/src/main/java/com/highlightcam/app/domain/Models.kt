package com.highlightcam.app.domain

import android.net.Uri

data class GoalZone(
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
) {
    fun intersects(other: GoalZone): Boolean {
        val left1 = xFraction
        val right1 = xFraction + widthFraction
        val top1 = yFraction
        val bottom1 = yFraction + heightFraction

        val left2 = other.xFraction
        val right2 = other.xFraction + other.widthFraction
        val top2 = other.yFraction
        val bottom2 = other.yFraction + other.heightFraction

        return left1 < right2 && right1 > left2 && top1 < bottom2 && bottom1 > top2
    }

    companion object {
        val FULL_FRAME = GoalZone(0f, 0f, 1f, 1f)
    }
}

enum class VideoQuality {
    HD_720,
    FHD_1080,
}

data class RecordingConfig(
    val segmentDurationSeconds: Int = 3,
    val bufferSegments: Int = 10,
    val secondsAfterEvent: Int = 10,
    val videoQuality: VideoQuality = VideoQuality.HD_720,
) {
    val totalBufferSeconds: Int get() = segmentDurationSeconds * bufferSegments
}

data class SavedClip(
    val uri: Uri,
    val durationMs: Long,
    val savedAt: Long,
    val triggerReason: String,
)

sealed class RecorderState {
    data object Idle : RecorderState()

    data class Recording(val startedAt: Long) : RecorderState()

    data class SavingClip(val progress: Float) : RecorderState()

    data class Error(val message: String) : RecorderState()
}

sealed class DetectionEvent {
    data class CandidateDetected(val confidence: Float) : DetectionEvent()

    data class ClipSaveTriggered(val confidence: Float, val reason: String) : DetectionEvent()

    data class DetectionError(val message: String) : DetectionEvent()
}

enum class AppScreen {
    SETUP,
    RECORDING,
    LIBRARY,
    SETTINGS,
}
