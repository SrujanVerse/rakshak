package com.rakshak.core.detector

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IncidentDecisionState — user-facing monitoring state.
 */
enum class IncidentDecisionState {
    /** Normal movement — monitoring active, no action needed */
    NORMAL,

    /** Unusual or rapid movement detected — checking, no emergency triggered */
    SUSPICIOUS,

    /** Strong multi-signal evidence confirmed — emergency countdown required */
    CONFIRMED_INCIDENT
}

/**
 * IncidentDecisionEngine — deterministic multi-signal gate for incident classification.
 *
 * Architecture Rule:
 *  - High sensor values alone (e.g., picking up phone, shaking) do NOT trigger emergency.
 *  - ML probability alone (>0.5) does NOT trigger emergency.
 *  - Combines TFLite confidence, peak acceleration magnitude, jerk, and FSM safety state
 *    to prevent false positives during ordinary phone movement.
 */
object IncidentDecisionEngine {

    private const val TAG = "IncidentDecisionEngine"

    private val _decisionState = MutableStateFlow(IncidentDecisionState.NORMAL)
    val decisionState: StateFlow<IncidentDecisionState> = _decisionState.asStateFlow()

    private val _userStatusText = MutableStateFlow("Monitoring your movement...")
    val userStatusText: StateFlow<String> = _userStatusText.asStateFlow()

    /** Timestamp of last suspicious event to auto-reset to NORMAL after quiet period */
    private var lastSuspiciousTimestampMs: Long = 0L

    @Synchronized
    fun evaluate(
        crashProbability: Float,
        peakAccelMagnitude: Float,
        peakGyroMagnitude: Float,
        fsmState: DetectorState
    ): IncidentDecisionState {
        val now = System.currentTimeMillis()

        // 1. CONFIRMED INCIDENT GATE (Multi-signal requirement)
        // Requires FSM CONFIRMED state OR (Strong impact force >= 32 m/s² AND high ML confidence >= 0.85)
        val isConfirmed = fsmState == DetectorState.CONFIRMED ||
                (peakAccelMagnitude >= 32.0f && crashProbability >= 0.85f)

        if (isConfirmed) {
            if (_decisionState.value != IncidentDecisionState.CONFIRMED_INCIDENT) {
                Log.w(TAG, "[RAKSHAK_INCIDENT_CONFIRMED] Multi-signal gate PASSED. PeakAccel=%.2f, CrashProb=%.2f, FSM=%s"
                    .format(peakAccelMagnitude, crashProbability, fsmState))
                _decisionState.value = IncidentDecisionState.CONFIRMED_INCIDENT
                _userStatusText.value = "⚠️ Possible incident detected! Checking safety..."
            }
            return IncidentDecisionState.CONFIRMED_INCIDENT
        }

        // 2. SUSPICIOUS MOVEMENT GATE
        // Moderate acceleration spike (>=22 m/s²) or elevated ML probability (>=0.75f)
        val isSuspicious = peakAccelMagnitude >= 22.0f || crashProbability >= 0.75f ||
                fsmState == DetectorState.IMPACT_CANDIDATE || fsmState == DetectorState.CONFIRMING

        if (isSuspicious) {
            lastSuspiciousTimestampMs = now
            if (_decisionState.value != IncidentDecisionState.SUSPICIOUS) {
                Log.i(TAG, "[RAKSHAK_SUSPICIOUS_MOVEMENT] Unusual movement detected. PeakAccel=%.2f, CrashProb=%.2f"
                    .format(peakAccelMagnitude, crashProbability))
                _decisionState.value = IncidentDecisionState.SUSPICIOUS
                _userStatusText.value = "Unusual movement detected — RAKSHAK is checking..."
            }
            return IncidentDecisionState.SUSPICIOUS
        }

        // 3. NORMAL MOVEMENT
        // Auto-recover to NORMAL after 2.5 seconds of quiet movement
        if (_decisionState.value == IncidentDecisionState.SUSPICIOUS && (now - lastSuspiciousTimestampMs > 2500L)) {
            Log.i(TAG, "[RAKSHAK_NORMAL_MOVEMENT] Movement returned to normal bounds.")
            _decisionState.value = IncidentDecisionState.NORMAL
            _userStatusText.value = "✓ Normal movement — RAKSHAK is monitoring"
        } else if (_decisionState.value == IncidentDecisionState.NORMAL) {
            _userStatusText.value = "✓ Normal movement — RAKSHAK is monitoring"
        }

        return _decisionState.value
    }

    /** Reset state back to NORMAL (e.g., after user taps I'M GOOD) */
    @Synchronized
    fun resetToNormal() {
        Log.i(TAG, "[RAKSHAK_RESET_NORMAL] Decision state reset to NORMAL by user action.")
        _decisionState.value = IncidentDecisionState.NORMAL
        _userStatusText.value = "✓ Normal movement — RAKSHAK is monitoring"
    }
}
