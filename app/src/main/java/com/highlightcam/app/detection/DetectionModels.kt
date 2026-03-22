package com.highlightcam.app.detection

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class Detection(
    val classId: Int,
    val confidence: Float,
    val boundingBox: BoundingBox,
)

data class AnalysisResult(
    val isCandidateEvent: Boolean,
    val confidence: Float,
    val ballDetected: Boolean,
    val ballInZone: Boolean,
    val playerCountInZone: Int,
    val reason: String,
    val goalZoneId: String? = null,
)

data class AudioEvent(
    val energySpike: Boolean,
    val whistleDetected: Boolean,
    val currentRms: Float,
    val baselineRms: Float,
)

data class DebugInfo(
    val currentRms: Float = 0f,
    val baselineRms: Float = 0f,
    val ballDetected: Boolean = false,
    val ballInZone: Boolean = false,
    val playerCountInZone: Int = 0,
    val stateMachineState: String = "Idle",
    val lastEventTime: Long = 0L,
    val lastEventReason: String = "",
    val modelAvailable: Boolean = false,
    val recentInferenceTimesMs: List<Long> = emptyList(),
)
