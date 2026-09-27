package com.rakshak.core.detector

import com.rakshak.core.sensor.SensorData
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashDetectorTest {

    private val defaultConfig = CrashDetectionConfig()
    
    private fun vector(x: Float): FloatArray = floatArrayOf(x, 0f, 0f)

    @Test
    fun testEmptyOrInsufficientBufferIsHandledSafely() {
        val detector = CrashDetector(defaultConfig)
        
        detector.processAccelerometerBuffer(emptyList())
        assertEquals(DetectorState.MONITORING, detector.currentState)

        detector.processAccelerometerBuffer(listOf(SensorData(1000L, vector(9.8f))))
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testMalformedDataShortArrayIsSafelyIgnored() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        
        // This vector only has 2 elements!
        val badVector = floatArrayOf(40.0f, 40.0f)
        val buffer = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, badVector)
        )
        detector.processAccelerometerBuffer(buffer)
        
        // Should ignore it and stay in monitoring
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testMalformedDataNaNIsSafelyIgnored() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, floatArrayOf(40.0f, Float.NaN, 0f))
        )
        detector.processAccelerometerBuffer(buffer)
        
        // Should ignore it and stay in monitoring
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testMalformedDataInfiniteIsSafelyIgnored() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f))
        )
        detector.processAccelerometerBuffer(buffer)
        
        // Should ignore it and stay in monitoring
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testHistoricalSpikeCannotRetrigger() {
        val config = CrashDetectionConfig(jerkThreshold = 100000f)
        val detector = CrashDetector(config)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer1 = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer1)
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)

        val timeoutTime = t1 + config.candidateTimeoutNanos + 1L
        val buffer2 = buffer1 + SensorData(timeoutTime, vector(9.8f))
        
        detector.processAccelerometerBuffer(buffer2)
        assertEquals(DetectorState.MONITORING, detector.currentState)

        val newTime = timeoutTime + 20_000_000L
        val buffer3 = buffer2 + SensorData(newTime, vector(9.8f))
        
        detector.processAccelerometerBuffer(buffer3)
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testJerkInsideCandidateWindowConfirms() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 400_000_000L // 0.4s gap makes jerk < 100
        val buffer1 = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer1)
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)

        val t2 = t1 + defaultConfig.candidateTimeoutNanos - 100_000_000L // inside window
        val buffer2 = buffer1 + SensorData(t2, vector(80.0f)) // causes high jerk
        
        detector.processAccelerometerBuffer(buffer2)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)
    }

    @Test
    fun testIdenticalJerkAfterCandidateTimeoutNanosDoesNotConfirm() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 400_000_000L
        val buffer1 = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer1)
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)

        val t2 = t1 + defaultConfig.candidateTimeoutNanos + 100_000_000L // outside window
        val buffer2 = buffer1 + SensorData(t2, vector(80.0f)) // causes high jerk, but too late
        
        detector.processAccelerometerBuffer(buffer2)
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testGenuineContinuous2SecondRestingIntervalConfirms() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f)) // Immediately jumps to CONFIRMING
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        var t = t1
        val step = 100_000_000L // 100ms
        while (t - t1 <= defaultConfig.persistenceWindowNanos) {
            t += step
            buffer.add(SensorData(t, vector(9.8f)))
        }
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMED, detector.currentState)
    }

    @Test
    fun testRestingIntervalWithOneLargeGapDoesNotConfirm() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val t2 = t1 + 100_000_000L
        buffer.add(SensorData(t2, vector(9.8f)))
        
        // Huge gap of 1 second (exceeds maxAcceptableGapNanos=0.5s)
        val t3 = t2 + 1_000_000_000L
        buffer.add(SensorData(t3, vector(9.8f)))
        
        val t4 = t3 + 1_000_000_000L
        buffer.add(SensorData(t4, vector(9.8f)))
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState) // Waiting or monitoring, but NOT confirmed
    }

    @Test
    fun testRestingIntervalContainingNonRestingSampleDoesNotConfirm() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        var t = t1
        val step = 100_000_000L
        while (t - t1 <= defaultConfig.persistenceWindowNanos) {
            t += step
            val mag = if (t - t1 == 1_000_000_000L) 20.0f else 9.8f
            buffer.add(SensorData(t, vector(mag)))
        }
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState) // Hasn't had 2s of continuous rest
    }

    @Test
    fun testOutOfOrderTimestampsDoNotFalselyConfirm() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val step = 100_000_000L
        var t = t1 + step
        buffer.add(SensorData(t, vector(9.8f)))
        
        t += step
        buffer.add(SensorData(t + 2_000_000_000L, vector(9.8f))) 
        buffer.add(SensorData(t, vector(9.8f))) // Out of order!

        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)
    }

    @Test
    fun testDuplicateTimestampsDoNotFalselyConfirm() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val t2 = t1 + 2_500_000_000L 
        buffer.add(SensorData(t2, vector(9.8f)))
        buffer.add(SensorData(t2, vector(9.8f))) // Duplicate timestamp!
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)
    }

    @Test
    fun testCooldownBlocksDuplicateIncidentAndNewIncidentCanTrigger() {
        val detector = CrashDetector(defaultConfig)
        
        detector.triggerExternalIncident(0L)
        assertEquals(DetectorState.CONFIRMED, detector.currentState)

        detector.markAlerted(100L)
        assertEquals(DetectorState.ALERTED, detector.currentState)

        val buffer = listOf(
            SensorData(300L, vector(9.8f)),
            SensorData(300L + 20_000_000L, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        detector.triggerExternalIncident(200L)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        val afterCooldown = 300L + 20_000_000L + defaultConfig.cooldownWindowNanos + 1L
        detector.processAccelerometerBuffer(listOf(
            SensorData(300L + 20_000_000L, vector(40.0f)),
            SensorData(afterCooldown, vector(9.8f))
        ))
        assertEquals(DetectorState.MONITORING, detector.currentState)
        
        val newCrash = afterCooldown + 400_000_000L // 0.4s gap prevents instantaneous jerk confirmation
        detector.processAccelerometerBuffer(listOf(
            SensorData(afterCooldown, vector(9.8f)),
            SensorData(newCrash, vector(40.0f))
        ))
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)
    }
}
