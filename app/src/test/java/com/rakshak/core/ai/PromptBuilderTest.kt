package com.rakshak.core.ai

import com.rakshak.core.incident.IncidentData
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    private val testIncident = IncidentData(
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

    @Test
    fun `buildPrompt contains ChatML markers`() {
        val prompt = PromptBuilder.buildPrompt(testIncident)
        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>user"))
        assertTrue(prompt.contains("<|im_start|>assistant"))
    }

    @Test
    fun `buildPrompt contains incident JSON`() {
        val prompt = PromptBuilder.buildPrompt(testIncident)
        assertTrue(prompt.contains("\"event\":\"possible_crash\""))
        assertTrue(prompt.contains("\"confidence\":0.94"))
    }

    @Test
    fun `buildPrompt contains system reasoning instructions`() {
        val prompt = PromptBuilder.buildPrompt(testIncident)
        assertTrue(prompt.contains("on-device safety reasoning brain"))
        assertTrue(prompt.contains("Do NOT invent"))
        assertTrue(prompt.contains("recommended_action"))
        assertTrue(prompt.contains("severity"))
    }

    @Test
    fun `buildSimplePrompt does not contain ChatML`() {
        val prompt = PromptBuilder.buildSimplePrompt(testIncident)
        assertFalse(prompt.contains("<|im_start|>"))
        assertFalse(prompt.contains("<|im_end|>"))
    }

    @Test
    fun `buildPrompt handles missing location`() {
        val noLocation = testIncident.copy(
            locationAvailable = false,
            latitude = null,
            longitude = null,
        )
        val prompt = PromptBuilder.buildPrompt(noLocation)
        assertTrue(prompt.contains("\"location_available\":false"))
        assertFalse(prompt.contains("\"latitude\""))
    }
}
