package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData
import com.rakshak.core.verification.IncidentVerificationData

/**
 * SafetyValidator — authoritative deterministic safety boundary over LLM advisory output.
 *
 * ABSOLUTE SAFETY RULES:
 *  1. NORMAL, NO_INCIDENT, MINOR_EVENT, MINOR_MOVEMENT, UNUSUAL_MOVEMENT, or INCONCLUSIVE classifications CAN NEVER SEND SOS.
 *  2. LOW severity CAN NEVER SEND SOS.
 *  3. Absence of rider response alone with inconclusive camera/voice evidence MUST NOT trigger SOS.
 *  4. Rider tapping I'M OKAY immediately cancels emergency workflow.
 *  5. DISPATCH_SMS is ONLY authorized when ALL genuine crash criteria are satisfied:
 *     - Detector state is CONFIRMED / CONFIRMED_INCIDENT
 *     - Crash confidence >= 0.85f
 *     - Peak acceleration >= 35.0 m/s²
 *     - Peak gyroscope >= 5.0 rad/s
 *     - Rider post-event movement is NOT "normal"
 */
object SafetyValidator {

    fun validate(incident: IncidentData, llmReasoning: LlmReasoningResult?): LlmReasoningResult {
        val safeReasoning = llmReasoning ?: LlmReasoningResult(
            classification = EventClassification.INCONCLUSIVE,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "LLM reasoning output unavailable or malformed. Falling back to safety rules.",
            report = "Inconclusive assessment.",
            confidence = 0.0f
        )

        // Check if sensor evidence meets genuine severe emergency criteria
        val meetsEmergencyCriteria = (incident.detectorState == "CONFIRMED" || incident.detectorState == "CONFIRMED_INCIDENT") &&
                incident.confidence >= 0.85f &&
                incident.peakAcceleration >= 35.0f &&
                incident.peakGyroscope >= 5.0f &&
                incident.riderMovement != "normal"

        if (meetsEmergencyCriteria) {
            val finalSeverity = if (safeReasoning.severity == IncidentSeverity.CRITICAL) IncidentSeverity.CRITICAL else IncidentSeverity.HIGH
            return safeReasoning.copy(
                severity = finalSeverity,
                recommendedAction = RecommendedAction.DISPATCH_SMS,
                classification = EventClassification.POSSIBLE_INCIDENT
            )
        }

        var validatedAction = safeReasoning.recommendedAction
        var validatedSeverity = safeReasoning.severity
        val classification = safeReasoning.classification

        // ── RULE 1: Non-incident & Inconclusive Classification Guard ──
        val isNonIncidentClass = classification == EventClassification.NORMAL ||
                classification == EventClassification.NO_INCIDENT ||
                classification == EventClassification.MINOR_EVENT ||
                classification == EventClassification.MINOR_MOVEMENT ||
                classification == EventClassification.UNUSUAL_MOVEMENT ||
                classification == EventClassification.INCONCLUSIVE

        if (isNonIncidentClass) {
            if (validatedSeverity == IncidentSeverity.HIGH || validatedSeverity == IncidentSeverity.CRITICAL) {
                validatedSeverity = IncidentSeverity.MODERATE
            }
            if (validatedAction == RecommendedAction.DISPATCH_SMS || validatedAction == RecommendedAction.SEND_SOS) {
                validatedAction = if (validatedSeverity == IncidentSeverity.LOW) RecommendedAction.LOG_ONLY else RecommendedAction.PROMPT_USER
            }
        }

        // ── RULE 2: Weak / Inconclusive Evidence Cap ──
        val isWeakEvidence = incident.peakAcceleration < 35.0f ||
                incident.confidence < 0.85f ||
                incident.peakGyroscope < 4.0f ||
                incident.cameraVerification == "inconclusive" ||
                incident.cameraVerification == "unavailable" ||
                incident.audioVerification == "unavailable"

        if (isWeakEvidence) {
            if (validatedAction == RecommendedAction.DISPATCH_SMS || validatedAction == RecommendedAction.SEND_SOS) {
                validatedAction = if (validatedSeverity == IncidentSeverity.LOW) RecommendedAction.LOG_ONLY else RecommendedAction.PROMPT_USER
            }
            if (validatedSeverity == IncidentSeverity.CRITICAL || validatedSeverity == IncidentSeverity.HIGH) {
                validatedSeverity = IncidentSeverity.MODERATE
            }
        }

        // ── RULE 3: LOW SEVERITY CAN NEVER SEND SOS ──
        if (validatedSeverity == IncidentSeverity.LOW && (validatedAction == RecommendedAction.DISPATCH_SMS || validatedAction == RecommendedAction.SEND_SOS)) {
            validatedAction = RecommendedAction.LOG_ONLY
        }

        return safeReasoning.copy(
            severity = validatedSeverity,
            recommendedAction = validatedAction,
            classification = classification,
        )
    }

