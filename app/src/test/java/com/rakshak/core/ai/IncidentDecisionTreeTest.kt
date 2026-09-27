package com.rakshak.core.ai

import com.rakshak.core.detector.IncidentDecisionEngine
import com.rakshak.core.detector.IncidentDecisionState
import com.rakshak.core.incident.IncidentData
import com.rakshak.core.verification.IncidentVerificationData
import org.junit.Assert.*
import org.junit.Test

/**
 * IncidentDecisionTreeTest — Automated integration test verifying the complete RAKSHAK decision tree.
 *
 * MANDATORY REQUIREMENT 11 TEST SCENARIOS (A THROUGH I):
 *  A. testSmallLeftRightMovement — Small left-right movement -> MINOR_EVENT / NO_INCIDENT -> No SOS
 *  B. testShortShake — Short shake -> MINOR_JERK -> No SOS
 *  C. testCountdownExpiresWithWeakEvidence — Countdown expires with weak evidence -> VERIFYING -> NO_INCIDENT -> No SOS
 *  D. testUserPressesImOkay — Rider presses I'M OKAY -> Immediate cancellation -> No SOS
 *  E. testCameraUnavailable — Camera unavailable -> CAMERA_UNAVAILABLE -> No fabricated fall -> No SOS
 *  F. testAudioUnavailable — Audio unavailable -> AUDIO_UNAVAILABLE -> No assumption of unconsciousness -> No SOS
 *  G. testQwenLowSeveritySendSosRejected — Qwen returns LOW + SEND_SOS -> SafetyValidator rejects
 *  H. testMalformedJsonFallback — Qwen returns malformed JSON -> Safe fallback -> No SOS
 *  I. testStrongIncidentEvidenceAuthorizesSos — Strong genuine crash evidence -> Verification -> Qwen -> SafetyValidator authorizes SOS
 */
class IncidentDecisionTreeTest {

    @Test
    fun testSmallLeftRightMovement() {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.45f,
            peakAcceleration = 18.0f,
            peakGyroscope = 2.0f,
            impactDurationMs = 120L,
            riderMovement = "normal",
            cameraVerification = "normal",
            audioVerification = "no_voice_activity",
            locationAvailable = true,
            detectorState = "MONITORING"
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.MINOR_EVENT,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Small left-right phone movement.",
            report = "Minor handling motion.",
            confidence = 0.90f
        )

        val validated = SafetyValidator.validate(incident, qwenResult)

        assertNotEquals("Small movement MUST NEVER send SOS!", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
        assertEquals(IncidentSeverity.LOW, validated.severity)
        assertEquals(RecommendedAction.LOG_ONLY, validated.recommendedAction)
    }

    @Test
    fun testShortShake() {
        IncidentDecisionEngine.resetToNormal()
        val stateAfterMinorJerk = IncidentDecisionEngine.evaluate(
            crashProbability = 0.30f,
            recentPeakAccel = 18.5f,
            recentPeakGyro = 1.8f,
            fsmState = com.rakshak.core.detector.DetectorState.MONITORING
        )

        assertEquals("Short shake must produce MINOR_JERK", IncidentDecisionState.MINOR_JERK, stateAfterMinorJerk)

        IncidentDecisionEngine.resetToNormal()
        assertEquals(IncidentDecisionState.NORMAL, IncidentDecisionEngine.decisionState.value)
    }

