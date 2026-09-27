package com.rakshak.core.ai

import android.util.Log
import com.rakshak.core.ai.llm.LlmEngine
import com.rakshak.core.incident.IncidentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LocalLlmReportGenerator — generates incident reports and reasoning using an on-device LLM.
 *
 * Architecture:
 *  - Requests structured JSON reasoning from the LLM
 *  - Parses response into [LlmReasoningResult]
 *  - Enforces deterministic safety rules via [SafetyValidator]
 *  - On any failure or malformed JSON, falls back to [NoOpReportGenerator] reasoning
 */
class LocalLlmReportGenerator(
    private val engine: LlmEngine,
    private val modelPath: String,
    private val nThreads: Int = 4,
    private val contextSize: Int = 1024,
    private val maxTokens: Int = 160,
    private val temperature: Float = 0.1f,
) : IncidentReportGenerator {

    override fun isReady(): Boolean = engine.isLoaded

    override suspend fun generateReport(
        incident: IncidentData,
        timeoutMs: Long
    ): ReportResult {
        val startTime = System.currentTimeMillis()

        if (!engine.isLoaded) {
            Log.i(TAG, "[MODEL_LOAD_START] Attempting to load on-device LLM model from: $modelPath")
            val loaded = try {
                withContext(Dispatchers.IO) {
                    engine.loadModel(modelPath, nThreads, contextSize)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[MODEL_LOAD_FAILED] Model load thrown exception", e)
                false
            }
            if (!loaded) {
                val elapsed = System.currentTimeMillis() - startTime
                val fallbackReasoning = NoOpReportGenerator.buildDeterministicReasoning(incident)
                return ReportResult(
                    report = fallbackReasoning.report,
                    latencyMs = elapsed,
                    error = "Model failed to load from: $modelPath",
                    generatorType = GENERATOR_TYPE,
                    reasoningResult = fallbackReasoning,
                )
            }
            Log.i(TAG, "[MODEL_LOAD_SUCCESS] On-device LLM model loaded into memory")
        }

        val prompt = PromptBuilder.buildPrompt(incident)
        Log.i(TAG, "[LLM_INFERENCE_START] Sending ChatML prompt to on-device LLM")

        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.IO) {
                    engine.generate(
                        prompt = prompt,
                        maxTokens = maxTokens,
                        temperature = temperature,
                    )
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (result == null) {
                Log.w(TAG, "[LLM_INFERENCE_TIMEOUT] LLM reasoning timed out after ${elapsed}ms")
                val fallbackReasoning = NoOpReportGenerator.buildDeterministicReasoning(incident)
                ReportResult(
                    report = fallbackReasoning.report,
                    latencyMs = elapsed,
                    error = "Inference timed out after ${timeoutMs}ms",
                    generatorType = GENERATOR_TYPE,
                    reasoningResult = fallbackReasoning,
                )
            } else {
                val rawText = result.text.trim()
                Log.i(TAG, "[LLM_INFERENCE_COMPLETE] Completed in ${result.generationTimeMs}ms")
                Log.i(TAG, "[LLM_RAW_OUTPUT] Raw output: $rawText")

                val parsed = LlmReasoningResult.parseJson(rawText)

                val finalReasoning = if (parsed != null) {
                    Log.i(TAG, "[LLM_JSON_PARSE_SUCCESS] Successfully parsed JSON: severity=${parsed.severity}, action=${parsed.recommendedAction}")
                    val validated = SafetyValidator.validate(incident, parsed)
                    Log.i(TAG, "[LLM_SAFETY_VALIDATION_COMPLETE] Final action=${validated.recommendedAction}, severity=${validated.severity}")
                    validated
                } else {
                    Log.w(TAG, "[LLM_JSON_PARSE_FAILED] Failed to parse JSON, using deterministic fallback")
                    NoOpReportGenerator.buildDeterministicReasoning(
                        incident,
                        if (rawText.isNotBlank()) rawText else null
                    )
                }

                ReportResult(
                    report = finalReasoning.report,
                    latencyMs = elapsed,
                    tokenCount = result.tokenCount,
                    generatorType = GENERATOR_TYPE,
                    reasoningResult = finalReasoning,
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "LLM reasoning failed", e)
            val fallbackReasoning = NoOpReportGenerator.buildDeterministicReasoning(incident)
            ReportResult(
                report = fallbackReasoning.report,
                latencyMs = elapsed,
                error = "Inference error: ${e.message}",
                generatorType = GENERATOR_TYPE,
                reasoningResult = fallbackReasoning,
            )
        }
    }

    companion object {
        private const val TAG = "LocalLlmReportGen"
        const val GENERATOR_TYPE = "local_llm"
    }
}
