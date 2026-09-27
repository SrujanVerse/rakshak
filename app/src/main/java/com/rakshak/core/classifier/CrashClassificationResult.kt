package com.rakshak.core.classifier

/**
 * CrashClassificationResult — output of the crash classifier.
 *
 * Maps the TFLite model output:
 *  - 0 = normal (no crash)
 *  - 1 = possible crash/fall
 */
data class CrashClassificationResult(
    /** Predicted class: 0 = normal, 1 = crash/fall */
    val predictedClass: Int,

    /** Probability of crash/fall (0.0–1.0) */
    val crashProbability: Float,

    /** Inference latency in milliseconds */
    val inferenceTimeMs: Long,

    /** Classifier type that produced this result */
    val classifierType: String,
) {
    val isCrash: Boolean get() = predictedClass == 1
    val isNormal: Boolean get() = predictedClass == 0
}
