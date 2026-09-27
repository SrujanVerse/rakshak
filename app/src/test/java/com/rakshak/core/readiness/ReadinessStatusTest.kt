package com.rakshak.core.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * ReadinessStatusTest — unit tests for the ReadinessStatus data model.
 *
 * No Android context required — pure Kotlin data class.
 */
class ReadinessStatusTest {

    @Test
    fun `default state is CHECKING`() {
        val status = ReadinessStatus(id = "test", label = "Test")
        assertEquals(ReadinessState.CHECKING, status.state)
    }

    @Test
    fun `default isFixable is false`() {
        val status = ReadinessStatus(id = "test", label = "Test")
        assertFalse(status.isFixable)
    }

    @Test
    fun `default detail is empty string`() {
        val status = ReadinessStatus(id = "test", label = "Test")
        assertEquals("", status.detail)
    }

    @Test
    fun `OK state created correctly`() {
        val status = ReadinessStatus(
            id = "accelerometer",
            label = "Accelerometer",
            state = ReadinessState.OK,
        )
        assertEquals(ReadinessState.OK, status.state)
    }

    @Test
    fun `WARNING state with detail and isFixable created correctly`() {
        val status = ReadinessStatus(
            id = "sms_permission",
            label = "SMS Permission",
            state = ReadinessState.WARNING,
            detail = "Permission denied",
            isFixable = true,
        )
        assertEquals(ReadinessState.WARNING, status.state)
        assertEquals("Permission denied", status.detail)
        assertEquals(true, status.isFixable)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val original = ReadinessStatus(
            id = "loc", label = "Location", state = ReadinessState.OK, detail = "Ready"
        )
        val updated = original.copy(state = ReadinessState.WARNING)
        assertEquals("loc", updated.id)
        assertEquals("Location", updated.label)
        assertEquals("Ready", updated.detail)
        assertEquals(ReadinessState.WARNING, updated.state)
    }

    @Test
    fun `ReadinessState enum has exactly three values`() {
        val values = ReadinessState.values()
        assertEquals(3, values.size)
        assertNotNull(ReadinessState.valueOf("CHECKING"))
        assertNotNull(ReadinessState.valueOf("OK"))
        assertNotNull(ReadinessState.valueOf("WARNING"))
    }
}
