package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * NoOpReportGenerator — template-based fallback report and reasoning generator.
 *
 * Used when the on-device LLM model file is absent or fails.
 * Produces deterministic `LlmReasoningResult` without ML inference.
 */
class NoOpReportGenerator : IncidentReportGenerator {

    override fun isReady(): Boolean = true

    override suspend fun generateReport(
        incident: IncidentData,
        timeoutMs: Long
    ): ReportResult {
        val startTime = System.currentTimeMillis()
        val report = buildTemplateReport(incident)
        val reasoning = buildDeterministicReasoning(incident, report)
        val latency = System.currentTimeMillis() - startTime

        return ReportResult(
            report = report,
            latencyMs = latency,
            tokenCount = 0,
            generatorType = GENERATOR_TYPE,
            reasoningResult = reasoning,
        )
    }

    companion object {
        const val GENERATOR_TYPE = "noop_template"

        fun buildDeterministicReasoning(incident: IncidentData, reportText: String? = null): LlmReasoningResult {
            val report = reportText ?: buildTemplateReport(incident)

            val severity = when {
                incident.confidence >= 0.9f || incident.peakAcceleration >= 30.0f -> IncidentSeverity.CRITICAL
                incident.confidence >= 0.7f -> IncidentSeverity.HIGH
                incident.confidence >= 0.5f -> IncidentSeverity.MODERATE
                else -> IncidentSeverity.LOW
            }

            val action = when (severity) {
                IncidentSeverity.CRITICAL, IncidentSeverity.HIGH -> RecommendedAction.DISPATCH_SMS
                IncidentSeverity.MODERATE -> RecommendedAction.PROMPT_USER
                IncidentSeverity.LOW -> RecommendedAction.LOG_ONLY
            }

            val explanation = "Deterministic rule-based reasoning evaluation (confidence: ${incident.confidence}, peak acceleration: ${incident.peakAcceleration} m/s²)."

            val rawReasoning = LlmReasoningResult(
                severity = severity,
                recommendedAction = action,
                explanation = explanation,
                report = report,
            )

            return SafetyValidator.validate(incident, rawReasoning)
        }

        fun buildTemplateReport(incident: IncidentData): String {
            return buildString {
                val severity = when {
                    incident.confidence >= 0.9f -> "severe"
                    incident.confidence >= 0.7f -> "moderate"
                    incident.confidence >= 0.5f -> "possible"
                    else -> "low-confidence"
                }
                append("A $severity two-wheeler incident was detected")
                append(" (confidence: ${formatPercent(incident.confidence)}).")

                append(" Peak acceleration reached ${formatFloat(incident.peakAcceleration)} m/s\u00B2")
                if (incident.peakGyroscope > 0f) {
                    append(" with gyroscope activity of ${formatFloat(incident.peakGyroscope)} rad/s")
                }
                append(" over ${incident.impactDurationMs}ms.")

                val movement = when (incident.riderMovement) {
                    "limited" -> "Limited rider movement was observed post-impact."
                    "normal" -> "Normal rider movement was observed post-impact."
                    else -> "Rider movement status is unavailable."
                }
                append(" $movement")

                val camera = when (incident.cameraVerification) {
                    "possible_fall" -> " Camera verification indicates a possible fall."
                    "normal" -> " Camera verification shows normal conditions."
                    else -> " Camera verification is unavailable."
                }
                append(camera)

                if (incident.locationAvailable && incident.latitude != null && incident.longitude != null) {
                    append(" Location: ${incident.latitude}, ${incident.longitude}.")
                } else {
                    append(" GPS location is unavailable.")
                }
            }
        }

        private fun formatFloat(value: Float): String = String.format("%.1f", value)
        private fun formatPercent(value: Float): String = String.format("%.0f%%", value * 100)
    }
}
