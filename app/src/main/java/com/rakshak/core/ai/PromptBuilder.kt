package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData
import com.rakshak.core.verification.IncidentVerificationData

/**
 * PromptBuilder — constructs ChatML prompts for Qwen on-device LLM.
 *
 * Enforces strict ChatML JSON contracts and evidence-only reasoning rules.
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an on-device safety reasoning brain for a two-wheeler incident detection system. Given structured verification evidence as JSON, interpret the event and return a single valid JSON object.

Output Contract (MUST follow exactly):
{
  "classification": "NO_INCIDENT" | "MINOR_EVENT" | "INCONCLUSIVE" | "POSSIBLE_INCIDENT" | "SERIOUS_INCIDENT",
  "severity": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
  "action": "MONITOR" | "VERIFY" | "SEND_SOS",
  "recommendedAction": "MONITOR" | "VERIFY" | "SEND_SOS",
  "confidence": 0.0-0.95,
  "reason": "1-sentence evidence-based explanation"
}

STRICT EVIDENCE SEMANTICS & REASONING RULES:
1. Never invent camera observations or claim a fall when visual assessment is NO_CLEAR_VISUAL_EVIDENCE or CAMERA_UNAVAILABLE.
2. Never invent voice observations.
3. Never interpret VOICE_ACTIVITY_DETECTED as evidence of a crash, injury, or safety.
4. Never interpret NO_VOICE_ACTIVITY as evidence of unconsciousness or injury.
5. Never interpret GPS availability as evidence of an incident.
6. Never interpret high acceleration alone as a confirmed crash.
7. Never interpret high rotation alone as a confirmed crash.
8. NO_CLEAR_VISUAL_EVIDENCE means visual evidence is insufficient to confirm an incident.
9. If evidence is weak or conflicting, prefer INCONCLUSIVE classification with MONITOR action.
10. If evidence is missing, report it as missing.
11. Confidence must NOT exceed 0.95 (range: 0.00 to 0.95).
12. Explanation MUST be directly supported by provided evidence.
13. Rider not responding to countdown is NOT crash confirmation.
14. Output ONLY valid JSON. No preambles, no markdown blocks."""

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
