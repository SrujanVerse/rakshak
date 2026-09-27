package com.rakshak.core.detector

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IncidentDecisionState — multi-stage user-facing monitoring state.
 *
 * State progression (never skips stages):
 *   NORMAL → MINOR_JERK → UNUSUAL_MOVEMENT → POSSIBLE_IMPACT → POSSIBLE_INCIDENT → CONFIRMED_INCIDENT
 *
 * A small left-right phone movement MUST remain NORMAL or MINOR_JERK.
 * Only sustained, multi-signal evidence over time can escalate to CONFIRMED_INCIDENT.
 */
enum class IncidentDecisionState {
    /** Normal movement — monitoring active, no action needed */
    NORMAL,

    /** Brief acceleration spike detected — auto-decays to NORMAL within 1.5s */
    MINOR_JERK,

    /** Sustained unusual movement pattern — monitoring closely */
    UNUSUAL_MOVEMENT,

    /** Significant impact force detected — looking for post-impact pattern */
    POSSIBLE_IMPACT,

    /** Multiple independent signals confirm possible incident — needs verification */
    POSSIBLE_INCIDENT,

    /** Strong multi-signal evidence confirmed — emergency countdown required */
    CONFIRMED_INCIDENT
}

/**
 * IncidentDecisionEngine — deterministic multi-stage temporal gate for incident classification.
 *
 * Architecture Rules:
 *  1. States progress sequentially — NEVER jump from NORMAL directly to CONFIRMED_INCIDENT.
 *  2. Each stage requires TEMPORAL PERSISTENCE — a single spike cannot escalate.
 *  3. TFLite probability alone (even 0.98) CANNOT trigger CONFIRMED_INCIDENT.
 *  4. Peak acceleration alone (even 40 m/s²) CANNOT trigger CONFIRMED_INCIDENT.
 *  5. CONFIRMED_INCIDENT requires: high force + high rotation + high ML confidence + temporal pattern.
 *  6. States auto-decay toward NORMAL when signals subside.
 *
 * Uses INSTANTANEOUS peak values (from the latest ~500ms inference window),
 * NOT the 4-second ring buffer maximum.
 */
object IncidentDecisionEngine {

    private const val TAG = "IncidentDecisionEngine"

    private val _decisionState = MutableStateFlow(IncidentDecisionState.NORMAL)
    val decisionState: StateFlow<IncidentDecisionState> = _decisionState.asStateFlow()

    private val _userStatusText = MutableStateFlow("Monitoring your movement...")
    val userStatusText: StateFlow<String> = _userStatusText.asStateFlow()

    // ── Temporal tracking ─────────────────────────────────────────────────

    /** Timestamp when the current non-NORMAL state was entered */
    private var stateEnteredMs: Long = 0L

    /** Timestamp of last evaluation that kept the state elevated */
    private var lastElevatedMs: Long = 0L

    /** Count of consecutive elevated evaluations (each ~500ms) */
    private var consecutiveElevatedCount: Int = 0

    /** Peak acceleration seen during the current elevated episode */
    private var episodePeakAccel: Float = 0f

    /** Peak gyroscope seen during the current elevated episode */
    private var episodePeakGyro: Float = 0f

    /** Peak crash probability seen during the current elevated episode */
    private var episodePeakCrashProb: Float = 0f

    /** Number of times FSM reached CONFIRMING or CONFIRMED in this episode */
    private var fsmConfirmingCount: Int = 0

    // ── Thresholds ────────────────────────────────────────────────────────

    // MINOR_JERK: brief spike — auto-decays in 1.5s
    private const val MINOR_JERK_ACCEL = 18.0f       // m/s² (~1.8G, includes gravity)
    private const val MINOR_JERK_CRASH_PROB = 0.60f

    // UNUSUAL_MOVEMENT: sustained elevated signals for ≥2 consecutive evaluations
    private const val UNUSUAL_ACCEL = 24.0f           // m/s² (~2.4G)
    private const val UNUSUAL_CRASH_PROB = 0.75f
    private const val UNUSUAL_CONSECUTIVE_MIN = 2

