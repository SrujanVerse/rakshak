package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * SafetyValidator — authoritative deterministic safety boundary over LLM advisory output.
 *
 * ABSOLUTE SAFETY RULES:
 *  1. LOW severity MUST NEVER send SOS. If severity == LOW, action MUST be LOG_ONLY or CANCEL_ALERT or PROMPT_USER.
 *  2. DISPATCH_SMS is ONLY authorized when ALL 4 conditions are met:
 *     - Detector state is CONFIRMED / CONFIRMED_INCIDENT
 *     - Crash confidence >= 0.85f
 *     - Peak acceleration >= 32.0 m/s²
 *     - Peak gyroscope >= 4.0 rad/s
 *  3. Weak evidence (accel < 30 m/s², gyro < 3.5 rad/s, or confidence < 0.80) MUST NOT exceed MODERATE severity.
 *  4. Qwen is ADVISORY. SafetyValidator is AUTHORITATIVE.
 */
object SafetyValidator {

    fun validate(incident: IncidentData, llmReasoning: LlmReasoningResult): LlmReasoningResult {
        var validatedAction = llmReasoning.recommendedAction
        var validatedSeverity = llmReasoning.severity

        // ── RULE 1: Weak evidence cap — prevent false positive escalation ──
        val isWeakEvidence = incident.peakAcceleration < 30.0f ||
                incident.confidence < 0.80f ||
                incident.peakGyroscope < 3.5f

        if (isWeakEvidence) {
            if (validatedSeverity == IncidentSeverity.HIGH || validatedSeverity == IncidentSeverity.CRITICAL) {
                validatedSeverity = IncidentSeverity.MODERATE
            }
            if (validatedAction == RecommendedAction.DISPATCH_SMS) {
                validatedAction = RecommendedAction.PROMPT_USER
            }
        }

        // ── RULE 2: ABSOLUTE GUARD ON DISPATCH_SMS ──
        // DISPATCH_SMS is ONLY permitted if ALL genuine crash criteria are satisfied
        val meetsEmergencyCriteria = (incident.detectorState == "CONFIRMED" || incident.detectorState == "CONFIRMED_INCIDENT") &&
                incident.confidence >= 0.85f &&
                incident.peakAcceleration >= 32.0f &&
                incident.peakGyroscope >= 4.0f

        if (validatedAction == RecommendedAction.DISPATCH_SMS && !meetsEmergencyCriteria) {
            // Downgrade action — evidence does not justify SMS alert
            validatedAction = if (validatedSeverity == IncidentSeverity.LOW) RecommendedAction.LOG_ONLY else RecommendedAction.PROMPT_USER
        }

        // ── RULE 3: LOW SEVERITY CAN NEVER SEND SOS ──
        if (validatedSeverity == IncidentSeverity.LOW && validatedAction == RecommendedAction.DISPATCH_SMS) {
            validatedAction = RecommendedAction.LOG_ONLY
        }

        // ── RULE 4: Confirmed genuine severe crash enforces minimum HIGH severity ──
        if (meetsEmergencyCriteria && (validatedSeverity == IncidentSeverity.LOW || validatedSeverity == IncidentSeverity.MODERATE)) {
            validatedSeverity = IncidentSeverity.HIGH
            validatedAction = RecommendedAction.DISPATCH_SMS
        }

        return llmReasoning.copy(
            severity = validatedSeverity,
            recommendedAction = validatedAction,
        )
    }
}
