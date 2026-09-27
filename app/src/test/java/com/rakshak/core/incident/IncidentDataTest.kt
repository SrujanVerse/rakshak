package com.rakshak.core.incident

import org.junit.Assert.*
import org.junit.Test

class IncidentDataTest {

    @Test
    fun `default values are safe`() {
        val data = IncidentData(
            eventType = "possible_crash",
            confidence = 0.9f,
            peakAcceleration = 8.0f,
            peakGyroscope = 10.0f,
            impactDurationMs = 200L,
        )
        assertEquals("unknown", data.riderMovement)
        assertEquals("not_available", data.cameraVerification)
        assertFalse(data.locationAvailable)
        assertNull(data.latitude)
        assertNull(data.longitude)
        assertNull(data.llmReport)
        assertEquals("CONFIRMED", data.detectorState)
    }

    @Test
    fun `toJsonString contains all required fields`() {
        val data = IncidentData(
            eventType = "possible_crash",
            confidence = 0.94f,
            peakAcceleration = 8.7f,
            peakGyroscope = 14.5f,
            impactDurationMs = 180L,
            riderMovement = "limited",
            cameraVerification = "possible_fall",
            locationAvailable = true,
            latitude = 12.97,
            longitude = 77.59,
        )
        val json = data.toJsonString()
        assertTrue(json.contains("\"event\":\"possible_crash\""))
        assertTrue(json.contains("\"confidence\":0.94"))
        assertTrue(json.contains("\"peak_acceleration\":8.7"))
        assertTrue(json.contains("\"peak_gyro\":14.5"))
        assertTrue(json.contains("\"impact_duration_ms\":180"))
        assertTrue(json.contains("\"rider_movement\":\"limited\""))
        assertTrue(json.contains("\"camera_verification\":\"possible_fall\""))
        assertTrue(json.contains("\"location_available\":true"))
        assertTrue(json.contains("\"latitude\":12.97"))
        assertTrue(json.contains("\"longitude\":77.59"))
    }

    @Test
    fun `toJsonString excludes lat-lon when null`() {
        val data = IncidentData(
            eventType = "possible_crash",
            confidence = 0.5f,
            peakAcceleration = 5.0f,
            peakGyroscope = 0f,
            impactDurationMs = 100L,
            locationAvailable = false,
        )
        val json = data.toJsonString()
        assertFalse(json.contains("latitude"))
        assertFalse(json.contains("longitude"))
    }

    @Test
    fun `withReport creates copy with report attached`() {
        val original = IncidentData(
            eventType = "possible_crash",
            confidence = 0.9f,
            peakAcceleration = 8.0f,
            peakGyroscope = 10.0f,
            impactDurationMs = 200L,
        )
        assertNull(original.llmReport)

        val withReport = original.withReport("Test report")
        assertEquals("Test report", withReport.llmReport)
        assertEquals(original.eventType, withReport.eventType)
        assertEquals(original.confidence, withReport.confidence)
    }
}
