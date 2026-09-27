package com.rakshak.core.incident

/**
 * IncidentData — structured representation of a detected incident.
 *
 * This is the single source of truth shared between:
 *  - CrashDetector (producer)
 *  - IncidentReportGenerator / LLM (consumer — generates human-readable report)
 *  - IncidentLogger (consumer — writes to disk)
 *  - AlertSender (consumer — future: attach report to SMS)
 *
 * Architecture rule: The LLM must ONLY read this data to produce a report.
 * It must NOT decide whether a crash occurred — that decision is already made
 * before IncidentData is constructed.
 *
 * Fields with null/default values indicate information that was unavailable.
 * The LLM prompt builder must explicitly state "unavailable" for missing fields.
 */
data class IncidentData(
    /** Event type: "possible_crash", "voice_sos", "test_incident" */
    val eventType: String,

    /** Crash confidence from the classifier (0.0–1.0).
     *  For the deterministic FSM, this is 1.0 when CONFIRMED. */
    val confidence: Float,

    /** Peak acceleration magnitude during detection window (m/s²) */
    val peakAcceleration: Float,

    /** Peak gyroscope magnitude during detection window (rad/s) */
    val peakGyroscope: Float,

    /** Duration from initial impact to resting confirmation (ms) */
    val impactDurationMs: Long,

    /** Post-impact rider movement assessment: "limited", "normal", "unknown" */
    val riderMovement: String = "unknown",

    /** Camera verification result: "not_available", "possible_fall", "normal" */
    val cameraVerification: String = "not_available",

    /** Audio verification result: "not_available", "voice_detected", "no_voice_detected" */
    val audioVerification: String = "not_available",

    /** Whether GPS location was available at detection time */
    val locationAvailable: Boolean = false,

    /** GPS latitude (null if unavailable) */
    val latitude: Double? = null,

    /** GPS longitude (null if unavailable) */
    val longitude: Double? = null,

    /** Incident timestamp in milliseconds (System.currentTimeMillis()) */
    val timestampMs: Long = System.currentTimeMillis(),

    /** The DetectorState name at time of incident creation */
    val detectorState: String = "CONFIRMED",

    /** Human-readable report from LLM (null = not yet generated or LLM unavailable) */
    val llmReport: String? = null,
) {
    /** JSON representation for logging and prompt construction */
    fun toJsonString(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"event\":\"$eventType\",")
        sb.append("\"confidence\":$confidence,")
        sb.append("\"peak_acceleration\":$peakAcceleration,")
        sb.append("\"peak_gyro\":$peakGyroscope,")
        sb.append("\"impact_duration_ms\":$impactDurationMs,")
        sb.append("\"rider_movement\":\"$riderMovement\",")
        sb.append("\"camera_verification\":\"$cameraVerification\",")
        sb.append("\"audio_verification\":\"$audioVerification\",")
        sb.append("\"location_available\":$locationAvailable")
        if (latitude != null && longitude != null) {
            sb.append(",\"latitude\":$latitude,\"longitude\":$longitude")
        }
        sb.append("}")
        return sb.toString()
    }

    /** Create a copy with the LLM report attached */
    fun withReport(report: String): IncidentData = copy(llmReport = report)
}
