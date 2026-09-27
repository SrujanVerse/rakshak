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
 *     2. Extreme acceleration spikes (>= 35 m/s²) with high confidence enforce minimum HIGH severity.
 *     3. Weak evidence (low accel, low confidence) MUST NOT be escalated beyond MODERATE.
 *
 * Key principle: SafetyValidator prevents BOTH false negatives AND false positives.
 *  - It blocks LLM from downgrading confirmed high-confidence crashes.
 *  - It blocks escalation when evidence is weak (prevents small movements → HIGH).
 */
object SafetyValidator {

    fun validate(incident: IncidentData, llmReasoning: LlmReasoningResult): LlmReasoningResult {
        var validatedAction = llmReasoning.recommendedAction
        var validatedSeverity = llmReasoning.severity

        // ── Rule 1: Prevent downgrading CONFIRMED high-confidence crashes ──
        // If the deterministic FSM confirmed a crash with high confidence,
        // the LLM cannot cancel or log-only.
        if (incident.detectorState == "CONFIRMED" && incident.confidence >= 0.85f) {
            if (validatedAction == RecommendedAction.LOG_ONLY || validatedAction == RecommendedAction.CANCEL_ALERT) {
                validatedAction = RecommendedAction.DISPATCH_SMS
                validatedSeverity = IncidentSeverity.HIGH
            }
        }

        // ── Rule 2: Extreme force with high confidence → minimum HIGH severity ──
        // Raised threshold from 30 to 35 m/s² and requires high confidence to prevent
        // strong phone shakes from being forced to HIGH.
        if (incident.peakAcceleration >= 35.0f && incident.confidence >= 0.85f &&
            (validatedSeverity == IncidentSeverity.LOW || validatedSeverity == IncidentSeverity.MODERATE)) {
            validatedSeverity = IncidentSeverity.HIGH
        }

        // ── Rule 3: Weak evidence cap — prevent false positive escalation ──
        // If peak acceleration is below impact level AND confidence is below 0.80,
        // the severity MUST NOT exceed MODERATE regardless of what the LLM says.
        // This prevents: small left-right movement → LLM says HIGH → SOS sent
        if (incident.peakAcceleration < 30.0f && incident.confidence < 0.80f) {
            if (validatedSeverity == IncidentSeverity.HIGH || validatedSeverity == IncidentSeverity.CRITICAL) {
                validatedSeverity = IncidentSeverity.MODERATE
            }
            if (validatedAction == RecommendedAction.DISPATCH_SMS) {
                validatedAction = RecommendedAction.PROMPT_USER
            }
        }

        return llmReasoning.copy(
            severity = validatedSeverity,
            recommendedAction = validatedAction,
        )
    }
}