    // POSSIBLE_IMPACT: strong force + rotation
    private const val IMPACT_ACCEL = 30.0f            // m/s² (~3G, same as CrashDetector threshold)
    private const val IMPACT_GYRO = 5.0f              // rad/s (significant rotation)
    private const val IMPACT_CRASH_PROB = 0.80f

    // POSSIBLE_INCIDENT: impact confirmed + post-event temporal pattern
    private const val INCIDENT_ACCEL = 32.0f          // m/s²
    private const val INCIDENT_CRASH_PROB = 0.85f
    private const val INCIDENT_CONSECUTIVE_MIN = 3    // ≥1.5s of sustained high signals
    private const val INCIDENT_GYRO = 4.0f            // rad/s

    // CONFIRMED_INCIDENT: requires EITHER FSM CONFIRMED or all of: force + rotation + ML + temporal
    private const val CONFIRMED_ACCEL = 35.0f         // m/s² (~3.5G)
    private const val CONFIRMED_CRASH_PROB = 0.90f
    private const val CONFIRMED_GYRO = 6.0f           // rad/s
    private const val CONFIRMED_CONSECUTIVE_MIN = 4   // ≥2s sustained

    // Auto-decay timings (milliseconds)
    private const val MINOR_JERK_DECAY_MS = 1500L
    private const val UNUSUAL_DECAY_MS = 2500L
    private const val IMPACT_DECAY_MS = 3000L
    private const val INCIDENT_DECAY_MS = 4000L

    /**
     * Evaluate the current sensor state and determine the incident decision.
     *
     * IMPORTANT: [recentPeakAccel] and [recentPeakGyro] should be computed from
     * the RECENT inference window (~500ms), NOT the entire 4-second buffer.
     */
    @Synchronized
    fun evaluate(
        crashProbability: Float,
        recentPeakAccel: Float,
        recentPeakGyro: Float,
        fsmState: DetectorState
    ): IncidentDecisionState {
        val now = System.currentTimeMillis()
        val currentState = _decisionState.value

        // Track FSM states
        if (fsmState == DetectorState.CONFIRMING || fsmState == DetectorState.CONFIRMED) {
            fsmConfirmingCount++
        }

        // ── Check for auto-decay first ──────────────────────────────────
        val timeSinceElevated = now - lastElevatedMs
        val shouldDecay = when (currentState) {
            IncidentDecisionState.MINOR_JERK -> timeSinceElevated > MINOR_JERK_DECAY_MS
            IncidentDecisionState.UNUSUAL_MOVEMENT -> timeSinceElevated > UNUSUAL_DECAY_MS
            IncidentDecisionState.POSSIBLE_IMPACT -> timeSinceElevated > IMPACT_DECAY_MS
            IncidentDecisionState.POSSIBLE_INCIDENT -> timeSinceElevated > INCIDENT_DECAY_MS
            else -> false
        }

        if (shouldDecay && currentState != IncidentDecisionState.NORMAL && currentState != IncidentDecisionState.CONFIRMED_INCIDENT) {
            Log.i(TAG, "[RAKSHAK_AUTO_DECAY] $currentState → NORMAL after ${timeSinceElevated}ms quiet")
            resetTracking()
            _decisionState.value = IncidentDecisionState.NORMAL
            _userStatusText.value = "✓ Normal movement — RAKSHAK is monitoring"
            return IncidentDecisionState.NORMAL
        }

        // ── Determine the INSTANTANEOUS signal level ────────────────────

        // Check if current signals warrant staying elevated or escalating
        val isCurrentlyElevated = recentPeakAccel >= MINOR_JERK_ACCEL ||
                crashProbability >= MINOR_JERK_CRASH_PROB ||
                fsmState != DetectorState.MONITORING

        if (isCurrentlyElevated) {
            lastElevatedMs = now
            consecutiveElevatedCount++

            // Track episode peaks
            if (recentPeakAccel > episodePeakAccel) episodePeakAccel = recentPeakAccel
            if (recentPeakGyro > episodePeakGyro) episodePeakGyro = recentPeakGyro
            if (crashProbability > episodePeakCrashProb) episodePeakCrashProb = crashProbability
        } else {
            // Signals subsided — decrement consecutive count (don't reset immediately)
            if (consecutiveElevatedCount > 0) consecutiveElevatedCount--
            return currentState // Let auto-decay handle the transition
        }

        // ── Evaluate potential state transitions (sequential only) ──────

        val newState = evaluateEscalation(
            recentPeakAccel, recentPeakGyro, crashProbability, fsmState, currentState
        )

        if (newState != currentState) {
            if (newState.ordinal > currentState.ordinal) {
                // Escalation
                Log.w(TAG, "[RAKSHAK_ESCALATION] $currentState → $newState | " +
                        "Accel=%.1f Gyro=%.1f CrashProb=%.2f FSM=$fsmState " +
                        "ConsecutiveEval=$consecutiveElevatedCount " +
                        "EpPeakAccel=%.1f EpPeakGyro=%.1f EpPeakProb=%.2f"
                    .format(recentPeakAccel, recentPeakGyro, crashProbability,
                        consecutiveElevatedCount.toFloat(),
                        episodePeakAccel, episodePeakGyro, episodePeakCrashProb))
            }
            _decisionState.value = newState
            _userStatusText.value = stateToUserText(newState)

            if (newState.ordinal > currentState.ordinal && newState != IncidentDecisionState.MINOR_JERK) {
                stateEnteredMs = now
            }
        }

        return _decisionState.value
    }

