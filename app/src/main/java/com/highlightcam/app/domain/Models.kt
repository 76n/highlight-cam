package com.highlightcam.app.domain

import android.net.Uri

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

data class GoalZone(
    val id: String,
    val label: String,
    val p1: NormalizedPoint,
    val p2: NormalizedPoint,
    val p3: NormalizedPoint,
    val p4: NormalizedPoint,
) {
    fun containsPoint(
        px: Float,
        py: Float,
    ): Boolean {
        val points = toPoints()
        var positiveCount = 0
        var negativeCount = 0
        for (i in points.indices) {
            val curr = points[i]
            val next = points[(i + 1) % VERTEX_COUNT]
            val cross = (next.x - curr.x) * (py - curr.y) - (next.y - curr.y) * (px - curr.x)
            if (cross > 0f) {
                positiveCount++
            } else if (cross < 0f) {
                negativeCount++
            }
        }
        return positiveCount == 0 || negativeCount == 0
    }

    fun toPoints(): List<NormalizedPoint> = listOf(p1, p2, p3, p4)

    companion object {
        const val VERTEX_COUNT = 4

        val GOAL_A_DEFAULT =
            GoalZone(
                id = "a",
                label = "Goal A",
                p1 = NormalizedPoint(0.05f, 0.35f),
                p2 = NormalizedPoint(0.25f, 0.35f),
                p3 = NormalizedPoint(0.25f, 0.65f),
                p4 = NormalizedPoint(0.05f, 0.65f),
            )

        val GOAL_B_DEFAULT =
            GoalZone(
                id = "b",
                label = "Goal B",
                p1 = NormalizedPoint(0.75f, 0.35f),
                p2 = NormalizedPoint(0.95f, 0.35f),
                p3 = NormalizedPoint(0.95f, 0.65f),
                p4 = NormalizedPoint(0.75f, 0.65f),
            )
    }
}

data class GoalZoneSet(
    val goalA: GoalZone,
    val goalB: GoalZone,
) {
    companion object {
        val DEFAULT = GoalZoneSet(GoalZone.GOAL_A_DEFAULT, GoalZone.GOAL_B_DEFAULT)
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
    data class CandidateDetected(
        val confidence: Float,
        val goalZoneId: String? = null,
    ) : DetectionEvent()

    data class ClipSaveTriggered(
        val confidence: Float,
        val reason: String,
        val goalZoneId: String? = null,
    ) : DetectionEvent()

    data class DetectionError(val message: String) : DetectionEvent()
}

enum class AppScreen {
    SETUP,
    RECORDING,
    LIBRARY,
    SETTINGS,
}
