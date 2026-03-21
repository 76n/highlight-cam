package com.highlightcam.app.ui.setup

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalZoneMapperTest {
    @Test
    fun `full frame rect on 1080x1920 produces full GoalZone`() {
        val zone = rectToGoalZone(Rect(0f, 0f, 1080f, 1920f), 1080f, 1920f)
        assertEquals(0f, zone.xFraction, EPSILON)
        assertEquals(0f, zone.yFraction, EPSILON)
        assertEquals(1f, zone.widthFraction, EPSILON)
        assertEquals(1f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `full frame rect on 720x1280 produces full GoalZone`() {
        val zone = rectToGoalZone(Rect(0f, 0f, 720f, 1280f), 720f, 1280f)
        assertEquals(0f, zone.xFraction, EPSILON)
        assertEquals(0f, zone.yFraction, EPSILON)
        assertEquals(1f, zone.widthFraction, EPSILON)
        assertEquals(1f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `center rect on 1080x1920 produces correct fractions`() {
        val zone = rectToGoalZone(Rect(270f, 480f, 810f, 1440f), 1080f, 1920f)
        assertEquals(0.25f, zone.xFraction, EPSILON)
        assertEquals(0.25f, zone.yFraction, EPSILON)
        assertEquals(0.5f, zone.widthFraction, EPSILON)
        assertEquals(0.5f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `center rect on 720x1280 produces correct fractions`() {
        val zone = rectToGoalZone(Rect(180f, 320f, 540f, 960f), 720f, 1280f)
        assertEquals(0.25f, zone.xFraction, EPSILON)
        assertEquals(0.25f, zone.yFraction, EPSILON)
        assertEquals(0.5f, zone.widthFraction, EPSILON)
        assertEquals(0.5f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `small corner rect produces correct fractions`() {
        val zone = rectToGoalZone(Rect(0f, 0f, 108f, 192f), 1080f, 1920f)
        assertEquals(0f, zone.xFraction, EPSILON)
        assertEquals(0f, zone.yFraction, EPSILON)
        assertEquals(0.1f, zone.widthFraction, EPSILON)
        assertEquals(0.1f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `bottom right rect produces correct fractions`() {
        val zone = rectToGoalZone(Rect(540f, 960f, 1080f, 1920f), 1080f, 1920f)
        assertEquals(0.5f, zone.xFraction, EPSILON)
        assertEquals(0.5f, zone.yFraction, EPSILON)
        assertEquals(0.5f, zone.widthFraction, EPSILON)
        assertEquals(0.5f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `values are clamped to 0-1 range`() {
        val zone = rectToGoalZone(Rect(-100f, -50f, 1200f, 2000f), 1080f, 1920f)
        assertEquals(0f, zone.xFraction, EPSILON)
        assertEquals(0f, zone.yFraction, EPSILON)
        assertEquals(1f, zone.widthFraction, EPSILON)
        assertEquals(1f, zone.heightFraction, EPSILON)
    }

    @Test
    fun `same fractions from different resolutions`() {
        val zoneFhd = rectToGoalZone(Rect(270f, 480f, 810f, 1440f), 1080f, 1920f)
        val zoneHd = rectToGoalZone(Rect(180f, 320f, 540f, 960f), 720f, 1280f)
        assertEquals(zoneFhd.xFraction, zoneHd.xFraction, EPSILON)
        assertEquals(zoneFhd.yFraction, zoneHd.yFraction, EPSILON)
        assertEquals(zoneFhd.widthFraction, zoneHd.widthFraction, EPSILON)
        assertEquals(zoneFhd.heightFraction, zoneHd.heightFraction, EPSILON)
    }

    companion object {
        private const val EPSILON = 0.001f
    }
}
