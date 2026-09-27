package com.rakshak.core.classifier

/**
 * CrashClassifier — interface for ML-based crash classification.
 *
 * Abstracts the classification model so the system can use:
 *  - [ThresholdFallbackClassifier]: Wraps existing deterministic logic (always available)
 *  - [TFLiteCrashClassifier]: 1D CNN TFLite model (when .tflite file is available)
 *
 * Architecture rule: The existing deterministic CrashDetector remains the primary
 * detection mechanism. The TFLite classifier is an ADDITIONAL signal that can
 * CONFIRM or UPGRADE a detection, but cannot PREVENT the deterministic path from
 * triggering an alert.
 *
 * Input: Windowed sensor data [accelX, accelY, accelZ, gyroX, gyroY, gyroZ] x N samples
 * Output: CrashClassificationResult with probability and class label
 */
interface CrashClassifier {

    /** True if the classifier is initialized and ready for inference */
    val isReady: Boolean

    /**
     * Classify a window of sensor data.
     *
     * @param sensorWindow Flattened array of [N x 6] sensor readings
     *        (accelX, accelY, accelZ, gyroX, gyroY, gyroZ) per sample
     * @param sampleCount Number of samples in the window (N)
     * @return Classification result, or null on failure
     */
    fun classify(sensorWindow: FloatArray, sampleCount: Int): CrashClassificationResult?

    /** Release any resources (model, interpreter) */
    fun release()
}
