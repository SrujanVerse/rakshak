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
 * LlmReasoningResult — structured output from the on-device LLM reasoning brain.
 *
 * Encapsulates:
 *  - [severity]: Evaluated risk severity
 *  - [recommendedAction]: Suggested action for the safety layer
 *  - [explanation]: Rationale for the decision
 *  - [report]: Concise natural-language summary
 */
data class LlmReasoningResult(
    val severity: IncidentSeverity,
    val recommendedAction: RecommendedAction,
    val explanation: String,
    val report: String,
) {
    fun toJsonString(): String {
        return buildString {
            append("{")
            append("\"severity\":\"${severity.name}\",")
            append("\"recommended_action\":\"${recommendedAction.name}\",")
            append("\"explanation\":\"${escapeJson(explanation)}\",")
            append("\"report\":\"${escapeJson(report)}\"")
            append("}")
        }
    }

    companion object {
        /**
         * Safely parse LLM text output into [LlmReasoningResult].
         * Uses pure Kotlin extraction to ensure 100% JVM unit test compatibility
         * without relying on Android framework stubs.
         *
         * Handles raw JSON, markdown code-block wrapped JSON (```json ... ```),
         * missing fields, and invalid enum values.
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

                val severityStr = extractJsonValue(cleaned, "severity")?.uppercase() ?: "MODERATE"
                val severity = try {
                    IncidentSeverity.valueOf(severityStr)
                } catch (e: Exception) {
                    IncidentSeverity.MODERATE
                }

                val actionStr = (extractJsonValue(cleaned, "recommended_action")
                    ?: extractJsonValue(cleaned, "recommendedAction")
                    ?: "PROMPT_USER").uppercase()

                val recommendedAction = try {
                    RecommendedAction.valueOf(actionStr)
                } catch (e: Exception) {
                    RecommendedAction.PROMPT_USER
                }

                val explanation = extractJsonValue(cleaned, "explanation")
                    ?: "Reasoning generated based on multi-sensor evidence."
                val report = extractJsonValue(cleaned, "report")
                    ?: "Incident reported based on available data."

                LlmReasoningResult(
                    severity = severity,
                    recommendedAction = recommendedAction,
                    explanation = explanation,
                    report = report,
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun extractJsonValue(json: String, key: String): String? {
            val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            val match = pattern.find(json)
            return match?.groupValues?.get(1)
        }

        private fun escapeJson(value: String): String {
            return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        }
    }
}
