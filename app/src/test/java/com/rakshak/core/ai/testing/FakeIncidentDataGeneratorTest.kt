package com.rakshak.core.ai.testing

import org.junit.Assert.*
import org.junit.Test

class FakeIncidentDataGeneratorTest {

    @Test
    fun `highConfidenceCrash matches specification`() {
        val incident = FakeIncidentDataGenerator.highConfidenceCrash()
        assertEquals("possible_crash", incident.eventType)
        assertEquals(0.94f, incident.confidence, 0.001f)
        assertEquals(8.7f, incident.peakAcceleration, 0.001f)
        assertEquals(14.5f, incident.peakGyroscope, 0.001f)
        assertEquals(180L, incident.impactDurationMs)
        assertEquals("limited", incident.riderMovement)
        assertEquals("possible_fall", incident.cameraVerification)
        assertTrue(incident.locationAvailable)
        assertNotNull(incident.latitude)
        assertNotNull(incident.longitude)
        assertEquals("CONFIRMED", incident.detectorState)
    }

    @Test
    fun `allScenarios returns non-empty list with valid data`() {
        val scenarios = FakeIncidentDataGenerator.allScenarios()
        assertTrue(scenarios.isNotEmpty())
        for ((name, incident) in scenarios) {
            assertTrue("Scenario '$name' has empty eventType", incident.eventType.isNotBlank())
            assertTrue("Scenario '$name' has negative confidence", incident.confidence >= 0f)
            assertTrue("Scenario '$name' has confidence > 1", incident.confidence <= 1f)
            assertTrue("Scenario '$name' has negative acceleration", incident.peakAcceleration >= 0f)
            assertTrue("Scenario '$name' has negative gyro", incident.peakGyroscope >= 0f)
            assertTrue("Scenario '$name' has negative duration", incident.impactDurationMs >= 0L)
        }
    }

    @Test
    fun `severeCrashNoGps has no location`() {
        val incident = FakeIncidentDataGenerator.severeCrashNoGps()
        assertFalse(incident.locationAvailable)
        assertNull(incident.latitude)
        assertNull(incident.longitude)
    }

    @Test
    fun `minimalData has unknown and unavailable fields`() {
        val incident = FakeIncidentDataGenerator.minimalData()
        assertEquals("unknown", incident.riderMovement)
        assertEquals("not_available", incident.cameraVerification)
        assertFalse(incident.locationAvailable)
    }

    @Test
    fun `voiceSos has correct event type and full confidence`() {
        val incident = FakeIncidentDataGenerator.voiceSos()
        assertEquals("voice_sos", incident.eventType)
        assertEquals(1.0f, incident.confidence, 0.001f)
        assertEquals(0f, incident.peakAcceleration, 0.001f)
    }

    @Test
    fun `toJsonString is valid for all scenarios`() {
        for ((name, incident) in FakeIncidentDataGenerator.allScenarios()) {
            val json = incident.toJsonString()
            assertTrue("Scenario '$name' JSON should start with {", json.startsWith("{"))
            assertTrue("Scenario '$name' JSON should end with }", json.endsWith("}"))
            assertTrue("Scenario '$name' JSON should contain event", json.contains("\"event\""))
        }
    }
}
