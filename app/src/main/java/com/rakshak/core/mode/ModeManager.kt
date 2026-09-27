package com.rakshak.core.mode

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * AppMode — sealed class representing the two operating modes.
 *
 * REAL  — live production mode: SmsManager sends actual messages, sensors are real.
 * DEMO  — test/hackathon-demo mode: SmsManager is swapped for an in-memory recorder;
 *          the entire P0 pipeline runs identically, only the signal emitters are mocked.
 *
 * This is the ONLY source of truth for mode. Every module that sends a real-world
 * signal (SMS, camera flash, audio) must check ModeManager.isRealMode before acting.
 */
sealed class AppMode {
    /** Live mode — real SMS, real sensors. Use only for actual deployment or real demo. */
    object REAL : AppMode() {
        override fun toString(): String = "REAL"
    }

    /** Demo/test mode — in-memory recorders replace all real-world signal emitters. */
    object DEMO : AppMode() {
        override fun toString(): String = "DEMO"
    }
}

/**
 * ModeManager — singleton that owns the global AppMode.
 *
 * Thread-safety: AtomicReference ensures safe reads from any thread (including the
 * P0 SMS-send thread which must never block on a lock).
 *
 * Lifecycle:
 *  - [initialize] is called once from RakshakApplication.onCreate().
 *  - [setMode] may be called from the UI (MainActivity toggle).
 *  - [currentMode] is read by every module that sends a real-world signal.
 */
object ModeManager {

    private const val TAG = "ModeManager"

    private val _mode = AtomicReference<AppMode>(AppMode.DEMO) // safe default

    /** Initialize from BuildConfig. Called once in Application.onCreate(). */
    fun initialize(demoModeDefault: Boolean) {
        val initial = if (demoModeDefault) AppMode.DEMO else AppMode.REAL
        _mode.set(initial)
        Log.i(TAG, "ModeManager initialized — mode=$initial")
    }

    /** Current operating mode. Safe to read from any thread without locking. */
    val currentMode: AppMode
        get() = _mode.get()

    /** True when operating in real mode (sends actual SMS, uses real sensors). */
    val isRealMode: Boolean
        get() = _mode.get() is AppMode.REAL

    /** True when operating in demo/test mode (in-memory recorders only). */
    val isDemoMode: Boolean
        get() = _mode.get() is AppMode.DEMO

    /**
     * Switch operating mode. Typically called from MainActivity UI toggle.
     *
     * @param mode The new [AppMode] to activate.
     */
    fun setMode(mode: AppMode) {
        val previous = _mode.getAndSet(mode)
        if (previous != mode) {
            Log.i(TAG, "Mode changed: $previous → $mode")
        }
    }

    /** Convenience overload for Java interop / simple boolean toggle. */
    fun setDemoMode(demo: Boolean) {
        setMode(if (demo) AppMode.DEMO else AppMode.REAL)
    }
}
