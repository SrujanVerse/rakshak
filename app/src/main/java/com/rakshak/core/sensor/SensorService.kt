package com.rakshak.core.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rakshak.R
import com.rakshak.core.classifier.SensorPreprocessor
import com.rakshak.core.classifier.TFLiteCrashClassifier
import com.rakshak.core.detector.CrashDetector
import com.rakshak.core.detector.DetectorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service that continuously reads Accelerometer and Gyroscope
 * at SENSOR_DELAY_GAME (~50Hz) and executes real-time TFLite crash classification.
 */
class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // 4 seconds rolling window (nanoseconds), max capacity 1000 items
    val accelerometerBuffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 1000)
    val gyroscopeBuffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 1000)

    private var isListening = false

    // Real-time classification & safety FSM components
    private lateinit var classifier: TFLiteCrashClassifier
    private val crashDetector = CrashDetector()

    private var accelSampleCount = 0L
    private var gyroSampleCount = 0L
    private var lastInferenceTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Initialize and load TFLite classifier safely (handles missing native libraries in test environments)
        try {
            classifier = TFLiteCrashClassifier(applicationContext)
            val loaded = classifier.loadModel()
            Log.i(TAG, "TFLiteCrashClassifier initialized, modelLoaded=$loaded")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not initialize TFLiteCrashClassifier: ${t.message}")
        }

        // Observe IncidentDecisionEngine state transitions for user emergency trigger
        CoroutineScope(Dispatchers.Main).launch {
            com.rakshak.core.detector.IncidentDecisionEngine.decisionState.collect { decision ->
                if (decision == com.rakshak.core.detector.IncidentDecisionState.CONFIRMED_INCIDENT) {
                    Log.w(TAG, "[RAKSHAK_INCIDENT_CONFIRMED] Multi-signal gate CONFIRMED. Launching EmergencyCountdownActivity.")
                    val emergencyIntent = Intent(applicationContext, com.rakshak.ui.emergency.EmergencyCountdownActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(emergencyIntent)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSpecialUse()
        
        if (!isListening) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            } else {
                Log.w(TAG, "Accelerometer not available on this device!")
            }

            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            } else {
                Log.w(TAG, "Gyroscope not available on this device!")
            }
            isListening = true
        }

        return START_STICKY
    }

    private fun startForegroundSpecialUse() {
        val channelId = "rakshak_sensor_channel"
        val channelName = "Crash Detection Sensor"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps crash detection sensors running"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rakshak Crash Detection")
            .setContentText("Monitoring sensors & running TFLite inference...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerBuffer.add(event.timestamp, event.values)
                accelSampleCount++
                if (accelSampleCount % 100 == 0L) {
                    Log.d(TAG, "Sensor samples received: accel=$accelSampleCount, gyro=$gyroSampleCount")
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeBuffer.add(event.timestamp, event.values)
                gyroSampleCount++
            }
        }

        // 1. Feed continuous samples into CrashDetector (FSM safety logic)
        val accelSamples = accelerometerBuffer.flow.value
        if (accelSamples.size >= 2) {
            crashDetector.processAccelerometerBuffer(accelSamples)
        }

        // 2. Real-Time TFLite Inference loop (every 500ms window check)
        val now = System.currentTimeMillis()
        if (now - lastInferenceTimeMs >= INFERENCE_INTERVAL_MS) {
            lastInferenceTimeMs = now
            runRealtimeInference(accelSamples)
        }
    }

    private fun runRealtimeInference(accelSamples: List<SensorData>) {
        val gyroSamples = gyroscopeBuffer.flow.value
        if (accelSamples.size < 10 || gyroSamples.size < 10) return

        // Create 2-second window (100x6 normalized float array)
        val preprocessedInput = SensorPreprocessor.preprocess(accelSamples, gyroSamples)
        if (preprocessedInput == null) {
            Log.d(TAG, "Window creation skipped: insufficient aligned samples")
            return
        }

        Log.d(
            TAG,
            "Window created: accelSamples=${accelSamples.size}, gyroSamples=${gyroSamples.size}, inputShape=[1,100,6]"
        )

        // CRITICAL FIX: Compute peak values from RECENT samples only (~1 second window)
        // instead of the entire 4-second ring buffer. This prevents stale acceleration
        // spikes from persisting and causing false positives in the decision engine.
        val recentWindowNanos = 1_000_000_000L // 1 second
        val recentAccel = filterRecentSamples(accelSamples, recentWindowNanos)
        val recentGyro = filterRecentSamples(gyroSamples, recentWindowNanos)

        val (recentPeakAccel, recentPeakGyro) = SensorPreprocessor.computePeakValues(
            if (recentAccel.size >= 2) recentAccel else accelSamples,
            if (recentGyro.size >= 2) recentGyro else gyroSamples
        )

        // Execute TFLite inference
        val crashProb = if (::classifier.isInitialized && classifier.isReady) {
            val result = classifier.classify(preprocessedInput, SensorPreprocessor.WINDOW_SIZE)
            if (result != null) {
                val normalProb = 1.0f - result.crashProbability
                val prob = result.crashProbability
                Log.i(
                    TAG,
                    "TFLite inference: normalProb=%.4f, crashProb=%.4f, recentPeakAccel=%.2f m/s², recentPeakGyro=%.2f rad/s, FSM=${crashDetector.currentState}"
                        .format(normalProb, prob, recentPeakAccel, recentPeakGyro)
                )
                prob
            } else 0.0f
        } else 0.0f

        // Evaluate continuous incident decision engine with RECENT peak values
        com.rakshak.core.detector.IncidentDecisionEngine.evaluate(
            crashProbability = crashProb,
            recentPeakAccel = recentPeakAccel,
            recentPeakGyro = recentPeakGyro,
            fsmState = crashDetector.currentState
        )
    }

    /**
     * Filter sensor samples to only include those within the most recent time window.
     * This prevents stale spikes in the 4-second ring buffer from persisting as "peak" values.
     */
    private fun filterRecentSamples(samples: List<SensorData>, windowNanos: Long): List<SensorData> {
        if (samples.isEmpty()) return samples
        val latestTimestamp = samples.maxOf { it.timestamp }
        val cutoff = latestTimestamp - windowNanos
        return samples.filter { it.timestamp >= cutoff }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for crash detection
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        isListening = false
        if (::classifier.isInitialized) {
            classifier.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SensorService"
        private const val NOTIFICATION_ID = 1001
        private const val INFERENCE_INTERVAL_MS = 500L
        private const val CRASH_THRESHOLD = 0.85f
    }
}
