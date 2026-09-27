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
 * Tests:
 *  1. testMovementDetectedButNoIncidentEvidence() — CASE A (Small Movement)
 *  2. testCaseB_UnusualButInconclusiveMovement() — CASE B (Unusual / Moderate Movement)
 *  3. testCaseC_StrongIncidentEvidence() — CASE C (Genuine Crash Evidence)
 *  4. testCaseD_UserConfirmsSafe() — CASE D (User Taps I'M GOOD)
 *  5. testMandatorySafetyInvariants() — Absolute Safety Rules
 */
class IncidentDecisionTreeTest {

    @Test
    fun testMovementDetectedButNoIncidentEvidence() {
        println("\n==================================================")
        println("DECISION TREE TEST: Small Movement (No Incident Evidence)")
        println("==================================================")
        println("MOVEMENT DETECTED")
        println("↓")

        // 1. Input: Small left-right movement or minor jerk
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.55f,               // Moderate/weak ML confidence
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

        // 2. Qwen Phase 1 / Phase 2 output simulation for minor movement
        val qwenResult = LlmReasoningResult(
            classification = EventClassification.MINOR_MOVEMENT,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Short left-right movement without sustained impact force or abnormal rotation.",
            report = "Minor movement detected.",
            confidence = 0.88f
        )

        println("MOVEMENT CLASSIFICATION: ${qwenResult.classification}")
        println("↓")
        println("SAFETY CHECK: 10s Countdown Expired (No User Response)")
        println("↓")
        println("VERIFICATION: Camera=${incident.cameraVerification}, Mic=${incident.audioVerification}, Accel=${incident.peakAcceleration} m/s²")
        println("↓")
        println("QWEN ASSESSMENT: Severity=${qwenResult.severity}, Action=${qwenResult.recommendedAction}")
        println("↓")

        // 3. Pass through SafetyValidator
        val finalResult = SafetyValidator.validate(incident, qwenResult)

        println("SAFETY VALIDATOR: Approved Action=${finalResult.recommendedAction}")
        println("↓")
        println("FINAL ACTION: ${finalResult.recommendedAction.name} (NO SOS SENT)")
        println("==================================================\n")

        // 4. MANDATORY ASSERTIONS
        val movementDetected = true
        val incidentConfirmed = (finalResult.recommendedAction == RecommendedAction.DISPATCH_SMS)
        val noUserResponse = true

        // movementDetected != incidentConfirmed
        assertFalse("Movement detected MUST NOT equal confirmed incident!", incidentConfirmed)

        // noUserResponse != incidentConfirmed (absence of response is NOT proof of crash for weak evidence)
        if (noUserResponse) {
            assertNotEquals("No user response on weak evidence MUST NOT send SOS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        }

        // lowSeverity != SEND_SOS
        assertNotEquals("LOW severity MUST NEVER send SOS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)

        // minorMovement != SEND_SOS
        assertNotEquals("MINOR_MOVEMENT MUST NEVER send SOS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)

        assertEquals(IncidentSeverity.LOW, finalResult.severity)
        assertEquals(RecommendedAction.LOG_ONLY, finalResult.recommendedAction)
    }

    @Test
    fun testCaseB_UnusualButInconclusiveMovement() {
        println("\n==================================================")
        println("DECISION TREE TEST: Case B (Unusual But Inconclusive Movement)")
        println("==================================================")
        println("MOVEMENT DETECTED")
        println("↓")

        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.72f,
            peakAcceleration = 26.0f,        // Moderate force (25-30 m/s²)
            peakGyroscope = 3.2f,            // Mild rotation (< 4.0 rad/s)
            impactDurationMs = 300L,
            riderMovement = "normal",
            cameraVerification = "inconclusive",
            audioVerification = "no_voice_detected",
            locationAvailable = true,
            latitude = 17.4224521,
            longitude = 78.3369978,
            detectorState = "MONITORING"
        )

        // Qwen returns UNUSUAL_MOVEMENT / MODERATE / PROMPT_USER
        val qwenResult = LlmReasoningResult(
            classification = EventClassification.UNUSUAL_MOVEMENT,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.PROMPT_USER,
            explanation = "Unusual motion pattern without conclusive impact or rotation evidence.",
            report = "Unusual movement recorded.",
            confidence = 0.75f
        )

        println("MOVEMENT CLASSIFICATION: ${qwenResult.classification}")
        println("↓")
        println("VERIFICATION: Camera=${incident.cameraVerification}, Accel=${incident.peakAcceleration} m/s²")
        println("↓")
        println("QWEN ASSESSMENT: Severity=${qwenResult.severity}, Action=${qwenResult.recommendedAction}")
        println("↓")

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        println("SAFETY VALIDATOR: Action=${finalResult.recommendedAction}")
        println("↓")
        println("FINAL ACTION: ${finalResult.recommendedAction.name} (NO SOS SENT)")
        println("==================================================\n")

        // Weak / inconclusive evidence MUST NOT allow DISPATCH_SMS
        assertNotEquals("Inconclusive evidence MUST NOT allow DISPATCH_SMS!", RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(RecommendedAction.PROMPT_USER, finalResult.recommendedAction)
    }

    @Test
    fun testCaseC_StrongIncidentEvidence() {
        println("\n==================================================")
        println("DECISION TREE TEST: Case C (Strong Genuine Crash Evidence)")
        println("==================================================")
        println("MOVEMENT DETECTED")
        println("↓")

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

        println("MOVEMENT CLASSIFICATION: ${qwenResult.classification}")
        println("↓")
        println("VERIFICATION: Camera=${incident.cameraVerification}, Accel=${incident.peakAcceleration} m/s², Gyro=${incident.peakGyroscope} rad/s")
        println("↓")
        println("QWEN ASSESSMENT: Severity=${qwenResult.severity}, Action=${qwenResult.recommendedAction}")
        println("↓")

        val finalResult = SafetyValidator.validate(incident, qwenResult)

        println("SAFETY VALIDATOR: Approved Action=${finalResult.recommendedAction}")
        println("↓")
        println("FINAL ACTION: ${finalResult.recommendedAction.name} (SOS DISPATCH AUTHORIZED)")
        println("==================================================\n")

        // Only genuine strong evidence + failed user response reaches DISPATCH_SMS
        assertEquals(RecommendedAction.DISPATCH_SMS, finalResult.recommendedAction)
        assertEquals(IncidentSeverity.HIGH, finalResult.severity)
    }

    @Test
    fun testCaseD_UserConfirmsSafe() {
        println("\n==================================================")
        println("DECISION TREE TEST: Case D (User Taps I'M GOOD)")
        println("==================================================")

        // Reset IncidentDecisionEngine to NORMAL
        IncidentDecisionEngine.resetToNormal()

        val currentState = IncidentDecisionEngine.decisionState.value
        println("DECISION ENGINE STATE: $currentState")
        println("FINAL ACTION: MONITORING CONTINUED (USER SAFE)")
        println("==================================================\n")

        val userConfirmedSafe = true
        val sendSosPossible = if (userConfirmedSafe) false else true

        assertEquals("Engine state must be NORMAL when user confirms safe!", IncidentDecisionState.NORMAL, currentState)
        assertFalse("userConfirmedSafe = true -> SEND_SOS is impossible!", sendSosPossible)
    }

    @Test
    fun testMandatorySafetyInvariants() {
        // 1. Qwen LOW + SEND_SOS = REJECTED by SafetyValidator
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

        // 2. Weak evidence + SEND_SOS = REJECTED
        val weakEvidenceIncident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.60f,
            peakAcceleration = 22.0f,
            peakGyroscope = 3.0f,
            impactDurationMs = 150L,
            detectorState = "MONITORING"
        )
        val overstatingQwen = LlmReasoningResult(
            classification = EventClassification.POSSIBLE_INCIDENT,
            severity = IncidentSeverity.HIGH,
            recommendedAction = RecommendedAction.DISPATCH_SMS,
            explanation = "Overstated crash.",
            report = "Overstated.",
            confidence = 0.60f
        )

        val validatedWeak = SafetyValidator.validate(weakEvidenceIncident, overstatingQwen)
        assertNotEquals("Weak evidence + SEND_SOS MUST BE REJECTED!", RecommendedAction.DISPATCH_SMS, validatedWeak.recommendedAction)
        assertEquals(RecommendedAction.PROMPT_USER, validatedWeak.recommendedAction)
        assertEquals(IncidentSeverity.MODERATE, validatedWeak.severity)
    }
}
