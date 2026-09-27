package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * PromptBuilder — constructs LLM reasoning prompts from structured IncidentData.
 *
 * Uses the Qwen2.5 ChatML format (<|im_start|>/<|im_end|>).
 * System prompt instructs the LLM to act as the on-device reasoning brain and output ONLY valid JSON.
 *
 * CRITICAL: The prompt explicitly instructs the LLM to correlate severity with actual evidence strength.
 * Weak evidence (low acceleration, low confidence) MUST produce LOW/MODERATE severity — never HIGH.
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

SEVERITY RULES (YOU MUST FOLLOW THESE):
- LOW: peak_acceleration < 20 m/s² OR confidence < 0.5. Action: LOG_ONLY or CANCEL_ALERT.
- MODERATE: peak_acceleration 20-30 m/s² AND confidence 0.5-0.8. Action: PROMPT_USER or LOG_ONLY.
- HIGH: peak_acceleration >= 30 m/s² AND confidence >= 0.8 AND rider_movement is "limited". Action: PROMPT_USER or DISPATCH_SMS.
- CRITICAL: peak_acceleration >= 35 m/s² AND confidence >= 0.9 AND no voice detected AND rider unresponsive. Action: DISPATCH_SMS.

DO NOT assign HIGH or CRITICAL severity when the evidence is weak (low acceleration, low confidence, or normal rider movement).
A small phone movement or brief jerk with peak_acceleration < 25 m/s² is ALWAYS LOW severity.

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
