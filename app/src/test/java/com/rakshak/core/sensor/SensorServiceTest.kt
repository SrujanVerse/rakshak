package com.rakshak.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensorManager

import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensorServiceTest {

    @Test
    fun `missing-sensor doesn't crash startup`() {
        // By default in Robolectric, there are NO sensors available.
        // So getDefaultSensor will return null for both Accelerometer and Gyroscope.
        
        val controller = Robolectric.buildService(SensorService::class.java)
        
        // This will call onCreate() and then onStartCommand().
        // If it throws an exception, the test will fail.
        val service = controller.create().startCommand(0, 1).get()
        
        // It successfully started and didn't crash.
        assertNotNull(service)
    }
}