    fun validateVerification(data: IncidentVerificationData, llmReasoning: LlmReasoningResult?): LlmReasoningResult {
        val safeReasoning = llmReasoning ?: LlmReasoningResult(
            classification = EventClassification.INCONCLUSIVE,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Local AI output unavailable or malformed. Falling back to deterministic safety rules.",
            report = "Inconclusive verification assessment.",
            confidence = 0.0f
        )

        // Rule 0: Rider safe ("I'M OKAY") always wins
        if (data.userResponded) {
            return safeReasoning.copy(
                classification = EventClassification.NO_INCIDENT,
                severity = IncidentSeverity.LOW,
                recommendedAction = RecommendedAction.CANCEL_ALERT,
                explanation = "Rider explicitly confirmed safety. Alert cancelled."
            )
        }

        val meetsEmergencyCriteria = data.accelerationEvidence >= 35.0f &&
                data.rotationEvidence >= 5.0f &&
                data.tfliteEvidence >= 0.85f &&
                data.postEventMovement != "normal"

        if (meetsEmergencyCriteria && safeReasoning.classification == EventClassification.SERIOUS_INCIDENT) {
            val finalSeverity = if (safeReasoning.severity == IncidentSeverity.CRITICAL) IncidentSeverity.CRITICAL else IncidentSeverity.HIGH
            return safeReasoning.copy(
                severity = finalSeverity,
                recommendedAction = RecommendedAction.DISPATCH_SMS,
                classification = EventClassification.SERIOUS_INCIDENT
            )
        }

        var validatedAction = safeReasoning.recommendedAction
        var validatedSeverity = safeReasoning.severity
        val classification = safeReasoning.classification

        val isNonIncidentClass = classification == EventClassification.NORMAL ||
                classification == EventClassification.NO_INCIDENT ||
                classification == EventClassification.MINOR_EVENT ||
                classification == EventClassification.MINOR_MOVEMENT ||
                classification == EventClassification.UNUSUAL_MOVEMENT ||
                classification == EventClassification.INCONCLUSIVE

        if (isNonIncidentClass) {
            if (validatedSeverity == IncidentSeverity.HIGH || validatedSeverity == IncidentSeverity.CRITICAL) {
                validatedSeverity = IncidentSeverity.MODERATE
            }
            if (validatedAction == RecommendedAction.DISPATCH_SMS || validatedAction == RecommendedAction.SEND_SOS) {
                validatedAction = if (validatedSeverity == IncidentSeverity.LOW) RecommendedAction.LOG_ONLY else RecommendedAction.PROMPT_USER
            }
        }

        if (validatedSeverity == IncidentSeverity.LOW && (validatedAction == RecommendedAction.DISPATCH_SMS || validatedAction == RecommendedAction.SEND_SOS)) {
            validatedAction = RecommendedAction.LOG_ONLY
        }

        return safeReasoning.copy(
            severity = validatedSeverity,
            recommendedAction = validatedAction,
            classification = classification
        )
    }
}