    @Test
    fun testCountdownExpiresWithWeakEvidence() {
        val verificationData = IncidentVerificationData(
            movementClassification = "POSSIBLE_IMPACT",
            tfliteEvidence = 0.60f,
            accelerationEvidence = 24.0f,
            rotationEvidence = 2.5f,
            postEventMovement = "normal",
            frontCameraStatus = "AVAILABLE",
            frontCameraAssessment = "NO_CLEAR_VISUAL_EVIDENCE",
            rearCameraStatus = "AVAILABLE",
            rearCameraAssessment = "ENVIRONMENT_NORMAL",
            voiceStatus = "AVAILABLE",
            voiceAssessment = "NO_VOICE_ACTIVITY",
            gpsAvailable = true,
            latitude = 17.4224521,
            longitude = 78.3369978,
            userResponded = false
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.INCONCLUSIVE,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Weak evidence after countdown expiration.",
            report = "Inconclusive motion pattern.",
            confidence = 0.60f
        )

        val validated = SafetyValidator.validateVerification(verificationData, qwenResult)

        assertNotEquals("Countdown expiration with weak evidence MUST NOT send SOS!", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
        assertNotEquals("Countdown expiration with weak evidence MUST NOT send SOS!", RecommendedAction.SEND_SOS, validated.recommendedAction)
    }

    @Test
    fun testUserPressesImOkay() {
        val verificationData = IncidentVerificationData(
            movementClassification = "POSSIBLE_INCIDENT",
            tfliteEvidence = 0.80f,
            accelerationEvidence = 30.0f,
            rotationEvidence = 4.0f,
            postEventMovement = "normal",
            frontCameraStatus = "AVAILABLE",
            frontCameraAssessment = "NO_CLEAR_VISUAL_EVIDENCE",
            rearCameraStatus = "AVAILABLE",
            rearCameraAssessment = "ENVIRONMENT_NORMAL",
            voiceStatus = "AVAILABLE",
            voiceAssessment = "NO_VOICE_ACTIVITY",
            gpsAvailable = true,
            userResponded = true // Rider pressed I'M OKAY
        )

        val validated = SafetyValidator.validateVerification(verificationData, null)

        assertEquals("Tapping I'M OKAY MUST cancel alert!", RecommendedAction.CANCEL_ALERT, validated.recommendedAction)
        assertEquals("Tapping I'M OKAY MUST set NO_INCIDENT!", EventClassification.NO_INCIDENT, validated.classification)
    }

    @Test
    fun testCameraUnavailable() {
        val verificationData = IncidentVerificationData(
            movementClassification = "POSSIBLE_IMPACT",
            tfliteEvidence = 0.65f,
            accelerationEvidence = 22.0f,
            rotationEvidence = 3.0f,
            postEventMovement = "unknown",
            frontCameraStatus = "UNAVAILABLE",
            frontCameraAssessment = "CAMERA_UNAVAILABLE",
            rearCameraStatus = "UNAVAILABLE",
            rearCameraAssessment = "CAMERA_UNAVAILABLE",
            voiceStatus = "AVAILABLE",
            voiceAssessment = "NO_VOICE_ACTIVITY",
            gpsAvailable = true,
            userResponded = false
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.INCONCLUSIVE,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Camera unavailable, motion inconclusive.",
            report = "Camera unavailable.",
            confidence = 0.50f
        )

        val validated = SafetyValidator.validateVerification(verificationData, qwenResult)

        assertNotEquals("Camera unavailable MUST NOT fabricate crash or send SOS!", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
    }

    @Test
    fun testAudioUnavailable() {
        val verificationData = IncidentVerificationData(
            movementClassification = "POSSIBLE_IMPACT",
            tfliteEvidence = 0.65f,
            accelerationEvidence = 22.0f,
            rotationEvidence = 3.0f,
            postEventMovement = "unknown",
            frontCameraStatus = "AVAILABLE",
            frontCameraAssessment = "NO_CLEAR_VISUAL_EVIDENCE",
            rearCameraStatus = "AVAILABLE",
            rearCameraAssessment = "ENVIRONMENT_NORMAL",
            voiceStatus = "UNAVAILABLE",
            voiceAssessment = "AUDIO_UNAVAILABLE",
            gpsAvailable = true,
            userResponded = false
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.INCONCLUSIVE,
            severity = IncidentSeverity.MODERATE,
            recommendedAction = RecommendedAction.LOG_ONLY,
            explanation = "Audio unavailable, no assumption of unconsciousness.",
            report = "Audio unavailable.",
            confidence = 0.50f
        )

        val validated = SafetyValidator.validateVerification(verificationData, qwenResult)

        assertNotEquals("Audio unavailable MUST NOT assume unconsciousness or send SOS!", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
    }

    @Test
    fun testQwenLowSeveritySendSosRejected() {
        val verificationData = IncidentVerificationData(
            movementClassification = "MINOR_JERK",
            tfliteEvidence = 0.40f,
            accelerationEvidence = 15.0f,
            rotationEvidence = 1.5f,
            postEventMovement = "normal",
            frontCameraStatus = "AVAILABLE",
            frontCameraAssessment = "NO_CLEAR_VISUAL_EVIDENCE",
            rearCameraStatus = "AVAILABLE",
            rearCameraAssessment = "ENVIRONMENT_NORMAL",
            voiceStatus = "AVAILABLE",
            voiceAssessment = "NO_VOICE_ACTIVITY",
            gpsAvailable = true
        )

        val invalidQwenOutput = LlmReasoningResult(
            classification = EventClassification.MINOR_EVENT,
            severity = IncidentSeverity.LOW,
            recommendedAction = RecommendedAction.DISPATCH_SMS, // Invalid combination!
            explanation = "Minor bump.",
            report = "Bump.",
            confidence = 0.40f
        )

        val validated = SafetyValidator.validateVerification(verificationData, invalidQwenOutput)

        assertNotEquals("Qwen LOW + SEND_SOS MUST BE REJECTED!", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
        assertNotEquals("Qwen LOW + SEND_SOS MUST BE REJECTED!", RecommendedAction.SEND_SOS, validated.recommendedAction)
        assertEquals(RecommendedAction.LOG_ONLY, validated.recommendedAction)
    }

    @Test
    fun testMalformedJsonFallback() {
        val malformedJson = "This is completely invalid non-JSON output from LLM model."
        val parsed = LlmReasoningResult.parseJson(malformedJson)

        assertNull("Malformed JSON must fail gracefully", parsed)

        val verificationData = IncidentVerificationData(
            movementClassification = "POSSIBLE_IMPACT",
            tfliteEvidence = 0.50f,
            accelerationEvidence = 20.0f,
            rotationEvidence = 2.0f,
            postEventMovement = "normal",
            frontCameraStatus = "AVAILABLE",
            frontCameraAssessment = "NO_CLEAR_VISUAL_EVIDENCE",
            rearCameraStatus = "AVAILABLE",
            rearCameraAssessment = "ENVIRONMENT_NORMAL",
            voiceStatus = "AVAILABLE",
            voiceAssessment = "NO_VOICE_ACTIVITY",
            gpsAvailable = true
        )

        val validatedFallback = SafetyValidator.validateVerification(verificationData, null)

        assertNotEquals("Fallback on null LLM output MUST NEVER dispatch SOS", RecommendedAction.DISPATCH_SMS, validatedFallback.recommendedAction)
        assertEquals(RecommendedAction.LOG_ONLY, validatedFallback.recommendedAction)
    }

    @Test
    fun testStrongIncidentEvidenceAuthorizesSos() {
        val verificationData = IncidentVerificationData(
            movementClassification = "CONFIRMED_INCIDENT",
            tfliteEvidence = 0.95f,
            accelerationEvidence = 37.0f,
            rotationEvidence = 6.5f,
            postEventMovement = "unresponsive",
            frontCameraStatus = "AVAILABLE",
            frontCameraAssessment = "PERSON_DETECTED",
            rearCameraStatus = "AVAILABLE",
            rearCameraAssessment = "ENVIRONMENT_NORMAL",
            voiceStatus = "AVAILABLE",
            voiceAssessment = "NO_VOICE_ACTIVITY",
            gpsAvailable = true,
            latitude = 17.4224521,
            longitude = 78.3369978,
            userResponded = false
        )

        val qwenResult = LlmReasoningResult(
            classification = EventClassification.SERIOUS_INCIDENT,
            severity = IncidentSeverity.HIGH,
            recommendedAction = RecommendedAction.DISPATCH_SMS,
            explanation = "Severe physical impact of 37 m/s² with 6.5 rad/s tumble rotation and unresponsive rider.",
            report = "Confirmed serious two-wheeler crash.",
            confidence = 0.95f
        )

        val validated = SafetyValidator.validateVerification(verificationData, qwenResult)

        assertEquals("Strong genuine crash evidence authorizes DISPATCH_SMS", RecommendedAction.DISPATCH_SMS, validated.recommendedAction)
        assertEquals(IncidentSeverity.HIGH, validated.severity)
    }
}
