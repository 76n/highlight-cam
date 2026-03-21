package com.highlightcam.app.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipAssemblerTest {
    @Test
    fun `single segment no trim returns zero offset`() {
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 0,
                segmentDurationsUs = listOf(3_000_000L),
                trimLeadingUs = 0L,
            )
        assertEquals(0L, offset)
    }

    @Test
    fun `single segment with trim returns negative offset`() {
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 0,
                segmentDurationsUs = listOf(3_000_000L),
                trimLeadingUs = 1_000_000L,
            )
        assertEquals(-1_000_000L, offset)
    }

    @Test
    fun `second segment no trim returns first segment duration`() {
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 1,
                segmentDurationsUs = listOf(3_000_000L, 3_000_000L),
                trimLeadingUs = 0L,
            )
        assertEquals(3_000_000L, offset)
    }

    @Test
    fun `third segment no trim returns sum of first two`() {
        val durations = listOf(3_000_000L, 3_000_000L, 3_000_000L)
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 2,
                segmentDurationsUs = durations,
                trimLeadingUs = 0L,
            )
        assertEquals(6_000_000L, offset)
    }

    @Test
    fun `second segment with trim subtracts trim from accumulated`() {
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 1,
                segmentDurationsUs = listOf(3_000_000L, 3_000_000L),
                trimLeadingUs = 500_000L,
            )
        assertEquals(2_500_000L, offset)
    }

    @Test
    fun `fourth segment with non-uniform durations`() {
        val durations = listOf(2_000_000L, 4_000_000L, 3_000_000L, 5_000_000L)
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 3,
                segmentDurationsUs = durations,
                trimLeadingUs = 0L,
            )
        assertEquals(9_000_000L, offset)
    }

    @Test
    fun `zero trim on zero index returns zero`() {
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 0,
                segmentDurationsUs = listOf(5_000_000L, 5_000_000L),
                trimLeadingUs = 0L,
            )
        assertEquals(0L, offset)
    }

    @Test
    fun `large trim exceeding first segment produces negative offset`() {
        val offset =
            ClipAssembler.timestampOffsetUs(
                segmentIndex = 0,
                segmentDurationsUs = listOf(3_000_000L),
                trimLeadingUs = 5_000_000L,
            )
        assertEquals(-5_000_000L, offset)
    }
}
