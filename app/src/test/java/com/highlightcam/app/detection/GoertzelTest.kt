package com.highlightcam.app.detection

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class GoertzelTest {
    @Test
    fun `detects pure 2500Hz sine wave`() {
        val sampleRate = 44100
        val numSamples = 4096
        val samples = generateSine(2500f, sampleRate, numSamples)

        val power2500 = Goertzel.power(samples, sampleRate, 2500f, numSamples)
        val power1000 = Goertzel.power(samples, sampleRate, 1000f, numSamples)

        assertTrue(
            "2500Hz signal should have much higher power at 2500Hz than at 1000Hz, " +
                "got power2500=$power2500 power1000=$power1000",
            power2500 > power1000 * 10,
        )
    }

    @Test
    fun `does not trigger 2500Hz on 1000Hz sine`() {
        val sampleRate = 44100
        val numSamples = 4096
        val samples = generateSine(1000f, sampleRate, numSamples)

        val power2500 = Goertzel.power(samples, sampleRate, 2500f, numSamples)
        val power1000 = Goertzel.power(samples, sampleRate, 1000f, numSamples)

        assertTrue(
            "1000Hz signal should have much higher power at 1000Hz than at 2500Hz, " +
                "got power1000=$power1000 power2500=$power2500",
            power1000 > power2500 * 10,
        )
    }

    @Test
    fun `detects 3200Hz whistle frequency`() {
        val sampleRate = 44100
        val numSamples = 4096
        val samples = generateSine(3200f, sampleRate, numSamples)

        val power3200 = Goertzel.power(samples, sampleRate, 3200f, numSamples)
        val power1000 = Goertzel.power(samples, sampleRate, 1000f, numSamples)

        assertTrue(
            "3200Hz signal should dominate at 3200Hz",
            power3200 > power1000 * 10,
        )
    }

    @Test
    fun `silent input produces near-zero power`() {
        val sampleRate = 44100
        val numSamples = 4096
        val samples = ShortArray(numSamples)

        val power = Goertzel.power(samples, sampleRate, 2500f, numSamples)

        assertTrue("Silent input should produce near-zero power, got $power", power < 0.001f)
    }

    @Suppress("MagicNumber")
    private fun generateSine(
        freq: Float,
        sampleRate: Int,
        numSamples: Int,
    ): ShortArray =
        ShortArray(numSamples) { i ->
            (0.5f * Short.MAX_VALUE * sin(2.0 * Math.PI * freq * i / sampleRate))
                .toInt()
                .toShort()
        }
}
