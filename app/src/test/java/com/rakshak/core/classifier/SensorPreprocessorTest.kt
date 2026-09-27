package com.rakshak.core.classifier

import com.rakshak.core.sensor.SensorData
import org.junit.Assert.*
import org.junit.Test

class SensorPreprocessorTest {

    @Test
    fun `alignSamples pairs accel with nearest gyro`() {
        val accel = listOf(
            SensorData(100L, floatArrayOf(1f, 0f, 0f)),
            SensorData(200L, floatArrayOf(2f, 0f, 0f)),
            SensorData(300L, floatArrayOf(3f, 0f, 0f)),
        )
        val gyro = listOf(
            SensorData(90L, floatArrayOf(10f, 0f, 0f)),
            SensorData(210L, floatArrayOf(20f, 0f, 0f)),
            SensorData(310L, floatArrayOf(30f, 0f, 0f)),
        )
        val aligned = SensorPreprocessor.alignSamples(accel, gyro)
        assertEquals(3, aligned.size)
        // First accel (t=100) should match gyro (t=90)
        assertEquals(10f, aligned[0][3], 0.01f)
        // Second accel (t=200) should match gyro (t=210)
        assertEquals(20f, aligned[1][3], 0.01f)
    }

    @Test
    fun `alignSamples handles empty gyro by using zeros`() {
        val accel = listOf(
            SensorData(100L, floatArrayOf(1f, 2f, 3f)),
        )
        val aligned = SensorPreprocessor.alignSamples(accel, emptyList())
        assertEquals(1, aligned.size)
        assertEquals(1f, aligned[0][0], 0.01f)
        assertEquals(0f, aligned[0][3], 0.01f)
        assertEquals(0f, aligned[0][4], 0.01f)
        assertEquals(0f, aligned[0][5], 0.01f)
    }

    @Test
    fun `resampleToFixedSize produces correct output length`() {
        val data = List(50) { floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f) }
        val result = SensorPreprocessor.resampleToFixedSize(data, SensorPreprocessor.WINDOW_SIZE)
        assertEquals(SensorPreprocessor.INPUT_SIZE, result.size)
    }

    @Test
    fun `preprocess returns null for insufficient data`() {
        val accel = listOf(SensorData(100L, floatArrayOf(1f, 0f, 0f)))
        val gyro = listOf(SensorData(100L, floatArrayOf(0f, 0f, 0f)))
        assertNull(SensorPreprocessor.preprocess(accel, gyro))
    }

    @Test
    fun `computePeakValues finds maximum magnitudes`() {
        val accel = listOf(
            SensorData(100L, floatArrayOf(1f, 0f, 0f)),
            SensorData(200L, floatArrayOf(3f, 4f, 0f)),
            SensorData(300L, floatArrayOf(2f, 0f, 0f)),
        )
        val gyro = listOf(
            SensorData(100L, floatArrayOf(0f, 0f, 1f)),
            SensorData(200L, floatArrayOf(0f, 3f, 4f)),
        )
        val (peakAccel, peakGyro) = SensorPreprocessor.computePeakValues(accel, gyro)
        assertEquals(5f, peakAccel, 0.01f)
        assertEquals(5f, peakGyro, 0.01f)
    }
}
