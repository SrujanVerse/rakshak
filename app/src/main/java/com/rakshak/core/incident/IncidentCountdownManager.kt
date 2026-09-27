package com.rakshak.core.incident

import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import com.rakshak.core.detector.IncidentDecisionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CountdownStatus — state of the authoritative countdown.
 */
enum class CountdownStatus {
    IDLE,
    RUNNING,
    CANCELLED,
    EXPIRED
}

/**
 * CountdownState — immutable snapshot of current countdown state.
 */
data class CountdownState(
    val status: CountdownStatus = CountdownStatus.IDLE,
    val remainingSeconds: Int = 10,
    val incidentId: String = ""
)

/**
 * IncidentCountdownManager — Single Authoritative Countdown State.
 *
 * Architecture Rules:
 *  1. ONE single CountDownTimer instance for the entire system.
 *  2. Shared state observed by SensorService (notifications) and EmergencyCountdownActivity (UI).
 *  3. "I'M OKAY" cancellation from either notification or in-app button calls [cancelCountdown].
 *  4. Cancellation stops timer, resets IncidentDecisionEngine to NORMAL, logs record, and prevents SMS/SOS.
 *  5. Countdown reaching zero (0s) sets status to EXPIRED and triggers post-countdown verification.
 */
object IncidentCountdownManager {

    private const val TAG = "IncidentCountdownMgr"
    const val DURATION_SECONDS = 10

    private val _state = MutableStateFlow(CountdownState())
    val state: StateFlow<CountdownState> = _state.asStateFlow()

    private var countDownTimer: CountDownTimer? = null
    private var onTickCallback: ((Int) -> Unit)? = null
    private var onExpiredCallback: (() -> Unit)? = null
    private var onCancelledCallback: (() -> Unit)? = null

    /**
     * Start the authoritative 10-second countdown.
     * If already running, this is a no-op to prevent duplicate timers.
     */
    @Synchronized
    fun startCountdown(
        context: Context,
        incidentId: String = "RKS-${System.currentTimeMillis() % 100000}",
        autoExpire: Boolean = false,
        onTick: ((Int) -> Unit)? = null,
        onExpired: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        if (_state.value.status == CountdownStatus.RUNNING) {
            Log.w(TAG, "[$incidentId] Countdown already running (${_state.value.remainingSeconds}s remaining), ignoring duplicate start request.")
            return
        }

        this.onTickCallback = onTick
        this.onExpiredCallback = onExpired
        this.onCancelledCallback = onCancelled

        val initialDurationSecs = if (autoExpire) 1 else DURATION_SECONDS

        _state.value = CountdownState(
            status = CountdownStatus.RUNNING,
            remainingSeconds = initialDurationSecs,
            incidentId = incidentId
        )

        Log.i(TAG, "[$incidentId] Authoritative $initialDurationSecs-second countdown started")

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(initialDurationSecs * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = ((millisUntilFinished + 500) / 1000).toInt().coerceIn(0, DURATION_SECONDS)
                if (_state.value.status != CountdownStatus.RUNNING) return

                _state.value = _state.value.copy(remainingSeconds = secs)
                Log.d(TAG, "[$incidentId] Countdown tick: $secs seconds remaining")
                onTickCallback?.invoke(secs)
            }

            override fun onFinish() {
                if (_state.value.status != CountdownStatus.RUNNING) return

                _state.value = _state.value.copy(remainingSeconds = 0, status = CountdownStatus.EXPIRED)
                Log.i(TAG, "[$incidentId] Countdown expired (0s). Authoritative transition to verification.")
                onExpiredCallback?.invoke()
            }
        }.start()
    }

    /**
     * Authoritative cancellation of the countdown ("I'M OKAY").
     * Stops timer, resets decision engine, logs record, and prevents emergency actions.
     */
    @Synchronized
    fun cancelCountdown(context: Context, source: String = "USER_ACTION"): Boolean {
        if (_state.value.status != CountdownStatus.RUNNING && _state.value.status != CountdownStatus.EXPIRED) {
            Log.w(TAG, "cancelCountdown called from $source but status is ${_state.value.status}")
            return false
        }

        val incidentId = if (_state.value.incidentId.isNotBlank()) _state.value.incidentId else "RKS-${System.currentTimeMillis() % 100000}"
        countDownTimer?.cancel()
        countDownTimer = null

        _state.value = _state.value.copy(status = CountdownStatus.CANCELLED)
        Log.i(TAG, "[$incidentId] [03] RIDER_RESPONSE Rider confirmed safety via $source. Cancelling emergency workflow.")

        // 1. Authoritative reset of incident decision engine
        IncidentDecisionEngine.resetToNormal()

        // 2. Persist cancellation record
        try {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            IncidentRepository.addRecord(
                context.applicationContext,
                IncidentRecord(
                    id = incidentId,
                    timestampMs = now,
                    title = "Movement Anomaly — Resolved",
                    status = "RESOLVED BY RIDER",
                    severity = "LOW",
                    aiReasoning = "Incident safety check initiated. Rider confirmed safety via 'I'M OKAY' ($source). Alert cancelled with zero emergency actions taken.",
                    locationUrl = null,
                    timeline = listOf(
                        sdf.format(java.util.Date(now - 10000)) to "Unusual movement pattern detected",
                        sdf.format(java.util.Date(now - 8000)) to "Safety countdown requested",
                        sdf.format(java.util.Date(now)) to "Rider tapped I'M OKAY ($source) — Incident cancelled"
                    )
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error adding cancellation record to IncidentRepository: ${e.message}")
        }

        onCancelledCallback?.invoke()
        return true
    }

    /**
     * Reset countdown state to IDLE (e.g. after verification completes or activity finishes).
     */
    @Synchronized
    fun resetToIdle() {
        countDownTimer?.cancel()
        countDownTimer = null
        onTickCallback = null
        onExpiredCallback = null
        onCancelledCallback = null
        _state.value = CountdownState(status = CountdownStatus.IDLE)
        Log.i(TAG, "IncidentCountdownManager reset to IDLE")
    }
}
