package com.rakshak.core.incident

import com.rakshak.core.detector.IncidentDecisionEngine
import com.rakshak.core.detector.IncidentDecisionState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IncidentCountdownManagerTest {

    @Before
    fun setUp() {
        IncidentCountdownManager.resetToIdle()
        IncidentDecisionEngine.resetToNormal()
    }

    @Test
    fun testStartCountdownInitialState() {
        assertEquals(CountdownStatus.IDLE, IncidentCountdownManager.state.value.status)

        // Null-safe context handling inside manager
        IncidentCountdownManager.startCountdown(
            context = dummyContext(),
            incidentId = "TEST-101"
        )

        val state = IncidentCountdownManager.state.value
        assertEquals(CountdownStatus.RUNNING, state.status)
        assertEquals(10, state.remainingSeconds)
        assertEquals("TEST-101", state.incidentId)
    }

    @Test
    fun testDuplicateStartIgnored() {
        IncidentCountdownManager.startCountdown(
            context = dummyContext(),
            incidentId = "TEST-101"
        )
        val state1 = IncidentCountdownManager.state.value
        assertEquals(CountdownStatus.RUNNING, state1.status)

        // Attempting to start again should be ignored
        IncidentCountdownManager.startCountdown(
            context = dummyContext(),
            incidentId = "TEST-102"
        )
        val state2 = IncidentCountdownManager.state.value
        assertEquals("TEST-101", state2.incidentId)
    }

    @Test
    fun testCancelCountdownResetsDecisionEngine() {
        IncidentCountdownManager.startCountdown(
            context = dummyContext(),
            incidentId = "TEST-CANCEL"
        )
        assertEquals(CountdownStatus.RUNNING, IncidentCountdownManager.state.value.status)

        IncidentCountdownManager.cancelCountdown(dummyContext(), "TEST_SUITE")

        val state = IncidentCountdownManager.state.value
        assertEquals(CountdownStatus.CANCELLED, state.status)
        assertEquals(IncidentDecisionState.NORMAL, IncidentDecisionEngine.decisionState.value)
    }

    @Test
    fun testResetToIdle() {
        IncidentCountdownManager.startCountdown(
            context = dummyContext(),
            incidentId = "TEST-IDLE"
        )
        assertEquals(CountdownStatus.RUNNING, IncidentCountdownManager.state.value.status)

        IncidentCountdownManager.resetToIdle()
        assertEquals(CountdownStatus.IDLE, IncidentCountdownManager.state.value.status)
    }

    private fun dummyContext(): android.content.Context {
        return object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context {
                return this
            }
        }
    }
}
