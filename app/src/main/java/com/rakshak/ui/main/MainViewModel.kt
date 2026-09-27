package com.rakshak.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.core.mode.AppMode
import com.rakshak.core.mode.ModeManager
import com.rakshak.core.readiness.ReadinessChecker
import com.rakshak.core.readiness.ReadinessStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MainViewModel — owns the System Readiness screen state.
 *
 * Exposes:
 *  - [readinessItems]: StateFlow<List<ReadinessStatus>> — live readiness checks.
 *  - [currentMode]: StateFlow<AppMode> — current DEMO/REAL mode.
 *
 * Architecture note: AndroidViewModel is used (not ViewModel) because ReadinessChecker
 * needs a Context to query SensorManager/TelephonyManager. The Application context is
 * used (not Activity context) to prevent memory leaks.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val checker = ReadinessChecker(application.applicationContext)

    /** Live readiness status for all six P0 components. */
    val readinessItems: StateFlow<List<ReadinessStatus>> = checker.statusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Current operating mode — observed by the UI for the mode toggle. */
    private val _currentMode = kotlinx.coroutines.flow.MutableStateFlow(ModeManager.currentMode)
    val currentMode: StateFlow<AppMode> = _currentMode

    init {
        // Initial check immediately
        refresh()

        // Periodic refresh every 5 seconds so readiness stays live
        viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    /** Re-evaluate all readiness checks. Safe to call from any thread. */
    fun refresh() {
        checker.refresh()
    }

    /**
     * Toggle between DEMO and REAL mode.
     * This is the ONLY place in the codebase that switches [ModeManager].
     */
    fun setMode(mode: AppMode) {
        ModeManager.setMode(mode)
        _currentMode.value = mode
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
    }
}
