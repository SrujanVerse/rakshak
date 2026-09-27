package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LlmReasoningResultTest {

    @Test
    fun `parseJson correctly parses valid JSON`() {
        val json = """
        {
          "severity": "CRITICAL",
          "recommended_action": "DISPATCH_SMS",
          "explanation": "High acceleration spike and limited rider movement.",
          "report": "Severe crash detected."
        }
        """.trimIndent()

        val result = LlmReasoningResult.parseJson(json)
        assertNotNull(result)
        assertEquals(IncidentSeverity.CRITICAL, result!!.severity)
        assertEquals(RecommendedAction.DISPATCH_SMS, result.recommendedAction)
        assertEquals("High acceleration spike and limited rider movement.", result.explanation)
        assertEquals("Severe crash detected.", result.report)
    }

    @Test
    fun `parseJson correctly handles markdown code blocks`() {
        val json = """
        ```json
        {
          "severity": "HIGH",
          "recommended_action": "PROMPT_USER",
          "explanation": "Moderate impact force detected.",
          "report": "Possible incident detected."
        }
        ```
        """.trimIndent()

        val result = LlmReasoningResult.parseJson(json)
        assertNotNull(result)
        assertEquals(IncidentSeverity.HIGH, result!!.severity)
        assertEquals(RecommendedAction.PROMPT_USER, result.recommendedAction)
    }

    @Test
    fun `parseJson returns null for completely malformed JSON`() {
        val malformed = "This is not JSON at all, just plain text from LLM."
        val result = LlmReasoningResult.parseJson(malformed)
        assertNull(result)
    }

    @Test
    fun `parseJson handles camelCase field name recommendedAction`() {
        val json = """
        {
          "severity": "MODERATE",
          "recommendedAction": "LOG_ONLY",
          "explanation": "Low risk bump.",
          "report": "Bump detected."
        }
        """.trimIndent()

        val result = LlmReasoningResult.parseJson(json)
        assertNotNull(result)
        assertEquals(RecommendedAction.LOG_ONLY, result!!.recommendedAction)
    }

    @Test
    fun `parseJson defaults missing or invalid enum fields safely`() {
        val json = """
        {
          "severity": "UNKNOWN_SEVERITY_NAME",
          "explanation": "Sample explanation."
        }
        """.trimIndent()

        val result = LlmReasoningResult.parseJson(json)
        assertNotNull(result)
        assertEquals(IncidentSeverity.MODERATE, result!!.severity)
        assertEquals(RecommendedAction.PROMPT_USER, result.recommendedAction)
    }

    @Test
    fun `SafetyValidator prevents downgrading high-confidence confirmed crash`() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.94f,
            peakAcceleration = 12.0f,
            peakGyroscope = 10.0f,
            impactDurationMs = 200L,
            detectorState = "CONFIRMED",
        )
        val llmAttemptedDowngrade = LlmReasoningResult(
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.CANCEL_ALERT,
            explanation = "Rider might be fine.",
            report = "No action needed.",
        )

        val validated = SafetyValidator.validate(incident, llmAttemptedDowngrade)
        assertEquals(RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
        assertEquals(IncidentSeverity.HIGH, validated.severity)
    }

    @Test
    fun `SafetyValidator upgrades severe acceleration with high confidence to at least HIGH severity`() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.88f,
            peakAcceleration = 36.0f, // Severe impact force + high confidence
            peakGyroscope = 10.0f,
            impactDurationMs = 200L,
            detectorState = "MONITORING",
        )
        val llmResult = LlmReasoningResult(
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Sensor spike.",
            report = "Spike observed.",
        )

        val validated = SafetyValidator.validate(incident, llmResult)
        assertEquals(IncidentSeverity.HIGH, validated.severity)
    }

    @Test
    fun `SafetyValidator caps weak evidence severity to MODERATE and prevents DISPATCH_SMS`() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.65f, // Weak confidence
            peakAcceleration = 22.0f, // Moderate accel (e.g. phone shake)
            peakGyroscope = 3.0f,
            impactDurationMs = 100L,
            detectorState = "MONITORING",
        )
        val llmResultOverstating = LlmReasoningResult(
            severity = IncidentSeverity.HIGH,
            recommendedAction = RecommendedAction.DISPATCH_SMS,
            explanation = "Potential crash suspected.",
            report = "Crash reported.",
        )

        val validated = SafetyValidator.validate(incident, llmResultOverstating)
        assertEquals(IncidentSeverity.MODERATE, validated.severity)
        assertEquals(RecommendedAction.PROMPT_USER, validated.recommendedAction)
    }

    @Test
    fun `NoOpReportGenerator builds valid deterministic reasoning`() = runBlocking {
        val generator = NoOpReportGenerator()
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.94f,
            peakAcceleration = 8.7f,
            peakGyroscope = 14.5f,
            impactDurationMs = 180L,
            riderMovement = "limited",
            cameraVerification = "possible_fall",
        )
        val result = generator.generateReport(incident)
        assertTrue(result.isSuccess)
        assertNotNull(result.reasoningResult)
        assertEquals(IncidentSeverity.CRITICAL, result.reasoningResult!!.severity)
        assertEquals(RecommendedAction.DISPATCH_SMS, result.reasoningResult!!.recommendedAction)
    }
}
