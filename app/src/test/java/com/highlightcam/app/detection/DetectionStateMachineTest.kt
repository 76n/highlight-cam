package com.highlightcam.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectionStateMachineTest {
    private lateinit var sm: DetectionStateMachine

    private val ballInZoneResult =
        AnalysisResult(
            isCandidateEvent = true,
            confidence = 0.85f,
            ballDetected = true,
            ballInZone = true,
            playerCountInZone = 1,
            reason = "Ball in zone (conf 0.85)",
        )

    private val noEventResult =
        AnalysisResult(
            isCandidateEvent = false,
            confidence = 0f,
            ballDetected = false,
            ballInZone = false,
            playerCountInZone = 0,
            reason = "No event",
        )

    private val audioSpike =
        AudioEvent(
            energySpike = true,
            whistleDetected = false,
            currentRms = 0.5f,
            baselineRms = 0.1f,
        )

    private val quietAudio =
        AudioEvent(
            energySpike = false,
            whistleDetected = false,
            currentRms = 0.05f,
            baselineRms = 0.05f,
        )

    @Before
    fun setup() {
        sm = DetectionStateMachine()
        sm.start()
    }

    @Test
    fun `initial state after start is Monitoring`() {
        assertTrue(sm.state is DetectionState.Monitoring)
    }

    @Test
    fun `strong trigger — ball in zone with audio spike fires immediately`() {
        sm.lastAudioEvent = audioSpike

        val action = sm.onVisualResult(ballInZoneResult, now = 1000L)

        assertTrue(action is DetectionAction.TriggerSave)
        assertTrue(sm.state is DetectionState.Triggered)
    }

    @Test
    fun `strong trigger — ball in zone with whistle fires immediately`() {
        sm.lastAudioEvent =
            AudioEvent(
                energySpike = false,
                whistleDetected = true,
                currentRms = 0.3f,
                baselineRms = 0.1f,
            )

        val action = sm.onVisualResult(ballInZoneResult, now = 1000L)

        assertTrue(action is DetectionAction.TriggerSave)
        assertTrue(sm.state is DetectionState.Triggered)
    }

    @Test
    fun `weak visual alone enters CandidatePending`() {
        sm.lastAudioEvent = quietAudio

        val action = sm.onVisualResult(ballInZoneResult, now = 1000L)

        assertTrue(action is DetectionAction.EmitCandidate)
        assertTrue(sm.state is DetectionState.CandidatePending)
    }

    @Test
    fun `weak visual alone does not fire after timeout`() {
        sm.lastAudioEvent = quietAudio
        sm.onVisualResult(ballInZoneResult, now = 1000L)

        val action = sm.onVisualResult(noEventResult, now = 4000L)

        assertTrue(action is DetectionAction.None)
        assertTrue(sm.state is DetectionState.Monitoring)
    }

    @Test
    fun `weak visual + audio within window fires`() {
        sm.lastAudioEvent = quietAudio
        sm.onVisualResult(ballInZoneResult, now = 1000L)
        assertTrue(sm.state is DetectionState.CandidatePending)

        val action = sm.onAudioEvent(audioSpike, now = 2000L)

        assertTrue(action is DetectionAction.TriggerSave)
        assertTrue(sm.state is DetectionState.Triggered)
    }

    @Test
    fun `audio spike alone enters CandidatePending`() {
        val action = sm.onAudioEvent(audioSpike, now = 1000L)

        assertTrue(action is DetectionAction.EmitCandidate)
        assertTrue(sm.state is DetectionState.CandidatePending)
        val candidate = sm.state as DetectionState.CandidatePending
        assertTrue(candidate.audioConfirmed)
    }

    @Test
    fun `audio alone + visual confirmation within 1500ms fires`() {
        sm.onAudioEvent(audioSpike, now = 1000L)

        val action = sm.onVisualResult(ballInZoneResult, now = 2000L)

        assertTrue(action is DetectionAction.TriggerSave)
        assertTrue(sm.state is DetectionState.Triggered)
    }

    @Test
    fun `audio alone times out after 1500ms`() {
        sm.onAudioEvent(audioSpike, now = 1000L)

        val action = sm.onVisualResult(noEventResult, now = 3000L)

        assertTrue(action is DetectionAction.None)
        assertTrue(sm.state is DetectionState.Monitoring)
    }

    @Test
    fun `cooldown prevents double trigger within 12 seconds`() {
        sm.lastAudioEvent = audioSpike
        val firstAction = sm.onVisualResult(ballInZoneResult, now = 1000L)
        assertTrue(firstAction is DetectionAction.TriggerSave)

        sm.lastAudioEvent = audioSpike
        val secondAction = sm.onVisualResult(ballInZoneResult, now = 5000L)
        assertTrue(secondAction is DetectionAction.None)
        assertTrue(sm.state is DetectionState.Triggered)
    }

    @Test
    fun `cooldown expires after 12 seconds allows new trigger`() {
        sm.lastAudioEvent = audioSpike
        sm.onVisualResult(ballInZoneResult, now = 1000L)

        sm.onVisualResult(noEventResult, now = 14_000L)
        assertTrue(sm.state is DetectionState.Monitoring)

        sm.lastAudioEvent = audioSpike
        val action = sm.onVisualResult(ballInZoneResult, now = 14_500L)
        assertTrue(action is DetectionAction.TriggerSave)
    }

    @Test
    fun `state label reflects current state`() {
        assertEquals("Monitoring", sm.stateLabel)

        sm.lastAudioEvent = audioSpike
        sm.onVisualResult(ballInZoneResult, now = 1000L)
        assertEquals("Triggered", sm.stateLabel)
    }

    @Test
    fun `stop resets to Idle`() {
        sm.stop()
        assertTrue(sm.state is DetectionState.Idle)
        assertEquals("Idle", sm.stateLabel)
    }
}
