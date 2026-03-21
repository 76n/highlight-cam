package com.highlightcam.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSegmentsTest {
    @Test
    fun `careful sensitivity maps to 0_2`() {
        assertEquals(0.2f, SettingsViewModel.SENSITIVITY_CAREFUL, 0.001f)
    }

    @Test
    fun `balanced sensitivity maps to 0_5`() {
        assertEquals(0.5f, SettingsViewModel.SENSITIVITY_BALANCED, 0.001f)
    }

    @Test
    fun `aggressive sensitivity maps to 0_8`() {
        assertEquals(0.8f, SettingsViewModel.SENSITIVITY_AGGRESSIVE, 0.001f)
    }

    @Test
    fun `sensitivity segment index 0 maps to careful`() {
        val value =
            when (0) {
                0 -> SettingsViewModel.SENSITIVITY_CAREFUL
                1 -> SettingsViewModel.SENSITIVITY_BALANCED
                else -> SettingsViewModel.SENSITIVITY_AGGRESSIVE
            }
        assertEquals(0.2f, value, 0.001f)
    }

    @Test
    fun `sensitivity segment index 1 maps to balanced`() {
        val value =
            when (1) {
                0 -> SettingsViewModel.SENSITIVITY_CAREFUL
                1 -> SettingsViewModel.SENSITIVITY_BALANCED
                else -> SettingsViewModel.SENSITIVITY_AGGRESSIVE
            }
        assertEquals(0.5f, value, 0.001f)
    }

    @Test
    fun `sensitivity segment index 2 maps to aggressive`() {
        val value =
            when (2) {
                0 -> SettingsViewModel.SENSITIVITY_CAREFUL
                1 -> SettingsViewModel.SENSITIVITY_BALANCED
                else -> SettingsViewModel.SENSITIVITY_AGGRESSIVE
            }
        assertEquals(0.8f, value, 0.001f)
    }

    @Test
    fun `sensitivity value maps back to correct segment index`() {
        assertEquals(0, sensitivityToIndex(0.2f))
        assertEquals(1, sensitivityToIndex(0.5f))
        assertEquals(2, sensitivityToIndex(0.8f))
    }

    private fun sensitivityToIndex(sensitivity: Float): Int =
        when {
            sensitivity <= 0.3f -> 0
            sensitivity <= 0.6f -> 1
            else -> 2
        }
}
