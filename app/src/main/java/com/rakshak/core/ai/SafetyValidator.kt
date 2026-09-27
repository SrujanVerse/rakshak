package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * SafetyValidator — enforces deterministic safety constraints over LLM advisory recommendations.
 *
 * Architecture Rule:
 *  - The Local LLM is an on-device reasoning engine, but its output is ADVISORY.
 *  - Hard deterministic rules enforce safety boundaries:
 *     1. If a crash is CONFIRMED with high confidence (>=0.85f), the LLM CANNOT downgrade the action
 *        to CANCEL_ALERT or LOG_ONLY without user interaction.
 *     2. Extreme acceleration spikes (>=30 m/s²) enforce minimum HIGH severity.
 */
object SafetyValidator {

    fun validate(incident: IncidentData, llmReasoning: LlmReasoningResult): LlmReasoningResult {
        var validatedAction = llmReasoning.recommendedAction
        var validatedSeverity = llmReasoning.severity

        // Rule 1: High confidence confirmed crashes must dispatch SMS or prompt user, never log-only or cancel
        if (incident.detectorState == "CONFIRMED" && incident.confidence >= 0.85f) {
            if (validatedAction == RecommendedAction.LOG_ONLY || validatedAction == RecommendedAction.CANCEL_ALERT) {
                validatedAction = RecommendedAction.DISPATCH_SMS
                validatedSeverity = IncidentSeverity.HIGH
            }
        }

        // Rule 2: Severe impact forces (>= 30 m/s²) enforce minimum HIGH severity
        if (incident.peakAcceleration >= 30.0f && (validatedSeverity == IncidentSeverity.LOW || validatedSeverity == IncidentSeverity.MODERATE)) {
            validatedSeverity = IncidentSeverity.HIGH
        }

        return llmReasoning.copy(
            severity = validatedSeverity,
            recommendedAction = validatedAction,
        )
    }
}
