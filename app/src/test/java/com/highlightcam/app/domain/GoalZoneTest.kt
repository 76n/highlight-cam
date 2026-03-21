package com.highlightcam.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalZoneTest {
    @Test
    fun `FULL_FRAME intersects any zone inside it`() {
        val inner = GoalZone(0.2f, 0.2f, 0.3f, 0.3f)
        assertTrue(GoalZone.FULL_FRAME.intersects(inner))
        assertTrue(inner.intersects(GoalZone.FULL_FRAME))
    }

    @Test
    fun `identical zones intersect`() {
        val zone = GoalZone(0.1f, 0.1f, 0.5f, 0.5f)
        assertTrue(zone.intersects(zone))
    }

    @Test
    fun `non-overlapping zones horizontally do not intersect`() {
        val left = GoalZone(0.0f, 0.0f, 0.3f, 1.0f)
        val right = GoalZone(0.5f, 0.0f, 0.3f, 1.0f)
        assertFalse(left.intersects(right))
        assertFalse(right.intersects(left))
    }

    @Test
    fun `non-overlapping zones vertically do not intersect`() {
        val top = GoalZone(0.0f, 0.0f, 1.0f, 0.3f)
        val bottom = GoalZone(0.0f, 0.5f, 1.0f, 0.3f)
        assertFalse(top.intersects(bottom))
        assertFalse(bottom.intersects(top))
    }

    @Test
    fun `edge-touching zones horizontally do not intersect`() {
        val left = GoalZone(0.0f, 0.0f, 0.5f, 1.0f)
        val right = GoalZone(0.5f, 0.0f, 0.5f, 1.0f)
        assertFalse(left.intersects(right))
    }

    @Test
    fun `edge-touching zones vertically do not intersect`() {
        val top = GoalZone(0.0f, 0.0f, 1.0f, 0.5f)
        val bottom = GoalZone(0.0f, 0.5f, 1.0f, 0.5f)
        assertFalse(top.intersects(bottom))
    }

    @Test
    fun `partial overlap zones intersect`() {
        val a = GoalZone(0.0f, 0.0f, 0.6f, 0.6f)
        val b = GoalZone(0.4f, 0.4f, 0.6f, 0.6f)
        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
    }

    @Test
    fun `zone fully contained inside another intersects`() {
        val outer = GoalZone(0.0f, 0.0f, 1.0f, 1.0f)
        val inner = GoalZone(0.3f, 0.3f, 0.1f, 0.1f)
        assertTrue(outer.intersects(inner))
        assertTrue(inner.intersects(outer))
    }

    @Test
    fun `diagonal non-overlapping zones do not intersect`() {
        val topLeft = GoalZone(0.0f, 0.0f, 0.3f, 0.3f)
        val bottomRight = GoalZone(0.5f, 0.5f, 0.3f, 0.3f)
        assertFalse(topLeft.intersects(bottomRight))
        assertFalse(bottomRight.intersects(topLeft))
    }

    @Test
    fun `FULL_FRAME self-intersects`() {
        assertTrue(GoalZone.FULL_FRAME.intersects(GoalZone.FULL_FRAME))
    }
}
