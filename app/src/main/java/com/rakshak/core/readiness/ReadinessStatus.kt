package com.rakshak.core.readiness

/**
 * ReadinessStatus — models the live readiness state of one system component.
 *
 * @property id         Stable identifier used as a list key (e.g. "accelerometer").
 * @property label      Human-readable label shown in the UI.
 * @property state      Current [ReadinessState].
 * @property detail     Optional detail string shown below the label (e.g. error message).
 * @property isFixable  True when a [FIX] button should be shown (e.g. permission denied).
 */
data class ReadinessStatus(
    val id: String,
    val label: String,
    val state: ReadinessState = ReadinessState.CHECKING,
    val detail: String = "",
    val isFixable: Boolean = false,
)

/**
 * ReadinessState — three possible states for each readiness item.
 *
 * CHECKING — initial / polling state (spinner shown).
 * OK       — component is ready (green checkmark shown).
 * WARNING  — component is unavailable or degraded (yellow/red warning icon shown).
 */
enum class ReadinessState {
    CHECKING,
    OK,
    WARNING,
}
