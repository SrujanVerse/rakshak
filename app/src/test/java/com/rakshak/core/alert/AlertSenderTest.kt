package com.rakshak.core.alert

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSenderTest {

    class MockSmsController : SmsController {
        var permission = true
        var service = true
        var sendCount = 0
        val messagesSent = mutableListOf<String>()

        override fun hasPermission(): Boolean = permission
        override fun hasService(): Boolean = service
        override fun sendSms(destinationAddress: String, text: String): Boolean {
            sendCount++
            messagesSent.add(text)
            return true
        }
    }

    class MockLocationController : LocationController {
        var deferred = CompletableDeferred<LocationData?>()
        
        override fun getLocationAsync(): Deferred<LocationData?> {
            return deferred
        }
    }

    @Test
    fun testRealModeSendsSmsWithExactMessageBodyWhenLocationImmediate() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        // Provide immediate location
        loc.deferred.complete(LocationData(37.422, -122.084))
        
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location: 37.422, -122.084", sms.messagesSent[0])
    }

    @Test
    fun testDemoModeNeverInvokesRealSmsManager() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(LocationData(37.422, -122.084))
        
        val sender = AlertSender(AlertMode.DEMO_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(0, sms.sendCount) // NEVER INVOKED

        assertEquals(1, sender.demoLogs.size)
        assertEquals("1234567890", sender.demoLogs[0].recipient)
        assertEquals("available", sender.demoLogs[0].locationStatus)
        assertEquals("SOS! A crash has been detected. Location: 37.422, -122.084", sender.demoLogs[0].messageBody)
    }

    @Test
    fun testDemoModeWithPermissionDeniedStillReturnsAlertSent() {
        val sms = MockSmsController().apply { permission = false }
        val loc = MockLocationController()
        loc.deferred.complete(LocationData(37.422, -122.084))
        
        // Demo mode ignores the missing permission
        val senderDemo = AlertSender(AlertMode.DEMO_MODE, sms, loc, listOf("1234567890"))
        assertEquals(AlertResult.ALERT_SENT, senderDemo.sendEmergencyAlert())
        assertEquals(1, senderDemo.demoLogs.size)
        assertEquals("SOS! A crash has been detected. Location: 37.422, -122.084", senderDemo.demoLogs[0].messageBody)
    }

    @Test
    fun testRealModeMissingPermissionFailsGracefully() {
        val sms = MockSmsController().apply { permission = false }
        val loc = MockLocationController()
        loc.deferred.complete(LocationData(37.422, -122.084))
        
        // Real mode fails
        val senderReal = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))
        assertEquals(AlertResult.ALERT_FAILED_NO_PERMISSION, senderReal.sendEmergencyAlert())
        assertEquals(0, sms.sendCount)
    }

    @Test
    fun testUnavailableLocationSendsSmsWithFallbackText() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        // Provide immediate NULL location
        loc.deferred.complete(null)
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location unavailable", sms.messagesSent[0])
    }

    @Test
    fun testUnavailableCellularServiceFailsGracefully() {
        val sms = MockSmsController().apply { service = false }
        val loc = MockLocationController()
        loc.deferred.complete(null)
        
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_FAILED_NO_SERVICE, result)
        assertEquals(0, sms.sendCount)
    }

    @Test
    fun testRepeatedCallsDoNotCrash() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(null)
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        assertEquals(AlertResult.ALERT_SENT, sender.sendEmergencyAlert())
        assertEquals(AlertResult.ALERT_SENT, sender.sendEmergencyAlert())
        assertEquals(AlertResult.ALERT_SENT, sender.sendEmergencyAlert())
        assertEquals(3, sms.sendCount)
    }

    @Test
    fun testSlowLocationControllerMustNotDelaySms() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        // DO NOT COMPLETE the deferred location! It is simulating a "slow" network/GPS call.
        
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        // Execution happens instantly because AlertSender doesn't block on incomplete Deferred.
        val result = sender.sendEmergencyAlert()
        
        // Result is sent, SMS count is 1.
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location unavailable", sms.messagesSent[0])
        
        // Even if location finishes LATER, SMS was already sent promptly.
        loc.deferred.complete(LocationData(37.422, -122.084))
        
        // Prove it actually didn't block and SMS was sent first.
        assertTrue(sms.sendCount == 1)
    }

    @Test
    fun testMultipleEmergencyContactsBehaveCorrectly() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(null)
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("111", "222", "333"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(3, sms.sendCount)
    }

    @Test
    fun testEmptyEmergencyContactsIsHandledSafely() {
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(null)
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, emptyList())

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_FAILED_NO_CONTACTS, result)
        assertEquals(0, sms.sendCount)
    }
}
