package com.highlightcam.app.detection

import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalEventAnalyzerTest {
    private lateinit var analyzer: GoalEventAnalyzer
    private val defaultSensitivity = 0.5f

    private val goalA =
        GoalZone(
            id = "a",
            label = "Goal A",
            p1 = NormalizedPoint(0.0f, 0.0f),
            p2 = NormalizedPoint(0.4f, 0.0f),
            p3 = NormalizedPoint(0.4f, 0.6f),
            p4 = NormalizedPoint(0.0f, 0.6f),
        )

    private val goalB =
        GoalZone(
            id = "b",
            label = "Goal B",
            p1 = NormalizedPoint(0.6f, 0.0f),
            p2 = NormalizedPoint(1.0f, 0.0f),
            p3 = NormalizedPoint(1.0f, 0.6f),
            p4 = NormalizedPoint(0.6f, 0.6f),
        )

    private val zoneSet = GoalZoneSet(goalA, goalB)

    @Before
    fun setup() {
        analyzer = GoalEventAnalyzer()
    }

    @Test
    fun `ball in Goal A returns goalZoneId a`() {
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.85f, BoundingBox(0.15f, 0.25f, 0.25f, 0.35f)),
            )
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertTrue(result.ballInZone)
        assertEquals("a", result.goalZoneId)
    }

    @Test
    fun `ball in Goal B returns goalZoneId b`() {
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.85f, BoundingBox(0.75f, 0.25f, 0.85f, 0.35f)),
            )
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertTrue(result.ballInZone)
        assertEquals("b", result.goalZoneId)
    }

    @Test
    fun `ball outside both zones returns null goalZoneId`() {
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.90f, BoundingBox(0.45f, 0.7f, 0.55f, 0.8f)),
            )
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertFalse(result.ballInZone)
        assertNull(result.goalZoneId)
    }

    @Test
    fun `ball in zone low confidence below threshold does not trigger`() {
        val threshold = GoalEventAnalyzer.lerp(0.65f, 0.35f, defaultSensitivity)
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, threshold - 0.05f, BoundingBox(0.15f, 0.25f, 0.25f, 0.35f)),
            )
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
    }

    @Test
    fun `four players in Goal A triggers with goalZoneId a`() {
        val detections =
            (1..4).map {
                Detection(GoalEventAnalyzer.CLASS_PERSON, 0.80f, BoundingBox(0.1f, 0.1f, 0.2f, 0.2f))
            }
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertEquals("a", result.goalZoneId)
        assertTrue(result.reason.contains("Player cluster"))
    }

    @Test
    fun `three players in zone does not trigger`() {
        val detections =
            (1..3).map {
                Detection(GoalEventAnalyzer.CLASS_PERSON, 0.80f, BoundingBox(0.1f, 0.1f, 0.2f, 0.2f))
            }
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
    }

    @Test
    fun `no detections returns no event`() {
        val result = analyzer.analyze(emptyList(), zoneSet, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertNull(result.goalZoneId)
    }

    @Test
    fun `ball in both zones returns higher confidence`() {
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.90f, BoundingBox(0.15f, 0.25f, 0.25f, 0.35f)),
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.70f, BoundingBox(0.75f, 0.25f, 0.85f, 0.35f)),
            )
        val result = analyzer.analyze(detections, zoneSet, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertEquals("a", result.goalZoneId)
    }

    @Test
    fun `sensitivity 0_0 rejects confidence 0_60`() {
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.60f, BoundingBox(0.15f, 0.25f, 0.25f, 0.35f)),
            )
        val result = analyzer.analyze(detections, zoneSet, 0.0f)
        assertFalse(result.ballInZone)
    }

    @Test
    fun `sensitivity 1_0 accepts confidence 0_60`() {
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.60f, BoundingBox(0.15f, 0.25f, 0.25f, 0.35f)),
            )
        val result = analyzer.analyze(detections, zoneSet, 1.0f)
        assertTrue(result.ballInZone)
        assertEquals("a", result.goalZoneId)
    }

    @Test
    fun `lerp produces expected values`() {
        assertEquals(0.65f, GoalEventAnalyzer.lerp(0.65f, 0.35f, 0.0f), 0.001f)
        assertEquals(0.50f, GoalEventAnalyzer.lerp(0.65f, 0.35f, 0.5f), 0.001f)
        assertEquals(0.35f, GoalEventAnalyzer.lerp(0.65f, 0.35f, 1.0f), 0.001f)
    }

    @Test
    fun `single zone set detects ball in Goal A`() {
        val singleZoneSet = GoalZoneSet(goalA)
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.85f, BoundingBox(0.15f, 0.25f, 0.25f, 0.35f)),
            )
        val result = analyzer.analyze(detections, singleZoneSet, defaultSensitivity)
        assertTrue(result.isCandidateEvent)
        assertTrue(result.ballInZone)
        assertEquals("a", result.goalZoneId)
    }

    @Test
    fun `single zone set does not crash on absent Goal B`() {
        val singleZoneSet = GoalZoneSet(goalA)
        val detections =
            listOf(
                Detection(GoalEventAnalyzer.CLASS_SPORTS_BALL, 0.90f, BoundingBox(0.75f, 0.25f, 0.85f, 0.35f)),
            )
        val result = analyzer.analyze(detections, singleZoneSet, defaultSensitivity)
        assertFalse(result.isCandidateEvent)
        assertNull(result.goalZoneId)
    }

    @Test
    fun `activeZones returns one when goalB is null`() {
        val singleZoneSet = GoalZoneSet(goalA)
        assertEquals(1, singleZoneSet.activeZones.size)
        assertEquals("a", singleZoneSet.activeZones[0].id)
    }

    @Test
    fun `activeZones returns two when goalB is defined`() {
        assertEquals(2, zoneSet.activeZones.size)
    }
}
