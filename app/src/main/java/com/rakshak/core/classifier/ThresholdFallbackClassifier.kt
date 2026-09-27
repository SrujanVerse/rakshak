package com.rakshak.core.classifier

import kotlin.math.sqrt

/**
 * ThresholdFallbackClassifier — deterministic crash classifier using acceleration thresholds.
 *
 * Wraps the same physics-based logic as the existing CrashDetector (magnitude threshold)
 * to provide a CrashClassifier interface when the TFLite model is unavailable.
 *
 * This is NOT a replacement for CrashDetector — it provides a classification
 * probability estimate from a sensor window, compatible with the CrashClassifier interface.
 *
 * The existing CrashDetector FSM remains the authority for detection decisions.
 */
class ThresholdFallbackClassifier(
    private val accelThreshold: Float = 30.0f, // m/s squared, matches CrashDetectionConfig
) : CrashClassifier {

    override val isReady: Boolean = true

    override fun classify(sensorWindow: FloatArray, sampleCount: Int): CrashClassificationResult? {
        if (sensorWindow.size < SensorPreprocessor.CHANNELS) return null

        val startTime = System.currentTimeMillis()
        var maxAccelMag = 0f

        for (i in 0 until sampleCount) {
            val offset = i * SensorPreprocessor.CHANNELS
            if (offset + 2 >= sensorWindow.size) break

            val ax = sensorWindow[offset]
            val ay = sensorWindow[offset + 1]
            val az = sensorWindow[offset + 2]
            val mag = sqrt(ax * ax + ay * ay + az * az)
            if (mag > maxAccelMag) maxAccelMag = mag
        }

        // Map acceleration to crash probability using a sigmoid-like curve
        val ratio = maxAccelMag / accelThreshold
        val probability = when {
            ratio >= 1.5f -> 0.95f
            ratio >= 1.0f -> 0.5f + 0.45f * ((ratio - 1.0f) / 0.5f)
            ratio >= 0.5f -> 0.1f + 0.4f * ((ratio - 0.5f) / 0.5f)
            else -> ratio * 0.2f
        }.coerceIn(0f, 1f)

        val elapsed = System.currentTimeMillis() - startTime

        return CrashClassificationResult(
            predictedClass = if (probability >= 0.5f) 1 else 0,
            crashProbability = probability,
            inferenceTimeMs = elapsed,
            classifierType = CLASSIFIER_TYPE,
        )
    }

    override fun release() {
        // No resources to release
    }

    companion object {
        const val CLASSIFIER_TYPE = "threshold_fallback"
    }
}
