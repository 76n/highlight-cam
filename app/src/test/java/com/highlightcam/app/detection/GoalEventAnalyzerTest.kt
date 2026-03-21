package com.highlightcam.app.detection

import com.highlightcam.app.domain.GoalZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalEventAnalyzerTest {
    private lateinit var analyzer: GoalEventAnalyzer
    private val zone = GoalZone(0.3f, 0.3f, 0.4f, 0.4f)
    private val defaultSensitivity = 0.5f

    @Before
    fun setup() {
        analyzer = GoalEventAnalyzer()
    }

    @Test
    fun `ball in zone high confidence triggers candidate`() {
        val detections =
            listOf(
                Detection(
                    classId = GoalEventAnalyzer.CLASS_SPORTS_BALL,
                    confidence = 0.85f,
                    boundingBox = BoundingBox(0.4f, 0.4f, 0.5f, 0.5f),
                ),
            )
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertTrue(result.ballInZone)
        assertTrue(result.ballDetected)
        assertTrue(result.confidence > 0.8f)
    }

    @Test
    fun `ball in zone low confidence below threshold does not trigger`() {
        val threshold = GoalEventAnalyzer.lerp(0.65f, 0.35f, defaultSensitivity)
        val detections =
            listOf(
                Detection(
                    classId = GoalEventAnalyzer.CLASS_SPORTS_BALL,
                    confidence = threshold - 0.05f,
                    boundingBox = BoundingBox(0.4f, 0.4f, 0.5f, 0.5f),
                ),
            )
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertFalse(result.ballInZone)
    }

    @Test
    fun `ball outside zone does not trigger`() {
        val detections =
            listOf(
                Detection(
                    classId = GoalEventAnalyzer.CLASS_SPORTS_BALL,
                    confidence = 0.90f,
                    boundingBox = BoundingBox(0.0f, 0.0f, 0.1f, 0.1f),
                ),
            )
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertFalse(result.ballInZone)
        assertTrue(result.ballDetected)
    }

    @Test
    fun `three players in zone below cluster threshold`() {
        val detections =
            (1..3).map {
                Detection(
                    classId = GoalEventAnalyzer.CLASS_PERSON,
                    confidence = 0.80f,
                    boundingBox = BoundingBox(0.35f, 0.35f, 0.45f, 0.45f),
                )
            }
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertEquals(3, result.playerCountInZone)
    }

    @Test
    fun `four players in zone at cluster threshold triggers candidate`() {
        val detections =
            (1..4).map {
                Detection(
                    classId = GoalEventAnalyzer.CLASS_PERSON,
                    confidence = 0.80f,
                    boundingBox = BoundingBox(0.35f, 0.35f, 0.45f, 0.45f),
                )
            }
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertEquals(4, result.playerCountInZone)
        assertTrue(result.reason.contains("Player cluster"))
    }

    @Test
    fun `five players in zone triggers with capped confidence`() {
        val detections =
            (1..5).map {
                Detection(
                    classId = GoalEventAnalyzer.CLASS_PERSON,
                    confidence = 0.80f,
                    boundingBox = BoundingBox(0.35f, 0.35f, 0.45f, 0.45f),
                )
            }
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertEquals(5, result.playerCountInZone)
        assertEquals(5f / 6f, result.confidence, 0.01f)
    }

    @Test
    fun `no detections returns no event`() {
        val result = analyzer.analyze(emptyList(), zone, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertFalse(result.ballDetected)
        assertFalse(result.ballInZone)
        assertEquals(0, result.playerCountInZone)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `mixed ball and players in zone — ball takes priority`() {
        val detections =
            listOf(
                Detection(
                    classId = GoalEventAnalyzer.CLASS_SPORTS_BALL,
                    confidence = 0.75f,
                    boundingBox = BoundingBox(0.4f, 0.4f, 0.5f, 0.5f),
                ),
            ) +
                (1..4).map {
                    Detection(
                        classId = GoalEventAnalyzer.CLASS_PERSON,
                        confidence = 0.80f,
                        boundingBox = BoundingBox(0.35f, 0.35f, 0.45f, 0.45f),
                    )
                }
        val result = analyzer.analyze(detections, zone, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertTrue(result.ballInZone)
        assertTrue(result.reason.contains("Ball in zone"))
    }

    @Test
    fun `sensitivity 0_0 rejects detection at confidence 0_60`() {
        val detections =
            listOf(
                Detection(
                    classId = GoalEventAnalyzer.CLASS_SPORTS_BALL,
                    confidence = 0.60f,
                    boundingBox = BoundingBox(0.4f, 0.4f, 0.5f, 0.5f),
                ),
            )
        val result = analyzer.analyze(detections, zone, 0.0f)
        assertFalse(result.ballInZone)
        assertFalse(result.isCandidateEvent)
    }

    @Test
    fun `sensitivity 1_0 accepts detection at confidence 0_60`() {
        val detections =
            listOf(
                Detection(
                    classId = GoalEventAnalyzer.CLASS_SPORTS_BALL,
                    confidence = 0.60f,
                    boundingBox = BoundingBox(0.4f, 0.4f, 0.5f, 0.5f),
                ),
            )
        val result = analyzer.analyze(detections, zone, 1.0f)
        assertTrue(result.ballInZone)
        assertTrue(result.isCandidateEvent)
    }

    @Test
    fun `lerp produces expected threshold values`() {
        assertEquals(0.65f, GoalEventAnalyzer.lerp(0.65f, 0.35f, 0.0f), 0.001f)
        assertEquals(0.50f, GoalEventAnalyzer.lerp(0.65f, 0.35f, 0.5f), 0.001f)
        assertEquals(0.35f, GoalEventAnalyzer.lerp(0.65f, 0.35f, 1.0f), 0.001f)
    }
}
