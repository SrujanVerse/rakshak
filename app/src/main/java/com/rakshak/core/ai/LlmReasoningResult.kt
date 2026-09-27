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
    CANCEL_ALERT
}

/**
 * Event classifications produced by Qwen Event Interpreter.
 */
enum class EventClassification {
    NORMAL,
    MINOR_MOVEMENT,
    UNUSUAL_MOVEMENT,
    POSSIBLE_INCIDENT
}

/**
 * LlmReasoningResult — structured output from the on-device LLM reasoning brain.
 *
 * Encapsulates:
 *  - [classification]: Movement event level
 *  - [severity]: Risk severity level
 *  - [recommendedAction]: Suggested action for the safety layer
 *  - [explanation]: Evidence-based rationale
 *  - [report]: Natural-language summary
 *  - [confidence]: Assessment confidence (0.0-1.0)
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
         * Supports both Qwen output contract keys ("classification", "severity", "action", "reason")
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

                // 1. Classification
                val classStr = (extractJsonValue(cleaned, "classification")
                    ?: extractJsonValue(cleaned, "eventClassification")
                    ?: "NORMAL").uppercase()
                val classification = when (classStr) {
                    "MINOR", "MINOR_MOVEMENT" -> EventClassification.MINOR_MOVEMENT
                    "UNUSUAL", "UNUSUAL_MOVEMENT" -> EventClassification.UNUSUAL_MOVEMENT
                    "POSSIBLE", "POSSIBLE_INCIDENT", "INCIDENT" -> EventClassification.POSSIBLE_INCIDENT
                    else -> EventClassification.NORMAL
                }

                // 2. Severity
                val severityStr = extractJsonValue(cleaned, "severity")?.uppercase() ?: "MODERATE"
                val severity = when (severityStr) {
                    "LOW" -> IncidentSeverity.LOW
                    "MEDIUM", "MODERATE" -> IncidentSeverity.MODERATE
                    "HIGH" -> IncidentSeverity.HIGH
                    "CRITICAL", "SEVERE" -> IncidentSeverity.CRITICAL
                    else -> IncidentSeverity.MODERATE
                }

                // 3. Recommended Action
                val actionStr = (extractJsonValue(cleaned, "action")
                    ?: extractJsonValue(cleaned, "recommended_action")
                    ?: extractJsonValue(cleaned, "recommendedAction")
                    ?: "PROMPT_USER").uppercase()

                val recommendedAction = when (actionStr) {
                    "SEND_SOS", "DISPATCH_SMS", "SOS" -> RecommendedAction.DISPATCH_SMS
                    "ASK_USER", "PROMPT_USER", "VERIFY" -> RecommendedAction.PROMPT_USER
                    "CANCEL", "CANCEL_ALERT" -> RecommendedAction.CANCEL_ALERT
                    "LOG_ONLY", "LOG" -> RecommendedAction.LOG_ONLY
                    else -> RecommendedAction.PROMPT_USER
                }

                // 4. Explanation / Reason
                val explanation = extractJsonValue(cleaned, "reason")
                    ?: extractJsonValue(cleaned, "explanation")
                    ?: extractJsonValue(cleaned, "assessment")
                    ?: "Multi-sensor evidence analyzed."

                val report = extractJsonValue(cleaned, "report")
                    ?: explanation

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
