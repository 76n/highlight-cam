package com.highlightcam.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingConfigTest {
    @Test
    fun `totalBufferSeconds with default values`() {
        val config = RecordingConfig()
        assertEquals(30, config.totalBufferSeconds)
    }

    @Test
    fun `totalBufferSeconds with custom values`() {
        val config = RecordingConfig(segmentDurationSeconds = 5, bufferSegments = 6)
        assertEquals(30, config.totalBufferSeconds)
    }

    @Test
    fun `totalBufferSeconds reflects changed segment duration`() {
        val config = RecordingConfig(segmentDurationSeconds = 2, bufferSegments = 10)
        assertEquals(20, config.totalBufferSeconds)
    }

    @Test
    fun `totalBufferSeconds reflects changed buffer segments`() {
        val config = RecordingConfig(segmentDurationSeconds = 3, bufferSegments = 20)
        assertEquals(60, config.totalBufferSeconds)
    }
}
