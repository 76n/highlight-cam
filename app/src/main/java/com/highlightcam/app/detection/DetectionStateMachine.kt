package com.highlightcam.app.detection

sealed class DetectionState {
    data object Idle : DetectionState()

    data object Monitoring : DetectionState()

    data class CandidatePending(
        val since: Long,
        val visualConfirmed: Boolean,
        val audioConfirmed: Boolean,
        val confidence: Float = 0f,
        val reason: String = "",
    ) : DetectionState()

    data class Triggered(val cooldownUntil: Long) : DetectionState()
}

sealed class DetectionAction {
    data object None : DetectionAction()

    data class EmitCandidate(val confidence: Float) : DetectionAction()

    data class TriggerSave(val confidence: Float, val reason: String) : DetectionAction()
}

class DetectionStateMachine {
    var state: DetectionState = DetectionState.Idle
        private set

    var lastAudioEvent: AudioEvent? = null

    fun start() {
        state = DetectionState.Monitoring
        lastAudioEvent = null
    }

    fun stop() {
        state = DetectionState.Idle
        lastAudioEvent = null
    }

    fun onVisualResult(
        result: AnalysisResult,
        now: Long,
    ): DetectionAction {
        expireTimeouts(now)

        return when (val s = state) {
            is DetectionState.Monitoring -> handleVisualInMonitoring(result, now)
            is DetectionState.CandidatePending -> handleVisualInCandidate(s, result, now)
            is DetectionState.Triggered -> handleCooldown(s, now)
            is DetectionState.Idle -> DetectionAction.None
        }
    }

    fun onAudioEvent(
        event: AudioEvent,
        now: Long,
    ): DetectionAction {
        lastAudioEvent = event
        expireTimeouts(now)

        val isAudioTrigger = event.energySpike || event.whistleDetected
        if (!isAudioTrigger) return DetectionAction.None

        return when (val s = state) {
            is DetectionState.Monitoring -> {
                state =
                    DetectionState.CandidatePending(
                        since = now,
                        visualConfirmed = false,
                        audioConfirmed = true,
                    )
                DetectionAction.EmitCandidate(AUDIO_ONLY_CONFIDENCE)
            }
            is DetectionState.CandidatePending -> {
                if (!s.audioConfirmed) {
                    if (s.visualConfirmed) {
                        val reason = s.reason.ifEmpty { "Visual + audio confirmed" }
                        state = DetectionState.Triggered(now + COOLDOWN_MS)
                        DetectionAction.TriggerSave(s.confidence, reason)
                    } else {
                        state = s.copy(audioConfirmed = true)
                        DetectionAction.None
                    }
                } else {
                    DetectionAction.None
                }
            }
            is DetectionState.Triggered -> DetectionAction.None
            is DetectionState.Idle -> DetectionAction.None
        }
    }

    private fun handleVisualInMonitoring(
        result: AnalysisResult,
        now: Long,
    ): DetectionAction {
        if (!result.isCandidateEvent) return DetectionAction.None

        val audio = lastAudioEvent
        if (audio != null && (audio.energySpike || audio.whistleDetected)) {
            state = DetectionState.Triggered(now + COOLDOWN_MS)
            return DetectionAction.TriggerSave(result.confidence, result.reason)
        }

        state =
            DetectionState.CandidatePending(
                since = now,
                visualConfirmed = true,
                audioConfirmed = false,
                confidence = result.confidence,
                reason = result.reason,
            )
        return DetectionAction.EmitCandidate(result.confidence)
    }

    private fun handleVisualInCandidate(
        s: DetectionState.CandidatePending,
        result: AnalysisResult,
        now: Long,
    ): DetectionAction {
        if (!result.isCandidateEvent) return DetectionAction.None

        if (!s.visualConfirmed) {
            val updated = s.copy(visualConfirmed = true, confidence = result.confidence, reason = result.reason)
            if (updated.audioConfirmed) {
                state = DetectionState.Triggered(now + COOLDOWN_MS)
                return DetectionAction.TriggerSave(result.confidence, result.reason)
            }
            state = updated
        }
        return DetectionAction.None
    }

    private fun handleCooldown(
        s: DetectionState.Triggered,
        now: Long,
    ): DetectionAction {
        if (now >= s.cooldownUntil) {
            state = DetectionState.Monitoring
        }
        return DetectionAction.None
    }

    private fun expireTimeouts(now: Long) {
        val s = state
        if (s is DetectionState.CandidatePending) {
            val timeout = if (s.audioConfirmed && !s.visualConfirmed) AUDIO_TIMEOUT_MS else VISUAL_TIMEOUT_MS
            if (now - s.since > timeout) {
                state = DetectionState.Monitoring
            }
        }
    }

    val stateLabel: String
        get() =
            when (state) {
                is DetectionState.Idle -> "Idle"
                is DetectionState.Monitoring -> "Monitoring"
                is DetectionState.CandidatePending -> "Candidate"
                is DetectionState.Triggered -> "Triggered"
            }

    companion object {
        const val COOLDOWN_MS = 12_000L
        const val VISUAL_TIMEOUT_MS = 2_500L
        const val AUDIO_TIMEOUT_MS = 1_500L
        const val AUDIO_ONLY_CONFIDENCE = 0.5f
    }
}
