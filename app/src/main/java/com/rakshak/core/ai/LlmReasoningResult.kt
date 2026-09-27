package com.rakshak.core.ai

/**
 * Severity levels assessed by the LLM reasoning brain.
 */
enum class IncidentSeverity {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

/**
 * Actions recommended by the LLM reasoning brain.
 */
enum class RecommendedAction {
    DISPATCH_SMS,
    PROMPT_USER,
    LOG_ONLY,
    CANCEL_ALERT,
    MONITOR,
    VERIFY,
    SEND_SOS
}

/**
 * Event classifications produced by Qwen Event Interpreter.
 */
enum class EventClassification {
    NORMAL,
    MINOR_MOVEMENT,
    UNUSUAL_MOVEMENT,
    INCONCLUSIVE,
    POSSIBLE_INCIDENT,
    NO_INCIDENT,
    MINOR_EVENT,
    SERIOUS_INCIDENT
}

/**
 * LlmReasoningResult — structured output from the on-device LLM reasoning brain.
 */
data class LlmReasoningResult(
    val severity: IncidentSeverity,
    val recommendedAction: RecommendedAction,
    val explanation: String,
    val report: String,
    val classification: EventClassification = EventClassification.NORMAL,
    val confidence: Float = 0.5f,
) {
    fun toJsonString(): String {
        return buildString {
            append("{")
            append("\"classification\":\"${classification.name}\",")
            append("\"severity\":\"${severity.name}\",")
            append("\"action\":\"${recommendedAction.name}\",")
            append("\"confidence\":$confidence,")
            append("\"reason\":\"${escapeJson(explanation)}\"")
            append("}")
        }
    }

    companion object {
        /**
         * Safely parse LLM text output into [LlmReasoningResult].
         * Supports both Qwen output contract keys ("classification", "severity", "recommendedAction", "action", "reason")
         * and legacy keys ("recommended_action", "explanation", "report").
         */
        fun parseJson(jsonString: String): LlmReasoningResult? {
            return try {
                var cleaned = jsonString.trim()
                if (cleaned.startsWith("```json")) {
                    cleaned = cleaned.removePrefix("```json").trim()
                } else if (cleaned.startsWith("```")) {
                    cleaned = cleaned.removePrefix("```").trim()
                }
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.removeSuffix("```").trim()
                }

                val startIdx = cleaned.indexOf('{')
                val endIdx = cleaned.lastIndexOf('}')
                if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
                    return null
                }
                cleaned = cleaned.substring(startIdx, endIdx + 1)

                val actionVal = extractJsonValue(cleaned, "action")
                    ?: extractJsonValue(cleaned, "recommendedAction")
                    ?: extractJsonValue(cleaned, "recommended_action")
                val severityVal = extractJsonValue(cleaned, "severity")
                val classificationVal = extractJsonValue(cleaned, "classification")
                    ?: extractJsonValue(cleaned, "eventClassification")
                val reasonVal = extractJsonValue(cleaned, "reason")
                    ?: extractJsonValue(cleaned, "explanation")

                if (actionVal == null && severityVal == null && classificationVal == null && reasonVal == null) {
                    return null
                }

                // 1. Classification
                val classStr = (classificationVal ?: "NO_INCIDENT").uppercase()
                val classification = when (classStr) {
                    "NO_INCIDENT", "NORMAL" -> EventClassification.NO_INCIDENT
                    "MINOR_EVENT", "MINOR", "MINOR_MOVEMENT", "MINOR_JERK" -> EventClassification.MINOR_EVENT
                    "UNUSUAL", "UNUSUAL_MOVEMENT" -> EventClassification.UNUSUAL_MOVEMENT
                    "INCONCLUSIVE", "UNCERTAIN" -> EventClassification.INCONCLUSIVE
                    "POSSIBLE", "POSSIBLE_INCIDENT", "INCIDENT" -> EventClassification.POSSIBLE_INCIDENT
                    "SERIOUS_INCIDENT", "CRITICAL_INCIDENT", "CONFIRMED" -> EventClassification.SERIOUS_INCIDENT
                    else -> EventClassification.INCONCLUSIVE
                }

                // 2. Severity
                val severityStr = severityVal?.uppercase() ?: "MODERATE"
                val severity = when (severityStr) {
                    "LOW" -> IncidentSeverity.LOW
                    "MEDIUM", "MODERATE" -> IncidentSeverity.MODERATE
                    "HIGH" -> IncidentSeverity.HIGH
                    "CRITICAL", "SEVERE" -> IncidentSeverity.CRITICAL
                    else -> IncidentSeverity.MODERATE
                }

                // 3. Recommended Action
                val actionStr = (actionVal ?: "PROMPT_USER").uppercase()
                val recommendedAction = when (actionStr) {
                    "SEND_SOS", "DISPATCH_SMS", "SOS" -> RecommendedAction.DISPATCH_SMS
                    "ASK_USER", "PROMPT_USER", "VERIFY" -> RecommendedAction.PROMPT_USER
                    "CANCEL", "CANCEL_ALERT" -> RecommendedAction.CANCEL_ALERT
                    "MONITOR", "LOG_ONLY", "LOG" -> RecommendedAction.LOG_ONLY
                    else -> RecommendedAction.PROMPT_USER
                }

                // 4. Explanation / Reason
                val explanation = reasonVal ?: "Multi-sensor evidence analyzed."
                val report = extractJsonValue(cleaned, "report") ?: explanation
                val confidenceStr = extractJsonValue(cleaned, "confidence")
                val confidence = confidenceStr?.toFloatOrNull() ?: 0.5f

                LlmReasoningResult(
                    severity = severity,
                    recommendedAction = recommendedAction,
                    explanation = explanation,
                    report = report,
                    classification = classification,
                    confidence = confidence,
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun extractJsonValue(json: String, key: String): String? {
            val pattern = Regex("\"$key\"\\s*:\\s*\"?([^\",}]*)\"?")
            val match = pattern.find(json)
            return match?.groupValues?.get(1)?.trim()
        }

        private fun escapeJson(value: String): String {
            return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        }
    }
}
