package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * IncidentReportGenerator — interface for producing human-readable reports and structured reasoning.
 *
 * Implementations:
 *  - [NoOpReportGenerator]: Template-based fallback (no ML required)
 *  - [LocalLlmReportGenerator]: On-device LLM reasoning engine via llama.cpp
 *
 * Architecture rule: This is an on-device reasoning and report generation layer.
 * It provides advisory guidance to the application layer.
 */
interface IncidentReportGenerator {

    /** Returns true if the generator is initialized and ready for inference. */
    fun isReady(): Boolean

    /**
     * Generate structured reasoning and a concise report from incident data.
     */
    suspend fun generateReport(
        incident: IncidentData,
        timeoutMs: Long = 10_000L
    ): ReportResult
}

/**
 * ReportResult — encapsulates the output of report and reasoning generation.
 */
data class ReportResult(
    /** The generated report text, or null if generation failed. */
    val report: String?,

    /** Wall-clock time taken for generation (milliseconds). */
    val latencyMs: Long,

    /** Number of output tokens generated (0 for template-based generators). */
    val tokenCount: Int = 0,

    /** Error message if generation failed, null on success. */
    val error: String? = null,

    /** Which generator produced this result: "noop", "local_llm", etc. */
    val generatorType: String = "unknown",

    /** Structured reasoning result (severity, recommendedAction, explanation, report). */
    val reasoningResult: LlmReasoningResult? = null,
) {
    /** True if the report was generated successfully. */
    val isSuccess: Boolean get() = report != null && error == null
}
