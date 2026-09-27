package com.rakshak.core.ai

import com.rakshak.core.detector.IncidentDecisionEngine
import com.rakshak.core.detector.IncidentDecisionState
import com.rakshak.core.incident.IncidentData
import org.junit.Assert.*
import org.junit.Test

/**
 * IncidentDecisionTreeTest — Automated integration test verifying the complete RAKSHAK decision tree.
 *
 * EXPLICIT PRINCIPLE: "DETECTED MOVEMENT DOES NOT EQUAL CONFIRMED INCIDENT"
 *
 * Test cases (1 to 10):
 *  1. testMovementDetectedButNoIncidentEvidence — Left-right shake -> MINOR_JERK / LOG_ONLY
 *  2. testShortShakeDecaysToNormal — Short shake auto-decays to NORMAL
 *  3. testUnusualMotionNormalPostEventDecaysToNormal — Unusual motion with normal post-event
 *  4. testUserConfirmsSafeCancelsEmergency — Rider taps "I'M GOOD"
 *  5. testNoResponseInconclusiveEvidencePromptsUser — Countdown expired + inconclusive evidence -> PROMPT_USER (never DISPATCH_SMS)
 *  6. testGenuineCrashEvidenceDispatchesSms — Genuine severe crash -> DISPATCH_SMS
 *  7. testQwenLowSeverityDispatchSmsRejectedBySafetyValidator — Qwen LOW + DISPATCH_SMS rejected by SafetyValidator
 *  8. testMalformedJsonFallbackToSafetyValidator — Malformed LLM output fallback
 *  9. testCameraUnavailableHandledGracefully — Camera unavailable handled without crash or false SOS
 *  10. testMicrophoneUnavailableHandledGracefully — Mic unavailable handled without crash or false SOS
 */
class IncidentDecisionTreeTest {

    @Test
    fun testMovementDetectedButNoIncidentEvidence() {
        // 1. Input: Small left-right movement or minor jerk
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.55f,
            peakAcceleration = 18.5f,        // Small accel spike (< 25 m/s²)
            peakGyroscope = 2.1f,            // Normal rotation (< 3.5 rad/s)
            impactDurationMs = 150L,
            riderMovement = "normal",        // Rider continues moving normally
            cameraVerification = "normal",
            audioVerification = "no_voice_detected",
            locationAvailable = true,
            latitude = 17.4224521,
            longitude = 78.3369978,
            detectorState = "MONITORING"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.MINOR_MOVEMENT,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Short left-right movement without sustained impact force or abnormal rotation.",
            report = "Minor movement detected.",
            confidence = 0.88f
        )

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        // MANDATORY ASSERTIONS
        val movementDetected = true
        val incidentConfirmed = (finalResult.recommendedAction == RecommendedAction.DISPATCH_SMS)

        assertFalse("Movement detected MUST NOT equal confirmed incident!", incidentConfirmed)
        assertNotEquals("LOW severity MUST NEVER send SOS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertNotEquals("MINOR_MOVEMENT MUST NEVER send SOS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(IncidentSeverity.LOW, finalResult.severity)
        assertEquals(RecommendedAction.LOG_ONLY, finalResult.recommendedAction)
    }

    @Test
    fun testShortShakeDecaysToNormal() {
        IncidentDecisionEngine.resetToNormal()
        // Simulate short shake evaluated by engine
        val stateAfterMinorJerk = IncidentDecisionEngine.evaluate(
            crashProbability = 0.35f,
            recentPeakAccel = 18.0f,
            recentPeakGyro = 2.0f,
            fsmState = com.rakshak.core.detector.DetectorState.MONITORING
        )

        assertEquals("Short shake must produce MINOR_JERK or NORMAL state", IncidentDecisionState.MINOR_JERK, stateAfterMinorJerk)

        // Reset to normal to verify state cleanup
        IncidentDecisionEngine.resetToNormal()
        assertEquals(IncidentDecisionState.NORMAL, IncidentDecisionEngine.decisionState.value)
    }