    /**
     * Determine whether to escalate to a higher state.
     * Key rule: can only go UP by ONE level at a time.
     */
    private fun evaluateEscalation(
        recentAccel: Float,
        recentGyro: Float,
        crashProb: Float,
        fsmState: DetectorState,
        currentState: IncidentDecisionState
    ): IncidentDecisionState {

        // ── CONFIRMED_INCIDENT gate ─────────────────────────────────────
        // Can ONLY be reached from POSSIBLE_INCIDENT
        if (currentState == IncidentDecisionState.POSSIBLE_INCIDENT) {
            val fsmConfirmed = fsmState == DetectorState.CONFIRMED

            // Path A: CrashDetector FSM independently confirmed WITH strong angular rotation AND high ML confidence
            // A hand shake lacks high rotational velocity (>=5.0 rad/s) and sustained vehicle impact signature
            val pathA = fsmConfirmed && episodePeakCrashProb >= 0.85f && episodePeakGyro >= 5.0f

            // Path B: All signals independently strong + temporal persistence
            val pathB = episodePeakAccel >= CONFIRMED_ACCEL &&
                    episodePeakGyro >= CONFIRMED_GYRO &&
                    episodePeakCrashProb >= CONFIRMED_CRASH_PROB &&
                    consecutiveElevatedCount >= CONFIRMED_CONSECUTIVE_MIN

            if (pathA || pathB) {
                return IncidentDecisionState.CONFIRMED_INCIDENT
            }
        }

        // ── POSSIBLE_INCIDENT gate ──────────────────────────────────────
        // Can ONLY be reached from POSSIBLE_IMPACT
        if (currentState == IncidentDecisionState.POSSIBLE_IMPACT) {
            val hasSustainedSignals = consecutiveElevatedCount >= INCIDENT_CONSECUTIVE_MIN
            val hasStrongEvidence = episodePeakAccel >= INCIDENT_ACCEL &&
                    episodePeakCrashProb >= INCIDENT_CRASH_PROB &&
                    episodePeakGyro >= INCIDENT_GYRO

            val fsmAdvanced = fsmState == DetectorState.CONFIRMING || fsmState == DetectorState.CONFIRMED

            if (hasSustainedSignals && (hasStrongEvidence || fsmAdvanced)) {
                return IncidentDecisionState.POSSIBLE_INCIDENT
            }
        }

        // ── POSSIBLE_IMPACT gate ────────────────────────────────────────
        // Can ONLY be reached from UNUSUAL_MOVEMENT
        if (currentState == IncidentDecisionState.UNUSUAL_MOVEMENT) {
            val hasImpact = recentAccel >= IMPACT_ACCEL || episodePeakAccel >= IMPACT_ACCEL
            val hasRotation = recentGyro >= IMPACT_GYRO || episodePeakGyro >= IMPACT_GYRO
            val hasMLSignal = crashProb >= IMPACT_CRASH_PROB || episodePeakCrashProb >= IMPACT_CRASH_PROB
            val fsmDetected = fsmState == DetectorState.IMPACT_CANDIDATE ||
                    fsmState == DetectorState.CONFIRMING ||
                    fsmState == DetectorState.CONFIRMED

            // Require at least 2 of: impact force, rotation, ML signal, FSM detection
            val signalCount = listOf(hasImpact, hasRotation, hasMLSignal, fsmDetected).count { it }
            if (signalCount >= 2) {
                return IncidentDecisionState.POSSIBLE_IMPACT
            }
        }

        // ── UNUSUAL_MOVEMENT gate ───────────────────────────────────────
        // Can ONLY be reached from MINOR_JERK
        if (currentState == IncidentDecisionState.MINOR_JERK) {
            val hasSustained = consecutiveElevatedCount >= UNUSUAL_CONSECUTIVE_MIN
            val hasUnusualSignal = recentAccel >= UNUSUAL_ACCEL ||
                    crashProb >= UNUSUAL_CRASH_PROB ||
                    fsmState == DetectorState.IMPACT_CANDIDATE

            if (hasSustained && hasUnusualSignal) {
                return IncidentDecisionState.UNUSUAL_MOVEMENT
            }
        }

        // ── MINOR_JERK gate ─────────────────────────────────────────────
        // Can be reached from NORMAL
        if (currentState == IncidentDecisionState.NORMAL) {
            val hasMinorSignal = recentAccel >= MINOR_JERK_ACCEL ||
                    crashProb >= MINOR_JERK_CRASH_PROB

            if (hasMinorSignal) {
                stateEnteredMs = System.currentTimeMillis()
                return IncidentDecisionState.MINOR_JERK
            }
        }

        return currentState
    }

