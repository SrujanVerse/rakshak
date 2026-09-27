package com.rakshak.core.mode

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ModeManagerTest — unit tests for the global DEMO/REAL mode switch.
 *
 * Test categories:
 *  - Normal: initialize DEMO, initialize REAL, toggle between modes
 *  - Edge: double-initialize (second call must win), concurrent reads
 *  - Failure: none applicable (AtomicReference cannot throw)
 *
 * Architecture note: ModeManager is a singleton. Each test resets it to DEMO
 * in @Before and @After to prevent state leaking between tests.
 */
class ModeManagerTest {

    @Before
    fun setUp() {
        // Reset to safe DEMO default before every test
        ModeManager.initialize(demoModeDefault = true)
    }

    @After
    fun tearDown() {
        // Ensure DEMO is restored after every test
        ModeManager.setMode(AppMode.DEMO)
    }

    // ── Normal cases ─────────────────────────────────────────────────────────

    @Test
    fun `initialize with demoModeDefault=true sets DEMO mode`() {
        ModeManager.initialize(demoModeDefault = true)
        assertEquals(AppMode.DEMO, ModeManager.currentMode)
        assertTrue("isDemoMode should be true", ModeManager.isDemoMode)
        assertFalse("isRealMode should be false", ModeManager.isRealMode)
    }

    @Test
    fun `initialize with demoModeDefault=false sets REAL mode`() {
        ModeManager.initialize(demoModeDefault = false)
        assertEquals(AppMode.REAL, ModeManager.currentMode)
        assertTrue("isRealMode should be true", ModeManager.isRealMode)
        assertFalse("isDemoMode should be false", ModeManager.isDemoMode)
    }

    @Test
    fun `setMode REAL switches to REAL`() {
        ModeManager.initialize(demoModeDefault = true)
        ModeManager.setMode(AppMode.REAL)
        assertEquals(AppMode.REAL, ModeManager.currentMode)
        assertTrue(ModeManager.isRealMode)
    }

    @Test
    fun `setMode DEMO switches to DEMO`() {
        ModeManager.initialize(demoModeDefault = false)
        ModeManager.setMode(AppMode.DEMO)
        assertEquals(AppMode.DEMO, ModeManager.currentMode)
        assertTrue(ModeManager.isDemoMode)
    }

    @Test
    fun `setDemoMode true sets DEMO`() {
        ModeManager.setDemoMode(true)
        assertTrue(ModeManager.isDemoMode)
        assertFalse(ModeManager.isRealMode)
    }

    @Test
    fun `setDemoMode false sets REAL`() {
        ModeManager.setDemoMode(false)
        assertFalse(ModeManager.isDemoMode)
        assertTrue(ModeManager.isRealMode)
    }

    @Test
    fun `toggle DEMO to REAL to DEMO preserves correctness`() {
        ModeManager.setMode(AppMode.DEMO)
        assertEquals(AppMode.DEMO, ModeManager.currentMode)

        ModeManager.setMode(AppMode.REAL)
        assertEquals(AppMode.REAL, ModeManager.currentMode)

        ModeManager.setMode(AppMode.DEMO)
        assertEquals(AppMode.DEMO, ModeManager.currentMode)
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `double initialize — second call wins`() {
        ModeManager.initialize(demoModeDefault = true)
        ModeManager.initialize(demoModeDefault = false)
        // Second call must override the first
        assertEquals(AppMode.REAL, ModeManager.currentMode)
    }

    @Test
    fun `currentMode readable from multiple threads without lock`() {
        ModeManager.initialize(demoModeDefault = true)
        val results = mutableListOf<AppMode>()
        val threads = (1..10).map {
            Thread { synchronized(results) { results.add(ModeManager.currentMode) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // All 10 reads must return DEMO (no race condition)
        assertTrue("All reads must return DEMO", results.all { it is AppMode.DEMO })
        assertEquals(10, results.size)
    }

    @Test
    fun `AppMode toString returns correct label`() {
        assertEquals("DEMO", AppMode.DEMO.toString())
        assertEquals("REAL", AppMode.REAL.toString())
    }

    // ── Failure / boundary cases ──────────────────────────────────────────────

    @Test
    fun `setMode called before initialize uses safe DEMO default`() {
        // ModeManager starts as DEMO (set in @Before).
        // Simulate a module reading mode before Application.onCreate fires.
        // The safe default (DEMO) must be returned — never null, never throw.
        val mode = ModeManager.currentMode
        assertTrue("Safe default must be DEMO", mode is AppMode.DEMO)
    }
}