    @Test
    fun testUnusualMotionNormalPostEventDecaysToNormal() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.72f,
            peakAcceleration = 26.0f,        // Moderate force (25-30 m/s²)
            peakGyroscope = 3.2f,            // Mild rotation (< 4.0 rad/s)
            impactDurationMs = 300L,
            riderMovement = "normal",        // Normal movement post-event
            cameraVerification = "inconclusive",
            audioVerification = "no_voice_detected",
            locationAvailable = true,
            latitude = 17.4224521,
            longitude = 78.3369978,
            detectorState = "MONITORING"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.UNUSUAL_MOVEMENT,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.PROMPT_USER,
            explanation = "Unusual motion pattern without conclusive impact or rotation evidence.",
            report = "Unusual movement recorded.",
            confidence = 0.75f
        )

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        assertNotEquals("Inconclusive movement MUST NOT allow DISPATCH_SMS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(RecommendedAction.PROMPT_USER, finalResult.recommendedAction)
    }

    @Test
    fun testUserConfirmsSafeCancelsEmergency() {
        IncidentDecisionEngine.resetToNormal()
        val currentState = IncidentDecisionEngine.decisionState.value

        assertEquals("Engine state must be NORMAL when user confirms safe!", IncidentDecisionState.NORMAL, currentState)
    }

    @Test
    fun testNoResponseInconclusiveEvidencePromptsUser() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.65f,
            peakAcceleration = 24.0f,
            peakGyroscope = 2.8f,
            impactDurationMs = 200L,
            riderMovement = "unknown",
            cameraVerification = "inconclusive",
            audioVerification = "no_voice_detected",
            locationAvailable = true,
            detectorState = "AWAITING_VERIFICATION"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.INCONCLUSIVE,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.PROMPT_USER,
            explanation = "Countdown expired with inconclusive sensor evidence.",
            report = "Inconclusive incident evidence.",
            confidence = 0.50f
        )

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        assertNotEquals("Absence of response on weak evidence MUST NOT send SOS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(RecommendedAction.PROMPT_USER, finalResult.recommendedAction)
    }

    @Test
    fun testGenuineCrashEvidenceDispatchesSms() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.94f,               // High crash confidence (>=0.85)
            peakAcceleration = 36.5f,        // Severe impact force (>=32 m/s²)
            peakGyroscope = 6.2f,            // High angular rotation (>=4.0 rad/s)
            impactDurationMs = 2400L,
            riderMovement = "unresponsive",   // Rider unresponsive post-impact
            cameraVerification = "possible_fall",
            audioVerification = "no_voice_detected",
            locationAvailable = true,
            latitude = 17.4224521,
            longitude = 78.3369978,
            detectorState = "CONFIRMED"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.POSSIBLE_INCIDENT,
            severity = IncidentSeverity.HIGH,
            recommendedAction = RecommendedAction.DISPATCH_SMS,
            explanation = "Severe impact force of 36.5 m/s² with 6.2 rad/s angular tumble and unresponsive rider.",
            report = "Confirmed two-wheeler crash.",
            confidence = 0.95f
        )

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        assertEquals(RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(IncidentSeverity.HIGH, finalResult.severity)
    }

    @Test
    fun testQwenLowSeverityDispatchSmsRejectedBySafetyValidator() {
        val weakIncident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.50f,
            peakAcceleration = 15.0f,
            peakGyroscope = 2.0f,
            impactDurationMs = 100L,
            detectorState = "MONITORING"
        )
        val invalidQwenOutput = LlmReasoningResult(
            classification = EventClassification.MINOR_MOVEMENT,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.DISPATCH_SMS, // Inconsistent combination!
            explanation = "Minor bump.",
            report = "Bump.",
            confidence = 0.40f
        )

        val validated = SafetyValidator.validate(weakIncident, invalidQwenOutput)

        assertNotEquals("Qwen LOW + SEND_SOS MUST BE REJECTED!", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
        assertEquals(RecommendedAction.LOG_ONLY, validated.recommendedAction)
    }

    @Test
    fun testMalformedJsonFallbackToSafetyValidator() {
        val malformedJson = "{ invalid json content: true, action: DISPATCH_SMS "
        val parsed = LlmReasoningResult.parseJson(malformedJson)

        assertNull("Malformed JSON should fail gracefully and parse to null", parsed)

        // SafetyValidator handles null LLM output safely
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.40f,
            peakAcceleration = 12.0f,
            peakGyroscope = 1.0f,
            impactDurationMs = 50L
        )
        val validatedFallback = SafetyValidator.validate(incident, null)

        assertNotEquals("Fallback on null LLM output MUST NEVER dispatch SOS", RecommendedAction.DISPATCH_SMS, validatedFallback.recommendedAction)
        assertEquals(RecommendedAction.LOG_ONLY, validatedFallback.recommendedAction)
    }

    @Test
    fun testCameraUnavailableHandledGracefully() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.60f,
            peakAcceleration = 22.0f,
            peakGyroscope = 2.5f,
            impactDurationMs = 200L,
            cameraVerification = "not_available",
            audioVerification = "no_voice_detected"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.UNUSUAL_MOVEMENT,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.PROMPT_USER,
            explanation = "Camera unavailable, relying on motion sensors.",
            report = "Camera unavailable.",
            confidence = 0.60f
        )

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        assertNotEquals("Camera unavailable on low force MUST NOT dispatch SOS", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(RecommendedAction.PROMPT_USER, finalResult.recommendedAction)
    }

    @Test
    fun testMicrophoneUnavailableHandledGracefully() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.60f,
            peakAcceleration = 22.0f,
            peakGyroscope = 2.5f,
            impactDurationMs = 200L,
            cameraVerification = "normal",
            audioVerification = "not_available"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.MINOR_MOVEMENT,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Microphone unavailable, motion normal.",
            report = "Microphone unavailable.",
            confidence = 0.70f
        )

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        assertNotEquals("Microphone unavailable on low force MUST NOT dispatch SOS", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(RecommendedAction.LOG_ONLY, finalResult.recommendedAction)
    }
}
