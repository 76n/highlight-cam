package com.highlightcam.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalZoneTest {
    private val square =
        GoalZone(
            id = "test",
            label = "Test",
            p1 = NormalizedPoint(0.2f, 0.2f),
            p2 = NormalizedPoint(0.8f, 0.2f),
            p3 = NormalizedPoint(0.8f, 0.8f),
            p4 = NormalizedPoint(0.2f, 0.8f),
        )

    private val trapezoid =
        GoalZone(
            id = "trap",
            label = "Trap",
            p1 = NormalizedPoint(0.3f, 0.2f),
            p2 = NormalizedPoint(0.7f, 0.2f),
            p3 = NormalizedPoint(0.9f, 0.8f),
            p4 = NormalizedPoint(0.1f, 0.8f),
        )

    private val skewedQuad =
        GoalZone(
            id = "skew",
            label = "Skew",
            p1 = NormalizedPoint(0.1f, 0.1f),
            p2 = NormalizedPoint(0.6f, 0.2f),
            p3 = NormalizedPoint(0.7f, 0.9f),
            p4 = NormalizedPoint(0.05f, 0.7f),
        )

    @Test
    fun `point inside square returns true`() {
        assertTrue(square.containsPoint(0.5f, 0.5f))
    }

    @Test
    fun `point outside square returns false`() {
        assertFalse(square.containsPoint(0.1f, 0.1f))
    }

    @Test
    fun `point on top edge returns true`() {
        assertTrue(square.containsPoint(0.5f, 0.2f))
    }

    @Test
    fun `point on right edge returns true`() {
        assertTrue(square.containsPoint(0.8f, 0.5f))
    }

    @Test
    fun `point on bottom edge returns true`() {
        assertTrue(square.containsPoint(0.5f, 0.8f))
    }

    @Test
    fun `point on left edge returns true`() {
        assertTrue(square.containsPoint(0.2f, 0.5f))
    }

    @Test
    fun `point at corner returns true`() {
        assertTrue(square.containsPoint(0.2f, 0.2f))
        assertTrue(square.containsPoint(0.8f, 0.8f))
    }

    @Test
    fun `point inside trapezoid returns true`() {
        assertTrue(trapezoid.containsPoint(0.5f, 0.5f))
    }

    @Test
    fun `point outside trapezoid narrow top returns false`() {
        assertFalse(trapezoid.containsPoint(0.25f, 0.25f))
    }

    @Test
    fun `point inside trapezoid wide bottom returns true`() {
        assertTrue(trapezoid.containsPoint(0.15f, 0.75f))
    }

    @Test
    fun `point outside trapezoid left returns false`() {
        assertFalse(trapezoid.containsPoint(0.05f, 0.5f))
    }

    @Test
    fun `point inside skewed quad returns true`() {
        assertTrue(skewedQuad.containsPoint(0.3f, 0.5f))
    }

    @Test
    fun `point outside skewed quad returns false`() {
        assertFalse(skewedQuad.containsPoint(0.9f, 0.1f))
    }

    @Test
    fun `GOAL_A_DEFAULT containsPoint for its center`() {
        val z = GoalZone.GOAL_A_DEFAULT
        assertTrue(z.containsPoint(0.15f, 0.5f))
    }

    @Test
    fun `GOAL_B_DEFAULT does not contain GOAL_A center`() {
        assertFalse(GoalZone.GOAL_B_DEFAULT.containsPoint(0.15f, 0.5f))
    }

    @Test
    fun `GOAL_B_DEFAULT containsPoint for its center`() {
        assertTrue(GoalZone.GOAL_B_DEFAULT.containsPoint(0.85f, 0.5f))
    }

    @Test
    fun `toPoints returns 4 points in order`() {
        val pts = square.toPoints()
        assert(pts.size == 4)
        assert(pts[0] == NormalizedPoint(0.2f, 0.2f))
        assert(pts[3] == NormalizedPoint(0.2f, 0.8f))
    }

    @Test
    fun `activeZones with both goals returns two zones`() {
        val set = GoalZoneSet(GoalZone.GOAL_A_DEFAULT, GoalZone.GOAL_B_DEFAULT)
        assertEquals(2, set.activeZones.size)
        assertEquals("a", set.activeZones[0].id)
        assertEquals("b", set.activeZones[1].id)
    }

    @Test
    fun `activeZones with null goalB returns only goalA`() {
        val set = GoalZoneSet(GoalZone.GOAL_A_DEFAULT, goalB = null)
        assertEquals(1, set.activeZones.size)
        assertEquals("a", set.activeZones[0].id)
    }

    @Test
    fun `hasGoalB is false when goalB is null`() {
        val set = GoalZoneSet(GoalZone.GOAL_A_DEFAULT, goalB = null)
        assertFalse(set.hasGoalB)
        assertNull(set.goalB)
    }

    @Test
    fun `hasGoalB is true when goalB is present`() {
        val set = GoalZoneSet(GoalZone.GOAL_A_DEFAULT, GoalZone.GOAL_B_DEFAULT)
        assertTrue(set.hasGoalB)
    }

    @Test
    fun `containsPoint for trapezoid vertex`() {
        assertTrue(trapezoid.containsPoint(0.3f, 0.2f))
    }

    @Test
    fun `containsPoint for skewed quad near right edge returns false`() {
        assertFalse(skewedQuad.containsPoint(0.8f, 0.5f))
    }
}
