package com.rakshak

import android.app.Application
import android.util.Log
import com.rakshak.core.mode.AppMode
import com.rakshak.core.mode.ModeManager

/**
 * RakshakApplication — Application entry point.
 *
 * Responsibilities:
 *  1. Initialize ModeManager (DEMO vs REAL) from BuildConfig before any component starts.
 *  2. Provide a global uncaught-exception handler that logs without crashing the demo.
 *
 * Architecture note: No P0 components are started here. P0 lifecycle is managed by
 * the foreground Service (added in a later prompt). This keeps the Application class
 * lean and testable.
 */
class RakshakApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize mode FIRST — every module that sends real-world signals reads this.
        ModeManager.initialize(BuildConfig.DEMO_MODE_DEFAULT)

        Log.i(TAG, "Rakshak starting — mode=${ModeManager.currentMode}")

        // Global uncaught-exception handler: log the crash, never silently swallow it,
        // but keep the process alive so P0 (if already running in a Service) continues.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on thread=${thread.name}", throwable)
            // Re-throw so Android can show the crash dialog in non-Service threads.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "RakshakApp"
    }
}
