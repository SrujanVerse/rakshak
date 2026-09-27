package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NoOpReportGeneratorTest {

    private val generator = NoOpReportGenerator()

    @Test
    fun `isReady always returns true`() {
        assertTrue(generator.isReady())
    }

    @Test
    fun `generates report for high confidence crash`() = runBlocking {
        val incident = IncidentData(
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
        val result = generator.generateReport(incident)
        assertTrue(result.isSuccess)
        assertNotNull(result.report)
        assertTrue(result.report!!.contains("severe"))
        assertTrue(result.report!!.contains("8.7"))
        assertTrue(result.report!!.contains("Limited rider movement"))
        assertTrue(result.report!!.contains("possible fall"))
        assertEquals(NoOpReportGenerator.GENERATOR_TYPE, result.generatorType)
        assertEquals(0, result.tokenCount)
    }

    @Test
    fun `generates report with unavailable fields`() = runBlocking {
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = 0.72f,
            peakAcceleration = 6.1f,
            peakGyroscope = 0f,
            impactDurationMs = 150L,
            riderMovement = "unknown",
            cameraVerification = "not_available",
            locationAvailable = false,
        )
        val result = generator.generateReport(incident)
        assertTrue(result.isSuccess)
        assertTrue(result.report!!.contains("unavailable"))
    }

    @Test
    fun `severity levels map correctly`() = runBlocking {
        val severe = generator.generateReport(makeIncident(0.95f))
        assertTrue(severe.report!!.contains("severe"))

        val moderate = generator.generateReport(makeIncident(0.75f))
        assertTrue(moderate.report!!.contains("moderate"))

        val possible = generator.generateReport(makeIncident(0.55f))
        assertTrue(possible.report!!.contains("possible"))

        val low = generator.generateReport(makeIncident(0.3f))
        assertTrue(low.report!!.contains("low-confidence"))
    }

    @Test
    fun `latency is near zero`() = runBlocking {
        val result = generator.generateReport(makeIncident(0.9f))
        assertTrue("Template should be < 100ms, was ${result.latencyMs}ms", result.latencyMs < 100)
    }

    private fun makeIncident(confidence: Float) = IncidentData(
        eventType = "possible_crash",
        confidence = confidence,
        peakAcceleration = 8.0f,
        peakGyroscope = 10.0f,
        impactDurationMs = 200L,
    )
}
