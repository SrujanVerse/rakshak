package com.rakshak.core.incident

import com.rakshak.core.alert.LocationData
import com.rakshak.core.classifier.CrashClassificationResult
import com.rakshak.core.classifier.SensorPreprocessor
import com.rakshak.core.detector.DetectorState
import com.rakshak.core.sensor.SensorData

/**
 * IncidentDataBuilder — constructs IncidentData from the crash detection pipeline outputs.
 *
 * Bridges the gap between:
 *  - CrashDetector (DetectorState, sensor buffers)
 *  - CrashClassifier (CrashClassificationResult)
 *  - LocationController (LocationData)
 *
 * This builder collects data from existing components WITHOUT modifying them.
 * It reads from public APIs of existing classes.
 */
object IncidentDataBuilder {

    /**
     * Build IncidentData from available pipeline components.
     *
     * @param detectorState Current detector state (should be CONFIRMED for real incidents)
     * @param accelBuffer Recent accelerometer samples
     * @param gyroBuffer Recent gyroscope samples
     * @param classificationResult Optional ML classifier result (null = classifier unavailable)
     * @param location Optional GPS location (null = location unavailable)
     * @param impactDurationMs Estimated duration from impact to resting (ms)
     * @param riderMovement Post-impact movement assessment
     * @param cameraVerification Camera verification result
     */
    fun build(
        detectorState: DetectorState,
        accelBuffer: List<SensorData>,
        gyroBuffer: List<SensorData>,
        classificationResult: CrashClassificationResult? = null,
        location: LocationData? = null,
        impactDurationMs: Long = 0L,
        riderMovement: String = "unknown",
        cameraVerification: String = "not_available",
    ): IncidentData {
        val (peakAccel, peakGyro) = SensorPreprocessor.computePeakValues(accelBuffer, gyroBuffer)

        // Use ML confidence if available, otherwise use 1.0 for FSM-confirmed crashes
        val confidence = classificationResult?.crashProbability
            ?: if (detectorState == DetectorState.CONFIRMED) 1.0f else 0.5f

        return IncidentData(
            eventType = when (detectorState) {
                DetectorState.CONFIRMED -> "possible_crash"
                DetectorState.ALERTED -> "possible_crash"
                else -> "sensor_anomaly"
            },
            confidence = confidence,
            peakAcceleration = peakAccel,
            peakGyroscope = peakGyro,
            impactDurationMs = impactDurationMs,
            riderMovement = riderMovement,
            cameraVerification = cameraVerification,
            locationAvailable = location != null,
            latitude = location?.latitude,
            longitude = location?.longitude,
            timestampMs = System.currentTimeMillis(),
            detectorState = detectorState.name,
        )
    }
}