    private fun stateToUserText(state: IncidentDecisionState): String {
        return when (state) {
            IncidentDecisionState.NORMAL ->
                "✓ Normal movement — RAKSHAK is monitoring"
            IncidentDecisionState.MINOR_JERK ->
                "Brief movement detected — monitoring..."
            IncidentDecisionState.UNUSUAL_MOVEMENT ->
                "Unusual movement pattern — RAKSHAK is checking..."
            IncidentDecisionState.POSSIBLE_IMPACT ->
                "⚠ Significant impact detected — analyzing..."
            IncidentDecisionState.POSSIBLE_INCIDENT ->
                "⚠️ Possible incident — gathering evidence..."
            IncidentDecisionState.CONFIRMED_INCIDENT ->
                "🚨 Incident confirmed — checking your safety!"
        }
    }

    /** Reset state back to NORMAL (e.g., after user taps I'M GOOD) */
    @Synchronized
    fun resetToNormal() {
        Log.i(TAG, "[RAKSHAK_RESET_NORMAL] Decision state reset to NORMAL by user action.")
        resetTracking()
        _decisionState.value = IncidentDecisionState.NORMAL
        _userStatusText.value = "✓ Normal movement — RAKSHAK is monitoring"
    }

    /**
     * Snapshot of the detection episode's accumulated sensor data.
     * Used by EmergencyCountdownActivity to construct IncidentData with REAL values.
     */
    data class EpisodeData(
        val peakAccel: Float,
        val peakGyro: Float,
        val peakCrashProb: Float,
        val durationMs: Long,
        val fsmState: String
    )

    /** Get a snapshot of the current episode's accumulated sensor data. */
    @Synchronized
    fun getEpisodeData(): EpisodeData {
        val duration = if (stateEnteredMs > 0) System.currentTimeMillis() - stateEnteredMs else 0L
        return EpisodeData(
            peakAccel = episodePeakAccel,
            peakGyro = episodePeakGyro,
            peakCrashProb = episodePeakCrashProb,
            durationMs = duration,
            fsmState = _decisionState.value.name
        )
    }

    private fun resetTracking() {
        stateEnteredMs = 0L
        lastElevatedMs = 0L
        consecutiveElevatedCount = 0
        episodePeakAccel = 0f
        episodePeakGyro = 0f
        episodePeakCrashProb = 0f
        fsmConfirmingCount = 0
    }
}
