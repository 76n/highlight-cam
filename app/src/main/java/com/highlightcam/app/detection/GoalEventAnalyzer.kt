package com.highlightcam.app.detection

import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalEventAnalyzer
    @Inject
    constructor() {
        fun analyze(
            detections: List<Detection>,
            goalZoneSet: GoalZoneSet,
            sensitivity: Float,
        ): AnalysisResult {
            val results = goalZoneSet.activeZones.map { analyzeForZone(detections, it, sensitivity) }
            val best = results.filter { it.isCandidateEvent }.maxByOrNull { it.confidence }

            return best ?: AnalysisResult(
                isCandidateEvent = false,
                confidence = 0f,
                ballDetected = results.any { it.ballDetected },
                ballInZone = false,
                playerCountInZone = results.sumOf { it.playerCountInZone },
                reason = "No event",
                goalZoneId = null,
            )
        }

        private fun analyzeForZone(
            detections: List<Detection>,
            zone: GoalZone,
            sensitivity: Float,
        ): AnalysisResult {
            val threshold = lerp(HIGH_CONF_THRESHOLD, LOW_CONF_THRESHOLD, sensitivity)
            val playerThreshold = threshold * PLAYER_THRESHOLD_FACTOR

            val ballDetected = detections.any { it.classId == CLASS_SPORTS_BALL && it.confidence >= threshold }

            val ballInZone =
                detections.any {
                    it.classId == CLASS_SPORTS_BALL &&
                        it.confidence >= threshold &&
                        zone.containsPoint(it.boundingBox.centerX, it.boundingBox.centerY)
                }

            val playerCountInZone =
                detections.count {
                    it.classId == CLASS_PERSON &&
                        it.confidence >= playerThreshold &&
                        zone.containsPoint(it.boundingBox.centerX, it.boundingBox.centerY)
                }

            val isCandidateEvent = ballInZone || playerCountInZone >= PLAYER_CLUSTER_THRESHOLD

            val confidence =
                when {
                    ballInZone ->
                        detections
                            .filter {
                                it.classId == CLASS_SPORTS_BALL &&
                                    it.confidence >= threshold &&
                                    zone.containsPoint(it.boundingBox.centerX, it.boundingBox.centerY)
                            }
                            .maxOf { it.confidence }
                    playerCountInZone >= PLAYER_CLUSTER_THRESHOLD ->
                        (playerCountInZone / 6f).coerceAtMost(1f)
                    else -> 0f
                }

            val reason =
                when {
                    ballInZone -> "Ball in ${zone.label} (conf %.2f)".format(confidence)
                    playerCountInZone >= PLAYER_CLUSTER_THRESHOLD ->
                        "Player cluster in ${zone.label} ($playerCountInZone players)"
                    else -> "No event"
                }

            return AnalysisResult(
                isCandidateEvent = isCandidateEvent,
                confidence = confidence,
                ballDetected = ballDetected,
                ballInZone = ballInZone,
                playerCountInZone = playerCountInZone,
                reason = reason,
                goalZoneId = if (isCandidateEvent) zone.id else null,
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
