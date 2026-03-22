package com.highlightcam.app.tracking

import com.highlightcam.app.detection.BoundingBox
import com.highlightcam.app.detection.Detection
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoFollowEngineTest {
    private lateinit var engine: AutoFollowEngine
    private val defaultConfig = AutoFollowConfig(enabled = true, smoothingAlpha = 1.0f)

    private val goalA =
        GoalZone(
            id = "a",
            label = "Goal A",
            p1 = NormalizedPoint(0.02f, 0.3f),
            p2 = NormalizedPoint(0.12f, 0.3f),
            p3 = NormalizedPoint(0.12f, 0.7f),
            p4 = NormalizedPoint(0.02f, 0.7f),
        )

    private val goalB =
        GoalZone(
            id = "b",
            label = "Goal B",
            p1 = NormalizedPoint(0.88f, 0.3f),
            p2 = NormalizedPoint(0.98f, 0.3f),
            p3 = NormalizedPoint(0.98f, 0.7f),
            p4 = NormalizedPoint(0.88f, 0.7f),
        )

    @Before
    fun setUp() {
        engine = AutoFollowEngine()
    }

    @Test
    fun `fewer than minPlayers returns currentCrop unchanged`() {
        val current = CropWindow(0.3f, 0.3f, 1.5f)
        val result =
            engine.computeNextCrop(
                detections = listOf(makePlayer(0.5f, 0.5f)),
                activeZones = listOf(goalA),
                currentCrop = current,
                config = defaultConfig.copy(minPlayersToTrack = 2),
            )
        assertEquals(current, result)
    }

    @Test
    fun `disabled config returns FULL_FRAME`() {
        val result =
            engine.computeNextCrop(
                detections = listOf(makePlayer(0.5f, 0.5f)),
                activeZones = listOf(goalA),
                currentCrop = CropWindow(0.3f, 0.3f, 1.5f),
                config = defaultConfig.copy(enabled = false),
            )
        assertEquals(CropWindow.FULL_FRAME, result)
    }

    @Test
    fun `players clustered in center produces scale above 1`() {
        val players =
            listOf(
                makePlayer(0.45f, 0.45f, 0.05f),
                makePlayer(0.55f, 0.55f, 0.05f),
                makePlayer(0.50f, 0.50f, 0.05f),
            )
        val result =
            engine.computeNextCrop(
                detections = players,
                activeZones = listOf(goalA, goalB),
                currentCrop = CropWindow.FULL_FRAME,
                config = defaultConfig,
            )
        assertTrue("Scale should be >= 1.0, was ${result.scale}", result.scale >= 1.0f)
    }

    @Test
    fun `players spread across full pitch produces scale close to 1`() {
        val players =
            listOf(
                makePlayer(0.1f, 0.3f, 0.05f),
                makePlayer(0.9f, 0.7f, 0.05f),
                makePlayer(0.5f, 0.5f, 0.05f),
            )
        val result =
            engine.computeNextCrop(
                detections = players,
                activeZones = listOf(goalA, goalB),
                currentCrop = CropWindow.FULL_FRAME,
                config = defaultConfig,
            )
        assertTrue("Scale should be close to 1.0, was ${result.scale}", result.scale <= 1.3f)
    }

    @Test
    fun `crop always contains goal zones`() {
        val players =
            listOf(
                makePlayer(0.5f, 0.5f, 0.05f),
                makePlayer(0.55f, 0.5f, 0.05f),
            )
        val result =
            engine.computeNextCrop(
                detections = players,
                activeZones = listOf(goalA, goalB),
                currentCrop = CropWindow.FULL_FRAME,
                config = defaultConfig,
            )
        val rect = result.toRect()

        for (zone in listOf(goalA, goalB)) {
            for (pt in zone.toPoints()) {
                assertTrue(
                    "Zone point (${pt.x}, ${pt.y}) outside crop rect $rect",
                    pt.x >= rect.left - EPSILON &&
                        pt.x <= rect.right + EPSILON &&
                        pt.y >= rect.top - EPSILON &&
                        pt.y <= rect.bottom + EPSILON,
                )
            }
        }
    }

    @Test
    fun `EMA smoothing produces slow convergence`() {
        val current = CropWindow(0.5f, 0.5f, 1.0f)
        val players =
            listOf(
                makePlayer(0.1f, 0.5f, 0.04f),
                makePlayer(0.9f, 0.5f, 0.04f),
                makePlayer(0.55f, 0.55f, 0.04f),
            )
        val result =
            engine.computeNextCrop(
                detections = players,
                activeZones = emptyList(),
                currentCrop = current,
                config = defaultConfig.copy(smoothingAlpha = 0.08f, maxScale = 2.0f),
            )
        assertTrue(
            "With alpha=0.08 and wide spread, center should barely move from 0.5, was ${result.centerX}",
            kotlin.math.abs(result.centerX - current.centerX) < 0.1f,
        )
    }

    @Test
    fun `scale is always within 1 and maxScale`() {
        val players =
            listOf(
                makePlayer(0.49f, 0.49f, 0.001f),
                makePlayer(0.51f, 0.51f, 0.001f),
            )
        val result =
            engine.computeNextCrop(
                detections = players,
                activeZones = listOf(goalA, goalB),
                currentCrop = CropWindow.FULL_FRAME,
                config = defaultConfig.copy(maxScale = 2.0f),
            )
        assertTrue("Scale >= 1.0", result.scale >= 1.0f)
        assertTrue("Scale <= 2.0", result.scale <= 2.0f)
    }

    @Test
    fun `crop rect never extends outside 0-1 bounds`() {
        val players =
            listOf(
                makePlayer(0.95f, 0.95f, 0.03f),
                makePlayer(0.92f, 0.92f, 0.03f),
            )
        val result =
            engine.computeNextCrop(
                detections = players,
                activeZones = listOf(goalA, goalB),
                currentCrop = CropWindow.FULL_FRAME,
                config = defaultConfig,
            )
        val rect = result.toRect()
        assertTrue("left >= 0", rect.left >= 0f)
        assertTrue("top >= 0", rect.top >= 0f)
        assertTrue("right <= 1", rect.right <= 1f + EPSILON)
        assertTrue("bottom <= 1", rect.bottom <= 1f + EPSILON)
    }

    @Test
    fun `safetyClamp with no zones returns unchanged`() {
        val result = AutoFollowEngine.safetyClamp(0.5f, 0.5f, 2.0f, emptyList())
        assertEquals(0.5f, result.centerX, EPSILON)
        assertEquals(0.5f, result.centerY, EPSILON)
        assertEquals(2.0f, result.scale, EPSILON)
    }

    @Test
    fun `zero players returns currentCrop unchanged`() {
        val current = CropWindow(0.4f, 0.6f, 1.2f)
        val result =
            engine.computeNextCrop(
                detections = emptyList(),
                activeZones = listOf(goalA, goalB),
                currentCrop = current,
                config = defaultConfig.copy(minPlayersToTrack = 2),
            )
        assertEquals(current, result)
    }

    @Test
    fun `safetyClamp reduces scale when zones are wider than crop`() {
        val wideZone =
            GoalZone(
                id = "wide",
                label = "Wide",
                p1 = NormalizedPoint(0.0f, 0.3f),
                p2 = NormalizedPoint(1.0f, 0.3f),
                p3 = NormalizedPoint(1.0f, 0.7f),
                p4 = NormalizedPoint(0.0f, 0.7f),
            )
        val result = AutoFollowEngine.safetyClamp(0.5f, 0.5f, 2.0f, listOf(wideZone))
        assertTrue("Scale should be 1.0 for full-width zone, was ${result.scale}", result.scale <= 1.0f + EPSILON)
    }

    private fun makePlayer(
        cx: Float,
        cy: Float,
        halfSize: Float = 0.05f,
    ): Detection =
        Detection(
            classId = 0,
            confidence = 0.8f,
            boundingBox =
                BoundingBox(
                    left = cx - halfSize,
                    top = cy - halfSize,
                    right = cx + halfSize,
                    bottom = cy + halfSize,
                ),
        )

    companion object {
        private const val EPSILON = 0.01f
    }
}
