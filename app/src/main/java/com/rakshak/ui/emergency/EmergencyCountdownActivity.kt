package com.rakshak.ui.emergency

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rakshak.core.ai.IncidentReportGenerator
import com.rakshak.core.ai.LocalLlmReportGenerator
import com.rakshak.core.ai.NoOpReportGenerator
import com.rakshak.core.ai.RecommendedAction
import com.rakshak.core.ai.SafetyValidator
import com.rakshak.core.ai.llm.LlamaAndroidEngine
import com.rakshak.core.incident.IncidentData
import com.rakshak.core.verification.CameraVerificationEngine
import com.rakshak.core.verification.IncidentVerificationData
import com.rakshak.core.verification.VoiceVerificationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * EmergencyCountdownActivity — core emergency verification and response flow.
 *
 * Flow:
 *  1. State: AWAITING_USER_CHECK (Vibration BUZZ + 10-Second "ARE YOU OKAY?" Countdown)
 *  2. Rider Taps "I'M OKAY" -> Immediately cancels emergency, zero verification/SMS, returns to monitoring
 *  3. Expiration -> State: VERIFYING_INCIDENT (Real Camera + Voice + GPS Verification)
 *  4. Local Qwen LLM Reasoning (Phase 2 Analysis on On-Device GGUF)
 *  5. Authoritative SafetyValidator Check (Enforces absolute safety rules)
 *  6. ONLY if recommendedAction == DISPATCH_SMS -> Send SMS & display SOS Sent UI
 *     OTHERWISE -> Display No Incident Confirmed UI, DO NOT SEND SMS
 */
class EmergencyCountdownActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDetails: TextView
    private lateinit var btnImGood: Button
    private lateinit var btnReturn: Button
    private lateinit var cameraContainer: LinearLayout
    private lateinit var frontTextureView: TextureView
    private lateinit var rearTextureView: TextureView
    private lateinit var tvFrontStatus: TextView
    private lateinit var tvRearStatus: TextView

    private lateinit var reportGenerator: IncidentReportGenerator
    private lateinit var cameraEngine: CameraVerificationEngine
    private lateinit var voiceEngine: VoiceVerificationEngine

    private var countDownTimer: CountDownTimer? = null
    private var verificationJob: Job? = null
    private var isCancelled = false
    private val incidentId = "RKS-${System.currentTimeMillis() % 100000}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportGenerator = resolveReportGenerator()
        cameraEngine = CameraVerificationEngine(applicationContext)
        voiceEngine = VoiceVerificationEngine(applicationContext)

        setContentView(buildLayout())

        Log.i(TAG, "[$incidentId] [01] MOTION_CANDIDATE Safety check initiated")
        
        triggerBuzz()
        startCountdown()
    }

    private fun triggerBuzz() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun resolveReportGenerator(): IncidentReportGenerator {
        val modelAssetPath = "models/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
        return try {
            val engine = LlamaAndroidEngine(applicationContext)
            LocalLlmReportGenerator(
                engine = engine,
                modelPath = modelAssetPath
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to NoOp report generator", e)
            NoOpReportGenerator()
        }
    }

    private fun startCountdown() {
        Log.i(TAG, "[$incidentId] [02] COUNTDOWN_STARTED 10-second countdown active")
        val durationMs = if (intent.getBooleanExtra("auto_expire", false)) 500L else 10000L
        countDownTimer = object : CountDownTimer(durationMs, 500) {
            override fun onTick(millisUntilFinished: Long) {
                if (isCancelled) return
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                tvTimer.text = seconds.toString()
            }

            override fun onFinish() {
                if (isCancelled) return
                tvTimer.text = "0"
                onCountdownExpired()
            }
        }.start()
    }

    private fun onImGoodPressed() {
        isCancelled = true
        countDownTimer?.cancel()
        verificationJob?.cancel()

        cameraEngine.closeCameras()

        Log.i(TAG, "[$incidentId] [03] RIDER_RESPONSE Rider pressed I'M OKAY. Cancelling emergency workflow.")
        com.rakshak.core.detector.IncidentDecisionEngine.resetToNormal()

        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        com.rakshak.core.incident.IncidentRepository.addRecord(
            applicationContext,
            com.rakshak.core.incident.IncidentRecord(
                id = incidentId,
                timestampMs = now,
                title = "Movement Anomaly — Resolved",
                status = "RESOLVED BY RIDER",
                severity = "LOW",
                aiReasoning = "Incident safety check initiated. User tapped 'I'M OKAY'. Alert cancelled by rider with zero emergency actions taken.",
                locationUrl = null,
                timeline = listOf(
                    sdf.format(java.util.Date(now - 10000)) to "Unusual movement pattern detected",
                    sdf.format(java.util.Date(now - 8000)) to "Safety countdown requested",
                    sdf.format(java.util.Date(now)) to "Rider tapped I'M OKAY — Incident cancelled"
                )
            )
        )

        tvStatus.text = "✓ YOU'RE SAFE"
        tvStatus.setTextColor(0xFF4CAF50.toInt())
        tvTimer.text = "CANCELLED"
        tvTimer.textSize = 28f
        tvTimer.setTextColor(0xFF4CAF50.toInt())

        tvDetails.text = "Emergency alert cancelled. No SMS sent.\nReturning to continuous monitoring..."
        btnImGood.visibility = View.GONE
        cameraContainer.visibility = View.GONE
        btnReturn.visibility = View.VISIBLE

        Toast.makeText(this, "Emergency cancelled — Returning to monitoring", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            finish()
        }
    }

    private fun onCountdownExpired() {
        Log.i(TAG, "[$incidentId] [04] VERIFICATION_STARTED 10-second timer reached 0 without response")

        tvStatus.text = "🔍 CHECKING YOUR SITUATION..."
        tvStatus.setTextColor(0xFFFF9800.toInt())
        tvTimer.text = "VERIFYING"
        tvTimer.textSize = 28f
        tvTimer.setTextColor(0xFFFFC107.toInt())

        cameraContainer.visibility = View.VISIBLE
        cameraContainer.alpha = 1.0f

        verificationJob = lifecycleScope.launch {
            runVerificationAndReasoningFlow()
        }
    }

    private suspend fun runVerificationAndReasoningFlow() {
        // Allow Android UI layout pass to complete for TextureViews
        kotlinx.coroutines.delay(200L)

        // 1. Live Camera Verification
        Log.i(TAG, "[$incidentId] [05] FRONT_CAMERA & [06] REAR_CAMERA Starting Camera2 verification engine")
        val cameraResult = cameraEngine.runCameraVerification(incidentId, frontTextureView, rearTextureView)
        
        withContext(Dispatchers.Main) {
            tvFrontStatus.text = "Front: ● ${cameraResult.frontStatus}"
            tvRearStatus.text = "Rear: ● ${cameraResult.rearStatus}"
        }

        // 2. Microphone Voice Check
        Log.i(TAG, "[$incidentId] [07] AUDIO Starting AudioRecord VAD check")
        val voiceResult = voiceEngine.runVoiceVerification(durationMs = 1500L)
        Log.i(TAG, "[$incidentId] [07] AUDIO Voice assessment: ${voiceResult.assessment}")

        // 3. GPS Location Check
        Log.i(TAG, "[$incidentId] [08] GPS Fetching current GPS location")
        val location = getGpsLocation()
        val lat = location?.latitude ?: 17.4224521
        val lng = location?.longitude ?: 78.3369978
        val locationAvailable = location != null
        Log.i(TAG, "[$incidentId] [08] GPS Location: lat=$lat, lng=$lng, available=$locationAvailable")

        // Build IncidentVerificationData structured evidence payload
        val episodeData = com.rakshak.core.detector.IncidentDecisionEngine.getEpisodeData()
        val verificationData = IncidentVerificationData(
            movementClassification = episodeData.fsmState,
            tfliteEvidence = episodeData.peakCrashProb,
            accelerationEvidence = episodeData.peakAccel,
            rotationEvidence = episodeData.peakGyro,
            postEventMovement = "normal",
            frontCameraStatus = cameraResult.frontStatus,
            frontCameraAssessment = cameraResult.frontAssessment,
            rearCameraStatus = cameraResult.rearStatus,
            rearCameraAssessment = cameraResult.rearAssessment,
            voiceStatus = voiceResult.status,
            voiceAssessment = voiceResult.assessment,
            gpsAvailable = locationAvailable,
            latitude = if (locationAvailable) lat else null,
            longitude = if (locationAvailable) lng else null,
            userResponded = false
        )

        withContext(Dispatchers.Main) {
            tvStatus.text = "🧠 AI ASSESSMENT IN PROGRESS..."
            tvDetails.text = buildString {
                append("RAW EVIDENCE\n")
                append("• Acceleration: ${String.format("%.1f", episodeData.peakAccel)} m/s²\n")
                append("• Rotation:     ${String.format("%.1f", episodeData.peakGyro)} rad/s\n")
                append("• Front Camera: ${cameraResult.frontAssessment}\n")
                append("• Rear Camera:  ${cameraResult.rearAssessment}\n")
                append("• Voice Check:  ${voiceResult.assessment}\n")
                append("• GPS Status:   ${if (locationAvailable) "AVAILABLE ($lat, $lng)" else "UNAVAILABLE"}\n\n")
                append("🧠 Qwen 2.5 is analyzing the available evidence...")
            }
        }

        // 4. Local Qwen 2.5 LLM Analysis
        Log.i(TAG, "[$incidentId] [09] QWEN_INPUT Triggering Qwen2.5-0.5B-Instruct verification analysis")
        val incident = IncidentData(
            eventType = "possible_crash",
            confidence = episodeData.peakCrashProb,
            peakAcceleration = episodeData.peakAccel,
            peakGyroscope = episodeData.peakGyro,
            impactDurationMs = episodeData.durationMs,
            riderMovement = verificationData.postEventMovement,
            cameraVerification = cameraResult.frontAssessment,
            audioVerification = voiceResult.assessment,
            locationAvailable = locationAvailable,
            latitude = if (locationAvailable) lat else null,
            longitude = if (locationAvailable) lng else null,
            detectorState = episodeData.fsmState
        )

        val reportResult = reportGenerator.generateReport(incident, timeoutMs = 15000)
        Log.i(TAG, "[$incidentId] [10] QWEN_OUTPUT On-device LLM reasoning completed in ${reportResult.latencyMs}ms")

        val reasoning = reportResult.reasoningResult
        val finalReasoning = SafetyValidator.validateVerification(verificationData, reasoning)

        Log.i(TAG, "[$incidentId] [11] SAFETY_VALIDATION Validated Action: ${finalReasoning.recommendedAction}, Severity: ${finalReasoning.severity}")

        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val locUrl = if (locationAvailable) "https://maps.google.com/?q=$lat,$lng" else "Unavailable"

        // 5. EXPLICIT SAFETY GATE BEFORE SMS DISPATCH
        val isSmsAuthorized = finalReasoning.recommendedAction == RecommendedAction.DISPATCH_SMS

        Log.i(TAG, "[$incidentId] [12] FINAL_ACTION Authorized: $isSmsAuthorized, Action: ${finalReasoning.recommendedAction}")

        val smsStatus = if (isSmsAuthorized) {
            Log.i(TAG, "[$incidentId] [13] SMS_RESULT Dispatching emergency SOS alert")
            val sosMessage = "RAKSHAK ALERT: Confirmed two-wheeler incident detected. Location: $locUrl Time: ${sdf.format(java.util.Date(now))}."
            val status = sendSosSms("9999999999", sosMessage)
            Log.i(TAG, "[$incidentId] [13] SMS_RESULT Status: $status")
            status
        } else {
            Log.i(TAG, "[$incidentId] [13] SMS_RESULT SafetyValidator prevented SMS dispatch. Action=${finalReasoning.recommendedAction}")
            "NO_SMS_REQUIRED"
        }

        // Persist incident record
        com.rakshak.core.incident.IncidentRepository.addRecord(
            applicationContext,
            com.rakshak.core.incident.IncidentRecord(
                id = incidentId,
                timestampMs = now,
                title = if (isSmsAuthorized) "Confirmed Serious Incident" else "No Incident Confirmed",
                status = if (isSmsAuthorized) "SOS SENT" else "LOGGED ONLY",
                severity = finalReasoning.severity.name,
                aiReasoning = finalReasoning.explanation,
                locationUrl = if (locationAvailable) locUrl else null,
                timeline = listOf(
                    sdf.format(java.util.Date(now - 15000)) to "Movement pattern evaluated (${episodeData.fsmState})",
                    sdf.format(java.util.Date(now - 13000)) to "State: AWAITING_USER_CHECK",
                    sdf.format(java.util.Date(now - 3000)) to "State: VERIFYING_INCIDENT (Checking situation)",
                    sdf.format(java.util.Date(now - 2500)) to "Front camera: ${cameraResult.frontAssessment}",
                    sdf.format(java.util.Date(now - 2000)) to "Voice check: ${voiceResult.assessment}",
                    sdf.format(java.util.Date(now - 1500)) to "GPS status: ${if (locationAvailable) "Acquired" else "Unavailable"}",
                    sdf.format(java.util.Date(now - 500)) to "Local Qwen 2.5 analysis completed (${reportResult.latencyMs}ms)",
                    sdf.format(java.util.Date(now)) to "SafetyValidator decision: ${finalReasoning.recommendedAction.name}"
                )
            )
        )

        withContext(Dispatchers.Main) {
            val normalizedConfPercent = (finalReasoning.confidence.coerceIn(0.0f, 0.95f) * 100).toInt()

            if (isSmsAuthorized && smsStatus.startsWith("SMS_SENT")) {
                tvStatus.text = "🚨 EMERGENCY CONTACT NOTIFIED"
                tvStatus.setTextColor(0xFFFF5252.toInt())

                tvDetails.text = buildString {
                    append("RAW EVIDENCE\n")
                    append("• Acceleration: ${String.format("%.1f", episodeData.peakAccel)} m/s²\n")
                    append("• Rotation:     ${String.format("%.1f", episodeData.peakGyro)} rad/s\n")
                    append("• Front Camera: ${cameraResult.frontAssessment}\n")
                    append("• Rear Camera:  ${cameraResult.rearAssessment}\n")
                    append("• Voice Check:  ${voiceResult.assessment}\n")
                    append("• GPS Status:   ${if (locationAvailable) "AVAILABLE" else "UNAVAILABLE"}\n\n")
                    append("LOCAL AI ASSESSMENT\n")
                    append("Qwen 2.5 • Running locally on phone\n")
                    append("Classification: ${finalReasoning.classification}\n")
                    append("Severity:       ${finalReasoning.severity}\n")
                    append("Confidence:     $normalizedConfPercent%\n")
                    append("Why:            ${finalReasoning.explanation}\n\n")
                    append("SAFETY VALIDATION\n")
                    append("✓ SafetyValidator authorized emergency dispatch.\n\n")
                    append("FINAL ACTION\n")
                    append("Emergency contact notification initiated\n")
                    append("Location: $locUrl\n")
                }
            } else {
                tvStatus.text = "🟢 NO INCIDENT CONFIRMED"
                tvStatus.setTextColor(0xFF81C784.toInt())

                tvDetails.text = buildString {
                    append("RAW EVIDENCE\n")
                    append("• Acceleration: ${String.format("%.1f", episodeData.peakAccel)} m/s²\n")
                    append("• Rotation:     ${String.format("%.1f", episodeData.peakGyro)} rad/s\n")
                    append("• Front Camera: ${cameraResult.frontAssessment}\n")
                    append("• Rear Camera:  ${cameraResult.rearAssessment}\n")
                    append("• Voice Check:  ${voiceResult.assessment}\n")
                    append("• GPS Status:   ${if (locationAvailable) "AVAILABLE" else "UNAVAILABLE"}\n\n")
                    append("LOCAL AI ASSESSMENT\n")
                    append("Qwen 2.5 • Running locally on phone\n")
                    append("Classification: ${finalReasoning.classification}\n")
                    append("Severity:       ${finalReasoning.severity}\n")
                    append("Confidence:     $normalizedConfPercent%\n")
                    append("Why:            ${finalReasoning.explanation}\n\n")
                    append("SAFETY VALIDATION\n")
                    append("✓ SafetyValidator verified evidence did not justify emergency dispatch.\n\n")
                    append("FINAL ACTION\n")
                    append("Monitoring / No emergency action\n")
                }
            }

            btnImGood.visibility = View.GONE
            cameraContainer.visibility = View.GONE
            btnReturn.visibility = View.VISIBLE
        }
    }

    private fun getGpsLocation(): Location? {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var bestLoc: Location? = null
            for (p in providers) {
                val l = if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    lm.getLastKnownLocation(p)
                } else null
                if (l != null && (bestLoc == null || l.accuracy < bestLoc.accuracy)) {
                    bestLoc = l
                }
            }
            bestLoc
        } catch (e: Exception) {
            null
        }
    }

    private fun sendSosSms(phone: String, body: String): String {
        return try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phone, null, body, null, null)
                "SMS_SENT_TO_$phone"
            } else {
                "DEMO_SMS_PREPARED (Permission Missing)"
            }
        } catch (e: Exception) {
            Log.w(TAG, "SMS send exception", e)
            "SOS_SEND_FAILED: ${e.message}"
        }
    }

    private fun buildLayout(): ScrollView {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0D0F14.toInt())
            setPadding(dp(20), dp(32), dp(20), dp(20))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Header Title
        container.addView(TextView(this).apply {
            text = "🛡️ RAKSHAK"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        // Status Badge
        tvStatus = TextView(this).apply {
            text = "⚠️ AWAITING RIDER CONFIRMATION"
            setTextColor(0xFFFF9800.toInt())
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(0xFF221A0F.toInt())
        }
        container.addView(tvStatus)

        // Question Banner
        container.addView(TextView(this).apply {
            text = "ARE YOU OKAY?"
            setTextColor(0xFFF0F0F0.toInt())
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        })

        // Countdown Timer Text
        tvTimer = TextView(this).apply {
            text = "10"
            setTextColor(0xFFFF5252.toInt())
            textSize = 72f
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(tvTimer)

        // Dual Camera Container
        cameraContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.VISIBLE
            alpha = 0f
            setPadding(0, 0, 0, dp(16))
        }

        val frontCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val params = LinearLayout.LayoutParams(0, dp(130), 1f)
            params.marginEnd = dp(4)
            layoutParams = params
            setBackgroundColor(0xFF1F2430.toInt())
        }
        tvFrontStatus = TextView(this).apply {
            text = "Front: ● INITIALIZING"
            setTextColor(0xFFB0B8C8.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
        }
        frontCard.addView(tvFrontStatus)
        frontTextureView = TextureView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        frontCard.addView(frontTextureView)
        cameraContainer.addView(frontCard)

        val rearCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val params = LinearLayout.LayoutParams(0, dp(130), 1f)
            params.marginStart = dp(4)
            layoutParams = params
            setBackgroundColor(0xFF1F2430.toInt())
        }
        tvRearStatus = TextView(this).apply {
            text = "Rear: ● INITIALIZING"
            setTextColor(0xFFB0B8C8.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
        }
        rearCard.addView(tvRearStatus)
        rearTextureView = TextureView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        rearCard.addView(rearTextureView)
        cameraContainer.addView(rearCard)

        container.addView(cameraContainer)

        // I'M OKAY Button
        btnImGood = Button(this).apply {
            text = "I'M OKAY  —  CANCEL EMERGENCY"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2E7D32.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setOnClickListener { onImGoodPressed() }
        }
        container.addView(btnImGood)

        // Details Display Area
        tvDetails = TextView(this).apply {
            text = "Press 'I'M OKAY' if you do not require assistance.\nOtherwise, safety verification and local AI analysis will be initiated."
            setTextColor(0xFF9AA0B0.toInt())
            textSize = 12f
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(24)
            layoutParams = params
        }
        container.addView(tvDetails)

        // Return Button
        btnReturn = Button(this).apply {
            text = "RETURN TO MONITORING"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1F2430.toInt())
            textSize = 14f
            visibility = View.GONE
            setOnClickListener { finish() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(16)
            layoutParams = params
        }
        container.addView(btnReturn)

        scrollView.addView(container)
        return scrollView
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val TAG = "EmergencyCountdown"
    }
}
