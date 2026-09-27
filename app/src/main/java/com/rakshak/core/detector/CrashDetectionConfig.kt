package com.rakshak.core.detector

data class CrashDetectionConfig(
    /** Acceleration magnitude required to enter IMPACT_CANDIDATE state (m/s^2). ~3G */
    val accelMagnitudeThreshold: Float = 30.0f,
    
    /** Jerk magnitude required to advance to CONFIRMING state (m/s^3). */
    val jerkThreshold: Float = 100.0f,
    
    /** Maximum time to find a jerk spike after an acceleration spike (nanoseconds). */
    val candidateTimeoutNanos: Long = 500_000_000L, // 0.5 seconds
    
    /** The continuous time the device must remain "resting" to be CONFIRMED (nanoseconds). */
    val persistenceWindowNanos: Long = 2_000_000_000L, // 2 seconds
    
    /** The maximum time we wait in CONFIRMING before timing out to MONITORING (nanoseconds). */
    val persistenceTimeoutNanos: Long = 3_000_000_000L, // 3 seconds
    
    /** The max acceleration magnitude (m/s^2) considered "resting" during CONFIRMING. */
    val persistenceRestingThreshold: Float = 15.0f, // Gravity is 9.8, so 15 allows some wobble
    
    /** The maximum acceptable gap between sensor readings during persistence checks. */
    val maxAcceptableGapNanos: Long = 500_000_000L, // 0.5 seconds
    
    /** How long to block duplicate alerts after an ALERTED state (nanoseconds). */
    val cooldownWindowNanos: Long = 10_000_000_000L // 10 seconds
)
