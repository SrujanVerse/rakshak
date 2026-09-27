package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * PromptBuilder — constructs LLM reasoning prompts from structured IncidentData.
 *
 * Uses the Qwen2.5 ChatML format (<|im_start|>/<|im_end|>).
 * System prompt instructs the LLM to act as the on-device reasoning brain and output ONLY valid JSON.
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an on-device safety reasoning brain for a two-wheeler incident detection system. Given structured incident data as JSON, analyze the combined evidence and return a single valid JSON object.

Rules:
- Output ONLY a single JSON object. Do not output markdown code blocks, explanation text, or preambles outside the JSON.
- The JSON object MUST contain these 4 fields:
  1. "severity": "LOW" | "MODERATE" | "HIGH" | "CRITICAL"
  2. "recommended_action": "DISPATCH_SMS" | "PROMPT_USER" | "LOG_ONLY" | "CANCEL_ALERT"
  3. "explanation": A concise 1-sentence rationale for your evaluation.
  4. "report": A concise 2-sentence factual incident report.
- Base your evaluation ONLY on the input data. Do NOT invent sensor readings, GPS coordinates, medical conditions, or events not in the input.
- Keep the entire JSON under 100 words."""

    /**
     * Build a complete ChatML prompt for the LLM from incident data.
     */
    fun buildPrompt(incident: IncidentData): String {
        return buildString {
            append("<|im_start|>system\n")
            append(SYSTEM_PROMPT)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(incident.toJsonString())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    /**
     * Build a simple prompt without ChatML formatting for debugging.
     */
    fun buildSimplePrompt(incident: IncidentData): String {
        return buildString {
            append("Analyze this incident data and return a JSON object with severity, recommended_action, explanation, and report:\n\n")
            append(incident.toJsonString())
            append("\n\nJSON:")
        }
    }
}
