package com.highlightcam.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropWindowTest {
    @Test
    fun `FULL_FRAME toRect covers entire frame`() {
        val rect = CropWindow.FULL_FRAME.toRect()
        assertEquals(0f, rect.left, EPSILON)
        assertEquals(0f, rect.top, EPSILON)
        assertEquals(1f, rect.right, EPSILON)
        assertEquals(1f, rect.bottom, EPSILON)
    }

    @Test
    fun `scale 2_0 at center crops to 50 percent`() {
        val rect = CropWindow(0.5f, 0.5f, 2.0f).toRect()
        assertEquals(0.25f, rect.left, EPSILON)
        assertEquals(0.25f, rect.top, EPSILON)
        assertEquals(0.75f, rect.right, EPSILON)
        assertEquals(0.75f, rect.bottom, EPSILON)
    }

    @Test
    fun `scale 1_5 at center`() {
        val rect = CropWindow(0.5f, 0.5f, 1.5f).toRect()
        val halfW = 1f / 3f
        assertEquals(0.5f - halfW, rect.left, EPSILON)
        assertEquals(0.5f - halfW, rect.top, EPSILON)
        assertEquals(0.5f + halfW, rect.right, EPSILON)
        assertEquals(0.5f + halfW, rect.bottom, EPSILON)
    }

    @Test
    fun `rect clamped to 0-1 bounds`() {
        val rect = CropWindow(0.1f, 0.1f, 2.0f).toRect()
        assertTrue(rect.left >= 0f)
        assertTrue(rect.top >= 0f)
        assertTrue(rect.right <= 1f)
        assertTrue(rect.bottom <= 1f)
    }

    @Test
    fun `scale 2_0 off center`() {
        val rect = CropWindow(0.3f, 0.7f, 2.0f).toRect()
        assertEquals(0.5f, rect.width(), EPSILON)
        assertEquals(0.5f, rect.height(), EPSILON)
    }

    companion object {
        private const val EPSILON = 0.001f
    }
}
