package com.rakshak.core.readiness

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.rakshak.core.log.IncidentLogIntegrityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ReadinessChecker — evaluates the live readiness of all six P0 system components.
 *
 * Checks are stateless pure functions so they can be re-evaluated at any time
 * without side effects. The [refresh] function rebuilds the full list and emits
 * it via [statusFlow].
 *
 * Components checked:
 *  1. Accelerometer sensor presence
 *  2. Gyroscope sensor presence
 *  3. ACCESS_FINE_LOCATION permission
 *  4. SEND_SMS permission
 *  5. Cellular service availability (SIM state)
 *  6. Incident log integrity
 *
 * Architecture note: This class is intentionally NOT a ViewModel — it is a pure
 * logic class owned by the ViewModel. This makes it unit-testable without Robolectric.
 */
class ReadinessChecker(
    private val context: Context,
    private val logIntegrityChecker: IncidentLogIntegrityChecker = IncidentLogIntegrityChecker(context),
) {

    private val _statusFlow = MutableStateFlow<List<ReadinessStatus>>(emptyList())
    val statusFlow: StateFlow<List<ReadinessStatus>> = _statusFlow.asStateFlow()

    /**
     * Evaluate all six readiness checks and emit the result.
     * Safe to call from any coroutine context — no blocking I/O.
     */
    fun refresh() {
        val results = listOf(
            checkAccelerometer(),
            checkGyroscope(),
            checkLocationPermission(),
            checkSmsPermission(),
            checkCellularService(),
            checkLogIntegrity(),
        )
        _statusFlow.value = results
    }

    // ── Individual checks ────────────────────────────────────────────────────

    private fun checkAccelerometer(): ReadinessStatus {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val present = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        return ReadinessStatus(
            id = "accelerometer",
            label = "Accelerometer",
            state = if (present) ReadinessState.OK else ReadinessState.WARNING,
            detail = if (present) "" else "Hardware not found — crash detection disabled",
            isFixable = false,
        )
    }

    private fun checkGyroscope(): ReadinessStatus {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val present = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        return ReadinessStatus(
            id = "gyroscope",
            label = "Gyroscope",
            state = if (present) ReadinessState.OK else ReadinessState.WARNING,
            detail = if (present) "" else "Hardware not found — orientation detection degraded",
            isFixable = false,
        )
    }

    private fun checkLocationPermission(): ReadinessStatus {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return ReadinessStatus(
            id = "location_permission",
            label = "Location Permission",
            state = if (granted) ReadinessState.OK else ReadinessState.WARNING,
            detail = if (granted) "" else "Tap FIX to grant — GPS coordinates won't be sent without this",
            isFixable = !granted,
        )
    }

    private fun checkSmsPermission(): ReadinessStatus {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return ReadinessStatus(
            id = "sms_permission",
            label = "SMS Permission",
            state = if (granted) ReadinessState.OK else ReadinessState.WARNING,
            detail = if (granted) "" else "Tap FIX to grant — emergency alerts CANNOT be sent without this",
            isFixable = !granted,
        )
    }

    private fun checkCellularService(): ReadinessStatus {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simState = tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
            val ready = simState == TelephonyManager.SIM_STATE_READY
            ReadinessStatus(
                id = "cellular",
                label = "Cellular Service",
                state = if (ready) ReadinessState.OK else ReadinessState.WARNING,
                detail = if (ready) "" else "SIM not ready (state=$simState) — SMS may fail",
                isFixable = false,
            )
        } catch (e: Exception) {
            // TelephonyManager unavailable (e.g. tablet with no SIM) — warn but don't crash
            ReadinessStatus(
                id = "cellular",
                label = "Cellular Service",
                state = ReadinessState.WARNING,
                detail = "Cannot read SIM state: ${e.message}",
                isFixable = false,
            )
        }
    }

    private fun checkLogIntegrity(): ReadinessStatus {
        val result = logIntegrityChecker.check()
        return ReadinessStatus(
            id = "log_integrity",
            label = "Incident Log",
            state = if (result.isIntact) ReadinessState.OK else ReadinessState.WARNING,
            detail = result.detail,
            isFixable = !result.isIntact,
        )
    }
}
