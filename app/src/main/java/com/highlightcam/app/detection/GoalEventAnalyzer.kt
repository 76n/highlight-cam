package com.highlightcam.app.detection

import com.highlightcam.app.domain.GoalZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalEventAnalyzer
    @Inject
    constructor() {
        fun analyze(
            detections: List<Detection>,
            goalZone: GoalZone,
            sensitivity: Float,
        ): AnalysisResult {
            val threshold = lerp(HIGH_CONF_THRESHOLD, LOW_CONF_THRESHOLD, sensitivity)
            val playerThreshold = threshold * PLAYER_THRESHOLD_FACTOR

            val zoneBB = goalZone.toBoundingBox()

            val ballDetected = detections.any { it.classId == CLASS_SPORTS_BALL && it.confidence >= threshold }

            val ballInZone =
                detections.any {
                    it.classId == CLASS_SPORTS_BALL &&
                        it.confidence >= threshold &&
                        it.boundingBox.intersects(zoneBB)
                }

            val playerCountInZone =
                detections.count {
                    it.classId == CLASS_PERSON &&
                        it.confidence >= playerThreshold &&
                        it.boundingBox.intersects(zoneBB)
                }

            val isCandidateEvent = ballInZone || playerCountInZone >= PLAYER_CLUSTER_THRESHOLD

            val confidence =
                when {
                    ballInZone -> {
                        detections
                            .filter {
                                it.classId == CLASS_SPORTS_BALL &&
                                    it.confidence >= threshold &&
                                    it.boundingBox.intersects(zoneBB)
                            }
                            .maxOf { it.confidence }
                    }
                    playerCountInZone >= PLAYER_CLUSTER_THRESHOLD -> {
                        (playerCountInZone / 6f).coerceAtMost(1f)
                    }
                    else -> 0f
                }

            val reason =
                when {
                    ballInZone -> "Ball in zone (conf %.2f)".format(confidence)
                    playerCountInZone >= PLAYER_CLUSTER_THRESHOLD ->
                        "Player cluster ($playerCountInZone players)"
                    else -> "No event"
                }

            return AnalysisResult(
                isCandidateEvent = isCandidateEvent,
                confidence = confidence,
                ballDetected = ballDetected,
                ballInZone = ballInZone,
                playerCountInZone = playerCountInZone,
                reason = reason,
            )
        }

        companion object {
            const val CLASS_PERSON = 0
            const val CLASS_SPORTS_BALL = 32
            const val HIGH_CONF_THRESHOLD = 0.65f
            const val LOW_CONF_THRESHOLD = 0.35f
            const val PLAYER_THRESHOLD_FACTOR = 0.8f
            const val PLAYER_CLUSTER_THRESHOLD = 4

            fun lerp(
                a: Float,
                b: Float,
                t: Float,
            ): Float = a + (b - a) * t.coerceIn(0f, 1f)
        }
    }

private fun GoalZone.toBoundingBox() =
    BoundingBox(
        left = xFraction,
        top = yFraction,
        right = xFraction + widthFraction,
        bottom = yFraction + heightFraction,
    )
