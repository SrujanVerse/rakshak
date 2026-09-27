package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData
import com.rakshak.core.verification.IncidentVerificationData

/**
 * PromptBuilder — constructs ChatML prompts for Qwen on-device LLM.
 *
 * Enforces strict JSON contracts and evidence-only reasoning rules.
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an on-device safety reasoning brain for a two-wheeler incident detection system. Given structured verification evidence as JSON, interpret the event and return a single valid JSON object.

Output Contract (MUST follow exactly):
{
  "classification": "NO_INCIDENT" | "MINOR_EVENT" | "INCONCLUSIVE" | "POSSIBLE_INCIDENT" | "SERIOUS_INCIDENT",
  "severity": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
  "action": "MONITOR" | "VERIFY" | "SEND_SOS",
  "recommendedAction": "MONITOR" | "VERIFY" | "SEND_SOS",
  "confidence": 0.0-1.0,
  "reason": "1-sentence evidence-based explanation"
}

Interpretation Rules:
1. NO_INCIDENT / MINOR_EVENT: Small tilt, ordinary phone handling, left-right movement. Severity: LOW. Action: MONITOR.
2. INCONCLUSIVE: Verification evidence is unclear or missing. Severity: MEDIUM / LOW. Action: MONITOR or VERIFY (NEVER SEND_SOS).
3. POSSIBLE_INCIDENT / SERIOUS_INCIDENT: Severe impact force (peakAccel >= 35 m/s²) + high rotation (peakGyro >= 5 rad/s) + unresponsive rider. Severity: HIGH or CRITICAL. Action: SEND_SOS.

STRICT EVIDENCE RULES:
- Use ONLY supplied evidence. NEVER invent visual observations or claim rider fall when status is NO_CLEAR_VISUAL_EVIDENCE or CAMERA_UNAVAILABLE.
- NEVER claim NO_VOICE_ACTIVITY or INCONCLUSIVE audio implies rider is unconscious.
- Output ONLY valid JSON. No preambles, no markdown blocks."""

    /**
     * Build ChatML prompt for Qwen Verification Analysis on [IncidentVerificationData].
     */
    fun buildVerificationPrompt(verificationData: IncidentVerificationData): String {
        return buildString {
            append("<|im_start|>system\n")
            append(SYSTEM_PROMPT)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(verificationData.toPromptPayload())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

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
            append("Analyze this incident data and return a JSON object with classification, severity, recommendedAction, confidence, and reason:\n\n")
            append(incident.toJsonString())
            append("\n\nJSON:")
        }
    }
}
