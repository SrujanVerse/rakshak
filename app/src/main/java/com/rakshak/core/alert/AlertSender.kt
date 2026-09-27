package com.rakshak.core.alert

import kotlinx.coroutines.Deferred

enum class AlertMode {
    REAL_MODE,
    DEMO_MODE
}

enum class AlertResult {
    ALERT_SENT,
    ALERT_FAILED_NO_PERMISSION,
    ALERT_FAILED_NO_SERVICE,
    ALERT_FAILED_NO_CONTACTS,
    ALERT_FAILED_UNKNOWN
}

data class LocationData(val latitude: Double, val longitude: Double)

interface SmsController {
    fun hasPermission(): Boolean
    fun hasService(): Boolean
    fun sendSms(destinationAddress: String, text: String): Boolean
}

interface LocationController {
    // Starts location fetch and returns a Deferred. 
    // If it can resolve cached location immediately, it returns a completed Deferred.
    fun getLocationAsync(): Deferred<LocationData?>
}

class AlertSender(
    private val mode: AlertMode,
    private val smsController: SmsController,
    private val locationController: LocationController,
    private val emergencyContacts: List<String>
) {
    val demoLogs = mutableListOf<DemoLog>()

    data class DemoLog(
        val recipient: String,
        val messageBody: String,
        val locationStatus: String,
        val alertResult: AlertResult
    )

    fun sendEmergencyAlert(): AlertResult {
        if (emergencyContacts.isEmpty()) {
            return AlertResult.ALERT_FAILED_NO_CONTACTS
        }

        if (mode == AlertMode.REAL_MODE) {
            if (!smsController.hasPermission()) {
                return AlertResult.ALERT_FAILED_NO_PERMISSION
            }
            if (!smsController.hasService()) {
                return AlertResult.ALERT_FAILED_NO_SERVICE
            }
        }

        // 1. Start or retrieve the location fetch
        val locationDeferred = locationController.getLocationAsync()
        
        // 2. Use it ONLY if it is immediately available (non-blocking)
        val location = if (locationDeferred.isCompleted) {
            try {
                locationDeferred.getCompleted()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        
        val locationStatus = if (location != null) "available" else "unavailable"
        
        val locationText = if (location != null) {
            "Location: " + location.latitude + ", " + location.longitude
        } else {
            "Location unavailable"
        }
        
        val message = "SOS! A crash has been detected. " + locationText

        // 3. Demo mode logging
        if (mode == AlertMode.DEMO_MODE) {
            logDemo(AlertResult.ALERT_SENT, locationStatus, message)
            return AlertResult.ALERT_SENT
        }

        // 4. Send SMS immediately
        var successCount = 0
        for (contact in emergencyContacts) {
            val sent = smsController.sendSms(contact, message)
            if (sent) successCount++
        }

        return if (successCount > 0) AlertResult.ALERT_SENT else AlertResult.ALERT_FAILED_UNKNOWN
    }
    
    private fun logDemo(result: AlertResult, locationStatus: String, messageBody: String) {
        for (contact in emergencyContacts) {
            demoLogs.add(DemoLog(contact, messageBody, locationStatus, result))
        }
    }
}
