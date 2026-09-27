package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData

/**
 * PromptBuilder — constructs ChatML prompts for Qwen on-device LLM.
 *
 * Phase 1: Event Interpretation — evaluates raw sensor evidence episode.
 * Phase 2: Verification Analysis — evaluates multi-sensor verification evidence after countdown expiration.
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an on-device safety reasoning brain for a two-wheeler incident detection system. Given structured movement evidence as JSON, interpret the event and return a single valid JSON object.

Output Contract (MUST follow exactly):
{
  "classification": "NORMAL" | "MINOR_MOVEMENT" | "UNUSUAL_MOVEMENT" | "POSSIBLE_INCIDENT",
  "severity": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
  "action": "MONITOR" | "ASK_USER" | "VERIFY" | "SEND_SOS",
  "confidence": 0.0-1.0,
  "reason": "1-sentence evidence-based explanation"
}

Interpretation Rules:
1. NORMAL: Small tilt, ordinary handling. Severity: LOW. Action: MONITOR.
2. MINOR_MOVEMENT: Left-right oscillation, brief movement spike (peakAccel < 25 m/s²). Severity: LOW. Action: MONITOR.
3. UNUSUAL_MOVEMENT: Moderate force (25-32 m/s²) without tumble rotation. Severity: MEDIUM. Action: ASK_USER.
4. POSSIBLE_INCIDENT: Severe impact (peakAccel >= 32 m/s²) + angular rotation (peakGyro >= 4 rad/s) + high crash probability. Severity: HIGH/CRITICAL. Action: VERIFY or SEND_SOS.

CRITICAL: Left-right phone shaking or brief motion spikes MUST be classified as MINOR_MOVEMENT with severity LOW and action MONITOR.
Output ONLY valid JSON. No preambles, no markdown blocks."""

    /**
     * Build Phase 1 ChatML prompt for Qwen Event Interpreter.
     */
    fun buildEventInterpretationPrompt(
        peakAccel: Float,
        peakGyro: Float,
        durationMs: Long,
        pattern: String,
        postMovement: String,
        tfliteProb: Float
    ): String {
        val jsonInput = """
        {
          "eventDurationMs": $durationMs,
          "peakAcceleration": ${String.format("%.1f", peakAccel)},
          "peakGyroscope": ${String.format("%.1f", peakGyro)},
          "movementPattern": "$pattern",
          "postEventMovement": "$postMovement",
          "tfliteCrashProbability": ${String.format("%.2f", tfliteProb)}
        }
        """.trimIndent()

        return buildString {
            append("<|im_start|>system\n")
            append(SYSTEM_PROMPT)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(jsonInput)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    /**
     * Build Phase 2 ChatML prompt for Qwen Verification Analysis.
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
     * Simple prompt fallback.
     */
    fun buildSimplePrompt(incident: IncidentData): String {
        return buildString {
            append("Analyze this incident data and return a JSON object with classification, severity, action, confidence, and reason:\n\n")
            append(incident.toJsonString())
            append("\n\nJSON:")
        }
    }
}
