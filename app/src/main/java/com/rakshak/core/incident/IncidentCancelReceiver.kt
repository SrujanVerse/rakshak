package com.rakshak.core.incident

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * IncidentCancelReceiver — BroadcastReceiver for the notification "I'M OKAY" action button.
 *
 * Triggered directly from the Android system notification action, even if
 * EmergencyCountdownActivity is not open, locked, or in the background.
 */
class IncidentCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i(TAG, "[RAKSHAK_NOTIFICATION_CANCEL] 'I'M OKAY' tapped from Notification action button")

        // Call single authoritative cancellation path
        val cancelled = IncidentCountdownManager.cancelCountdown(context, "NOTIFICATION_ACTION")

        if (cancelled) {
            // Revert notification to normal monitoring persistent notification via SensorService
            try {
                val serviceIntent = Intent(context, com.rakshak.core.sensor.SensorService::class.java).apply {
                    action = com.rakshak.core.sensor.SensorService.ACTION_REVERT_NORMAL_NOTIFICATION
                }
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send revert action to SensorService: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "IncidentCancelReceiver"
    }
}
