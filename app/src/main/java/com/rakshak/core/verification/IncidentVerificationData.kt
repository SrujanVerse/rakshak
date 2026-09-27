package com.rakshak.core.verification

/**
 * IncidentVerificationData — comprehensive structured payload containing all live sensor,
 * vision, audio, GPS, and rider response evidence gathered during verification mode.
 *
 * This object is passed directly to the local Qwen LLM for multi-modal reasoning.
 */
data class IncidentVerificationData(
    /** Movement classification from sensor window: "NORMAL", "MINOR_JERK", "UNUSUAL_MOVEMENT", "POSSIBLE_IMPACT", "POSSIBLE_INCIDENT" */
    val movementClassification: String,

    /** TFLite crash probability (0.0 - 1.0) */
    val tfliteEvidence: Float,

    /** Peak acceleration magnitude recorded during episode (m/s²) */
    val accelerationEvidence: Float,

    /** Peak angular rotation rate recorded during episode (rad/s) */
    val rotationEvidence: Float,

    /** Rider post-event movement status: "normal", "limited", "unresponsive", "unknown" */
    val postEventMovement: String,

    /** Front camera status: "AVAILABLE", "UNAVAILABLE", "PERMISSION_DENIED" */
    val frontCameraStatus: String,

    /** Front camera visual assessment: "PERSON_DETECTED", "UPRIGHT_POSTURE", "POSSIBLE_FALL_POSTURE", "NO_CLEAR_VISUAL_EVIDENCE", "CAMERA_UNAVAILABLE" */
    val frontCameraAssessment: String,

    /** Rear camera status: "AVAILABLE", "UNAVAILABLE", "PERMISSION_DENIED" */
    val rearCameraStatus: String,

    /** Rear camera visual assessment: "ENVIRONMENT_NORMAL", "NO_CLEAR_VISUAL_EVIDENCE", "CAMERA_UNAVAILABLE" */
    val rearCameraAssessment: String,

    /** Microphone status: "AVAILABLE", "UNAVAILABLE", "PERMISSION_DENIED" */
    val voiceStatus: String,

    /** Microphone audio assessment: "VOICE_ACTIVITY_DETECTED", "NO_VOICE_ACTIVITY", "AUDIO_UNAVAILABLE" */
    val voiceAssessment: String,

    /** GPS availability flag */
    val gpsAvailable: Boolean,

    /** GPS Latitude (null if unavailable) */
    val latitude: Double? = null,

    /** GPS Longitude (null if unavailable) */
    val longitude: Double? = null,

    /** Whether rider pressed I'M OKAY */
    val userResponded: Boolean = false,

    /** Timestamp of verification data construction */
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Format payload for ChatML local LLM prompt.
     */
    fun toPromptPayload(): String {
        return buildString {
            append("{\n")
            append("  \"movement_classification\": \"$movementClassification\",\n")
            append("  \"tflite_crash_probability\": ${String.format("%.2f", tfliteEvidence)},\n")
            append("  \"peak_acceleration_mps2\": ${String.format("%.1f", accelerationEvidence)},\n")
            append("  \"peak_gyroscope_rads\": ${String.format("%.1f", rotationEvidence)},\n")
            append("  \"post_event_movement\": \"$postEventMovement\",\n")
            append("  \"front_camera_status\": \"$frontCameraStatus\",\n")
            append("  \"front_camera_assessment\": \"$frontCameraAssessment\",\n")
            append("  \"rear_camera_status\": \"$rearCameraStatus\",\n")
            append("  \"rear_camera_assessment\": \"$rearCameraAssessment\",\n")
            append("  \"voice_status\": \"$voiceStatus\",\n")
            append("  \"voice_assessment\": \"$voiceAssessment\",\n")
            append("  \"gps_available\": $gpsAvailable,\n")
            if (latitude != null && longitude != null) {
                append("  \"latitude\": $latitude,\n")
                append("  \"longitude\": $longitude,\n")
            }
            append("  \"user_responded\": $userResponded\n")
            append("}")
        }
    }
}
