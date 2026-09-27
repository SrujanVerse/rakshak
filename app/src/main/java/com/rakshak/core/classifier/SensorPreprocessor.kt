package com.rakshak.core.classifier

import com.rakshak.core.sensor.SensorData
import kotlin.math.sqrt

/**
 * SensorPreprocessor — prepares raw sensor data for TFLite model input.
 *
 * The TFLite 1D CNN expects a fixed-size window of normalized sensor readings:
 *  - Input shape: [1, WINDOW_SIZE, 6] (batch=1, time steps, channels)
 *  - Channels: [accelX, accelY, accelZ, gyroX, gyroY, gyroZ]
 *
 * Preprocessing steps:
 *  1. Align accelerometer and gyroscope samples by nearest timestamp
 *  2. Resample to fixed WINDOW_SIZE using linear interpolation
 *  3. Normalize to zero-mean, unit-variance (per-channel)
 *
 * The SisFall dataset uses 200Hz sampling; common training window is 2 seconds = 400 samples.
 * For deployment at 50Hz (SENSOR_DELAY_GAME), we use 2 seconds = 100 samples.
 */
object SensorPreprocessor {

    /** Number of time steps in the model input window */
    const val WINDOW_SIZE = 100

    /** Number of channels per time step (accelX,Y,Z + gyroX,Y,Z) */
    const val CHANNELS = 6

    /** Total floats in the flattened input array */
    const val INPUT_SIZE = WINDOW_SIZE * CHANNELS

    // Normalization constants derived from SisFall training dataset.
    private val CHANNEL_MEANS = floatArrayOf(0.091738f, -6.925079f, -0.962364f, -0.007917f, 0.037486f, -0.005303f)
    private val CHANNEL_STDS = floatArrayOf(3.915502f, 5.749281f, 4.743640f, 0.724069f, 0.606066f, 0.506556f)

    /**
     * Merge accelerometer and gyroscope buffers into a fixed-size input array.
     *
     * @param accelSamples Accelerometer samples (timestamp + [x, y, z])
     * @param gyroSamples Gyroscope samples (timestamp + [x, y, z])
     * @return Flattened float array of shape [WINDOW_SIZE x CHANNELS], or null if insufficient data
     */
    fun preprocess(
        accelSamples: List<SensorData>,
        gyroSamples: List<SensorData>,
    ): FloatArray? {
        if (accelSamples.size < 2 || gyroSamples.size < 2) return null

        // Step 1: Align by nearest timestamp
        val aligned = alignSamples(accelSamples, gyroSamples)
        if (aligned.size < 2) return null

        // Step 2: Resample to fixed window size
        val resampled = resampleToFixedSize(aligned, WINDOW_SIZE)

        // Step 3: Normalize per-channel
        return normalize(resampled)
    }

    /**
     * Align accelerometer and gyroscope by nearest timestamp.
     * For each accel sample, find the closest gyro sample.
     * Returns a list of 6-element arrays [aX, aY, aZ, gX, gY, gZ].
     */
    internal fun alignSamples(
        accelSamples: List<SensorData>,
        gyroSamples: List<SensorData>,
    ): List<FloatArray> {
        if (gyroSamples.isEmpty()) {
            return accelSamples.map { accel ->
                floatArrayOf(
                    accel.values.getOrElse(0) { 0f },
                    accel.values.getOrElse(1) { 0f },
                    accel.values.getOrElse(2) { 0f },
                    0f, 0f, 0f
                )
            }
        }

        val result = mutableListOf<FloatArray>()
        var gyroIdx = 0

        for (accel in accelSamples) {
            while (gyroIdx < gyroSamples.size - 1 &&
                Math.abs(gyroSamples[gyroIdx + 1].timestamp - accel.timestamp) <
                Math.abs(gyroSamples[gyroIdx].timestamp - accel.timestamp)
            ) {
                gyroIdx++
            }
            val gyro = gyroSamples[gyroIdx]
            result.add(
                floatArrayOf(
                    accel.values.getOrElse(0) { 0f },
                    accel.values.getOrElse(1) { 0f },
                    accel.values.getOrElse(2) { 0f },
                    gyro.values.getOrElse(0) { 0f },
                    gyro.values.getOrElse(1) { 0f },
                    gyro.values.getOrElse(2) { 0f },
                )
            )
        }
        return result
    }

    /**
     * Resample aligned data to exactly [targetSize] frames using linear interpolation.
     */
    internal fun resampleToFixedSize(
        data: List<FloatArray>,
        targetSize: Int,
    ): FloatArray {
        val output = FloatArray(targetSize * CHANNELS)
        val srcSize = data.size

        for (i in 0 until targetSize) {
            val srcPos = i.toFloat() * (srcSize - 1) / (targetSize - 1)
            val lower = srcPos.toInt().coerceIn(0, srcSize - 2)
            val upper = (lower + 1).coerceIn(0, srcSize - 1)
            val fraction = srcPos - lower

            for (ch in 0 until CHANNELS) {
                output[i * CHANNELS + ch] =
                    data[lower][ch] * (1f - fraction) + data[upper][ch] * fraction
            }
        }
        return output
    }

    /**
     * Normalize per-channel: (value - mean) / std
     */
    internal fun normalize(data: FloatArray): FloatArray {
        val result = FloatArray(data.size)
        for (i in data.indices) {
            val ch = i % CHANNELS
            result[i] = (data[i] - CHANNEL_MEANS[ch]) / CHANNEL_STDS[ch]
        }
        return result
    }

    /**
     * Compute peak values from sensor buffers for IncidentData.
     */
    fun computePeakValues(
        accelSamples: List<SensorData>,
        gyroSamples: List<SensorData>,
    ): Pair<Float, Float> {
        var peakAccel = 0f
        for (sample in accelSamples) {
            if (sample.values.size >= 3) {
                val mag = sqrt(
                    sample.values[0] * sample.values[0] +
                    sample.values[1] * sample.values[1] +
                    sample.values[2] * sample.values[2]
                )
                if (mag > peakAccel) peakAccel = mag
            }
        }

        var peakGyro = 0f
        for (sample in gyroSamples) {
            if (sample.values.size >= 3) {
                val mag = sqrt(
                    sample.values[0] * sample.values[0] +
                    sample.values[1] * sample.values[1] +
                    sample.values[2] * sample.values[2]
                )
                if (mag > peakGyro) peakGyro = mag
            }
        }

        return peakAccel to peakGyro
    }
}
