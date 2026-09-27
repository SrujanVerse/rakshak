package com.rakshak.core.ai.testing

import com.rakshak.core.incident.IncidentData

/**
 * FakeIncidentDataGenerator — produces realistic fake IncidentData for testing.
 *
 * Allows testing the AI report pipeline without triggering real crashes.
 * Provides several predefined scenarios covering different severity levels
 * and data completeness states.
 */
object FakeIncidentDataGenerator {

    /**
     * The primary test scenario as specified in requirements:
     * High-confidence crash with limited rider movement and camera verification.
     */
    fun highConfidenceCrash(): IncidentData = IncidentData(
        eventType = "possible_crash",
        confidence = 0.94f,
        peakAcceleration = 8.7f,
        peakGyroscope = 14.5f,
        impactDurationMs = 180L,
        riderMovement = "limited",
        cameraVerification = "possible_fall",
        locationAvailable = true,
        latitude = 12.9716,
        longitude = 77.5946,
        timestampMs = System.currentTimeMillis(),
        detectorState = "CONFIRMED",
    )

    /** Low-confidence event, possibly a speed bump or road imperfection. */
    fun lowConfidenceBump(): IncidentData = IncidentData(
        eventType = "possible_crash",
        confidence = 0.35f,
        peakAcceleration = 3.2f,
        peakGyroscope = 2.1f,
        impactDurationMs = 50L,
        riderMovement = "normal",
        cameraVerification = "normal",
        locationAvailable = true,
        latitude = 13.0827,
        longitude = 80.2707,
        timestampMs = System.currentTimeMillis(),
        detectorState = "MONITORING",
    )

    /** Severe crash with all sensors but no GPS. */
    fun severeCrashNoGps(): IncidentData = IncidentData(
        eventType = "possible_crash",
        confidence = 0.98f,
        peakAcceleration = 12.4f,
        peakGyroscope = 22.3f,
        impactDurationMs = 320L,
        riderMovement = "limited",
        cameraVerification = "possible_fall",
        locationAvailable = false,
        timestampMs = System.currentTimeMillis(),
        detectorState = "CONFIRMED",
    )

    /** Minimal data — sensor-only, no camera, no GPS. */
    fun minimalData(): IncidentData = IncidentData(
        eventType = "possible_crash",
        confidence = 0.72f,
        peakAcceleration = 6.1f,
        peakGyroscope = 0f,
        impactDurationMs = 150L,
        riderMovement = "unknown",
        cameraVerification = "not_available",
        locationAvailable = false,
        timestampMs = System.currentTimeMillis(),
        detectorState = "CONFIRMED",
    )

    /** Voice SOS triggered manually by rider. */
    fun voiceSos(): IncidentData = IncidentData(
        eventType = "voice_sos",
        confidence = 1.0f,
        peakAcceleration = 0f,
        peakGyroscope = 0f,
        impactDurationMs = 0L,
        riderMovement = "unknown",
        cameraVerification = "not_available",
        locationAvailable = true,
        latitude = 19.0760,
        longitude = 72.8777,
        timestampMs = System.currentTimeMillis(),
        detectorState = "CONFIRMED",
    )

    /** Returns all predefined test scenarios. */
    fun allScenarios(): List<Pair<String, IncidentData>> = listOf(
        "High-confidence crash" to highConfidenceCrash(),
        "Low-confidence bump" to lowConfidenceBump(),
        "Severe crash (no GPS)" to severeCrashNoGps(),
        "Minimal data" to minimalData(),
        "Voice SOS" to voiceSos(),
    )
}
