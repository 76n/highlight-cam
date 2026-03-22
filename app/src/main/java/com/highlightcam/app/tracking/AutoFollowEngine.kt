package com.highlightcam.app.tracking

import com.highlightcam.app.detection.Detection
import com.highlightcam.app.domain.GoalZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoFollowEngine
    @Inject
    constructor() {
        fun computeNextCrop(
            detections: List<Detection>,
            activeZones: List<GoalZone>,
            currentCrop: CropWindow,
            config: AutoFollowConfig,
        ): CropWindow {
            if (!config.enabled) return CropWindow.FULL_FRAME

            val players =
                detections.filter { it.classId == CLASS_PERSON && it.confidence > PLAYER_CONFIDENCE }
            if (players.size < config.minPlayersToTrack) return currentCrop

            val centroidX = players.map { it.boundingBox.centerX }.average().toFloat()
            val centroidY = players.map { it.boundingBox.centerY }.average().toFloat()

            val spreadLeft = players.minOf { it.boundingBox.left }
            val spreadTop = players.minOf { it.boundingBox.top }
            val spreadRight = players.maxOf { it.boundingBox.right }
            val spreadBottom = players.maxOf { it.boundingBox.bottom }
            val spreadArea =
                ((spreadRight - spreadLeft) * (spreadBottom - spreadTop)).coerceAtLeast(0.001f)

            val rawScale =
                (1f / (spreadArea * config.spreadPadding)).coerceIn(1f, config.maxScale)

            val (targetCX, targetCY, targetScale) =
                safetyClamp(centroidX, centroidY, rawScale, activeZones)

            val alpha = config.smoothingAlpha
            val smoothCX = currentCrop.centerX + alpha * (targetCX - currentCrop.centerX)
            val smoothCY = currentCrop.centerY + alpha * (targetCY - currentCrop.centerY)
            val smoothScale = currentCrop.scale + alpha * (targetScale - currentCrop.scale)

            return clampToFrame(smoothCX, smoothCY, smoothScale)
        }

        companion object {
            private const val CLASS_PERSON = 0
            private const val PLAYER_CONFIDENCE = 0.45f

            internal fun safetyClamp(
                cx: Float,
                cy: Float,
                scale: Float,
                activeZones: List<GoalZone>,
            ): CropWindow {
                if (activeZones.isEmpty()) return CropWindow(cx, cy, scale)

                var allMinX = Float.MAX_VALUE
                var allMinY = Float.MAX_VALUE
                var allMaxX = Float.MIN_VALUE
                var allMaxY = Float.MIN_VALUE
                for (zone in activeZones) {
                    val pts = zone.toPoints()
                    allMinX = minOf(allMinX, pts.minOf { it.x })
                    allMinY = minOf(allMinY, pts.minOf { it.y })
                    allMaxX = maxOf(allMaxX, pts.maxOf { it.x })
                    allMaxY = maxOf(allMaxY, pts.maxOf { it.y })
                }

                val requiredW = allMaxX - allMinX
                val requiredH = allMaxY - allMinY
                var s = scale
                if (1f / s < requiredW) s = (1f / requiredW).coerceAtLeast(1f)
                if (1f / s < requiredH) s = (1f / requiredH).coerceAtLeast(1f)

                val halfW = 1f / (2f * s)
                val halfH = 1f / (2f * s)

                val adjCX = cx.coerceIn(allMaxX - halfW, allMinX + halfW)
                val adjCY = cy.coerceIn(allMaxY - halfH, allMinY + halfH)

                return CropWindow(
                    adjCX.coerceIn(halfW, 1f - halfW),
                    adjCY.coerceIn(halfH, 1f - halfH),
                    s,
                )
            }

            fun clampToFrame(
                cx: Float,
                cy: Float,
                scale: Float,
            ): CropWindow {
                val halfW = 1f / (2f * scale)
                val halfH = 1f / (2f * scale)
                return CropWindow(
                    cx.coerceIn(halfW, 1f - halfW),
                    cy.coerceIn(halfH, 1f - halfH),
                    scale,
                )
            }
        }
    }
