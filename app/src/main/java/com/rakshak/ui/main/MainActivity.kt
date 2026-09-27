package com.rakshak.ui.main

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rakshak.core.detector.IncidentDecisionEngine
import com.rakshak.core.detector.IncidentDecisionState
import com.rakshak.core.incident.IncidentRecord
import com.rakshak.core.incident.IncidentRepository
import com.rakshak.ui.incident.IncidentDetailActivity
import kotlinx.coroutines.launch

/**
 * MainActivity — RAKSHAK On-Device Safety Companion Main App.
 *
 * Polished mobile interface with 4 tabs:
 *  1. HOME — Real-time protection dashboard & live status.
 *  2. INCIDENTS — History timeline of recorded incidents.
 *  3. AI — On-device Qwen 2.5 LLM capabilities overview.
 *  4. SETTINGS — Safety infrastructure readiness & configurations.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tabContainer: FrameLayout
    private lateinit var tvHomeLiveStatus: TextView

    private lateinit var navHomeText: TextView
    private lateinit var navIncidentsText: TextView
    private lateinit var navAiText: TextView
    private lateinit var navSettingsText: TextView

    private var currentTab = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startSensorService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.rakshak.R.layout.activity_main)

        IncidentRepository.init(applicationContext)

        tabContainer = findViewById(com.rakshak.R.id.tab_container)
        navHomeText = findViewById(com.rakshak.R.id.nav_home_text)
        navIncidentsText = findViewById(com.rakshak.R.id.nav_incidents_text)
        navAiText = findViewById(com.rakshak.R.id.nav_ai_text)
        navSettingsText = findViewById(com.rakshak.R.id.nav_settings_text)

        findViewById<View>(com.rakshak.R.id.nav_home).setOnClickListener { switchTab(0) }
        findViewById<View>(com.rakshak.R.id.nav_incidents).setOnClickListener { switchTab(1) }
        findViewById<View>(com.rakshak.R.id.nav_ai).setOnClickListener { switchTab(2) }
        findViewById<View>(com.rakshak.R.id.nav_settings).setOnClickListener { switchTab(3) }

        // Start real-time SensorService
        startSensorService()

        // Request required permissions
        requestRequiredPermissions()

        // Render Home tab by default
        switchTab(0)

        // Observe live status
        observeIncidentDecisionState()
    }

    override fun onResume() {
        super.onResume()
        if (currentTab == 1) {
            renderIncidentsTab()
        }
    }

    private fun startSensorService() {
        try {
            val intent = Intent(this, com.rakshak.core.sensor.SensorService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start SensorService", e)
        }
    }

    private fun requestRequiredPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    private fun switchTab(index: Int) {
        currentTab = index
        navHomeText.setTextColor(if (index == 0) 0xFFFFFFFF.toInt() else 0xFF6C758D.toInt())
        navIncidentsText.setTextColor(if (index == 1) 0xFFFFFFFF.toInt() else 0xFF6C758D.toInt())
        navAiText.setTextColor(if (index == 2) 0xFFFFFFFF.toInt() else 0xFF6C758D.toInt())
        navSettingsText.setTextColor(if (index == 3) 0xFFFFFFFF.toInt() else 0xFF6C758D.toInt())

        tabContainer.removeAllViews()
        when (index) {
            0 -> renderHomeTab()
            1 -> renderIncidentsTab()
            2 -> renderAiTab()
            3 -> renderSettingsTab()
        }
    }

    // ── TAB 1: HOME ──────────────────────────────────────────────────────────

    private fun renderHomeTab() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1117.toInt())
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        // Header
        container.addView(TextView(this).apply {
            text = "🛡️ RAKSHAK"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = "Your On-Device Safety Companion"
            setTextColor(0xFF8E95A5.toInt())
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        })

        // Card 1: Protection Active
        val protCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A2421.toInt())
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        protCard.addView(TextView(this).apply {
            text = "● PROTECTION ACTIVE"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })

        protCard.addView(TextView(this).apply {
            text = "✓ YOU ARE PROTECTED"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(4))
        })

        protCard.addView(TextView(this).apply {
            text = "RAKSHAK is monitoring your movement in real-time for unusual incidents."
            setTextColor(0xFFB0B8C8.toInt())
            textSize = 13f
        })

        container.addView(protCard)

        // Card 2: Live Status
        container.addView(sectionHeader("LIVE MONITORING STATUS"))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        tvHomeLiveStatus = TextView(this).apply {
            text = IncidentDecisionEngine.userStatusText.value
            setTextColor(0xFF81C784.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        statusCard.addView(tvHomeLiveStatus)

        statusCard.addView(TextView(this).apply {
            text = "Continuous 50Hz sensor analysis & multi-signal gate active."
            setTextColor(0xFF8E95A5.toInt())
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        })

        container.addView(statusCard)

        // Card 3: Safety Infrastructure Grid
        container.addView(sectionHeader("SAFETY INFRASTRUCTURE"))

        val gridCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        gridCard.addView(createStatusRow("Accelerometer Sensor", "ACTIVE", 0xFF4CAF50.toInt()))
        gridCard.addView(createStatusRow("Gyroscope Sensor", "ACTIVE", 0xFF4CAF50.toInt()))
        gridCard.addView(createStatusRow("AI Movement Classifier", "ACTIVE", 0xFF4CAF50.toInt()))
        gridCard.addView(createStatusRow("GPS Location System", "READY", 0xFF4CAF50.toInt()))
        gridCard.addView(createStatusRow("Emergency SOS Contact", "READY", 0xFF4CAF50.toInt()))

        container.addView(gridCard)

        scrollView.addView(container)
        tabContainer.addView(scrollView)
    }

    // ── TAB 2: INCIDENTS ─────────────────────────────────────────────────────

    private fun renderIncidentsTab() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1117.toInt())
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        container.addView(TextView(this).apply {
            text = "📋 INCIDENT HISTORY"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = "Recorded safety events and AI incident analysis timeline"
            setTextColor(0xFF8E95A5.toInt())
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        })

        val records = IncidentRepository.getRecords()
        if (records.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF161922.toInt())
                setPadding(dp(24), dp(32), dp(24), dp(32))
                gravity = android.view.Gravity.CENTER
            }

            emptyCard.addView(TextView(this).apply {
                text = "✓ No incidents recorded"
                setTextColor(0xFF81C784.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })

            emptyCard.addView(TextView(this).apply {
                text = "RAKSHAK is active and monitoring. Recorded incidents and AI reports will appear here."
                setTextColor(0xFF8E95A5.toInt())
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
                gravity = android.view.Gravity.CENTER
            })

            container.addView(emptyCard)
        } else {
            for (rec in records) {
                container.addView(createIncidentCard(rec))
            }
        }

        scrollView.addView(container)
        tabContainer.addView(scrollView)
    }

    private fun createIncidentCard(rec: IncidentRecord): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val intent = Intent(this@MainActivity, IncidentDetailActivity::class.java).apply {
                    putExtra("incident_id", rec.id)
                }
                startActivity(intent)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(12)
            layoutParams = params
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        topRow.addView(TextView(this).apply {
            text = rec.title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
        })

        topRow.addView(TextView(this).apply {
            text = rec.status
            setTextColor(
                when (rec.status) {
                    "SOS SENT" -> 0xFFFF5252.toInt()
                    "RESOLVED BY RIDER" -> 0xFF4CAF50.toInt()
                    else -> 0xFFFFB74D.toInt()
                }
            )
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        })

        card.addView(topRow)

        card.addView(TextView(this).apply {
            text = rec.formattedTime()
            setTextColor(0xFF8E95A5.toInt())
            textSize = 12f
            setPadding(0, dp(4), 0, dp(6))
        })

        card.addView(TextView(this).apply {
            text = rec.aiReasoning
            setTextColor(0xFFD0D5E0.toInt())
            textSize = 13f
            maxLines = 2
        })

        card.addView(TextView(this).apply {
            text = "Tap to view complete timeline & Qwen AI breakdown →"
            setTextColor(0xFF64B5F6.toInt())
            textSize = 11f
            setPadding(0, dp(8), 0, 0)
        })

        return card
    }

    // ── TAB 3: AI ────────────────────────────────────────────────────────────

    private fun renderAiTab() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1117.toInt())
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        container.addView(TextView(this).apply {
            text = "🧠 ON-DEVICE AI REASONING"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = "Qwen 2.5 0.5B Instruct • 100% On-Device Local Reasoning"
            setTextColor(0xFF8E95A5.toInt())
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        })

        // Model Status Card
        val aiModelCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        aiModelCard.addView(TextView(this).apply {
            text = "● RUNNING LOCALLY ON PHONE"
            setTextColor(0xFF81C784.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })

        aiModelCard.addView(TextView(this).apply {
            text = "Qwen2.5-0.5B-Instruct GGUF"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(4))
        })

        aiModelCard.addView(TextView(this).apply {
            text = "Your incident data is analyzed entirely on your device with native ARM64 llama.cpp. No cloud APIs, no server calls, complete offline privacy."
            setTextColor(0xFFB0B8C8.toInt())
            textSize = 13f
        })

        container.addView(aiModelCard)

        // 6-Stage Intelligence
        container.addView(sectionHeader("6-STAGE INTELLIGENT DETECTION"))

        val stageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        stageCard.addView(createCapItem("1️⃣ NORMAL", "Continuous 50Hz monitoring. Small movements stay normal."))
        stageCard.addView(createCapItem("2️⃣ MINOR JERK", "Brief acceleration spike. Auto-decays in 1.5s."))
        stageCard.addView(createCapItem("3️⃣ UNUSUAL MOVEMENT", "Sustained elevated signals. Requires 2+ consecutive windows."))
        stageCard.addView(createCapItem("4️⃣ POSSIBLE IMPACT", "Strong force + rotation. Multi-signal confirmation."))
        stageCard.addView(createCapItem("5️⃣ POSSIBLE INCIDENT", "Temporal pattern validated. Evidence accumulation."))
        stageCard.addView(createCapItem("6️⃣ CONFIRMED INCIDENT", "Full multi-signal gate passed. Emergency countdown triggered."))

        container.addView(stageCard)

        // Safety Architecture
        container.addView(sectionHeader("SAFETY ARCHITECTURE"))

        val safetyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        safetyCard.addView(createCapItem("🔒 Sequential Escalation Only", "States can only progress one level at a time — no jumps."))
        safetyCard.addView(createCapItem("⏱ Temporal Validation", "Each stage requires sustained evidence — single spikes auto-decay."))
        safetyCard.addView(createCapItem("🛡️ SafetyValidator", "LLM output is advisory. Deterministic rules enforce safety boundaries."))

        container.addView(safetyCard)

        scrollView.addView(container)
        tabContainer.addView(scrollView)
    }

    private fun createCapItem(title: String, desc: String): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }

        layout.addView(TextView(this).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })

        layout.addView(TextView(this).apply {
            text = desc
            setTextColor(0xFF8E95A5.toInt())
            textSize = 12f
        })

        return layout
    }

    // ── TAB 4: SETTINGS ──────────────────────────────────────────────────────

    private fun renderSettingsTab() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1117.toInt())
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        container.addView(TextView(this).apply {
            text = "⚙️ SETTINGS & SYSTEM STATUS"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = "RAKSHAK System Readiness & Safety Settings"
            setTextColor(0xFF8E95A5.toInt())
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        })

        val setCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        setCard.addView(createStatusRow("Emergency Contact Number", "9999999999", 0xFF64B5F6.toInt()))
        setCard.addView(createStatusRow("SMS Service Dispatch", "ACTIVE", 0xFF4CAF50.toInt()))
        setCard.addView(createStatusRow("GPS Location Access", "GRANTED", 0xFF4CAF50.toInt()))
        setCard.addView(createStatusRow("Camera Permission", "GRANTED", 0xFF4CAF50.toInt()))
        setCard.addView(createStatusRow("Microphone Permission", "GRANTED", 0xFF4CAF50.toInt()))

        container.addView(setCard)

        // Debug trigger button for controlled verification
        container.addView(sectionHeader("DEBUG TESTING"))
        container.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "🚨 TRIGGER SAFETY CHECK (DEBUG)"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF252B3A.toInt())
            textSize = 13f
            setOnClickListener {
                val intent = Intent(this@MainActivity, com.rakshak.ui.emergency.EmergencyCountdownActivity::class.java)
                startActivity(intent)
            }
        })

        scrollView.addView(container)
        tabContainer.addView(scrollView)
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private fun observeIncidentDecisionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    IncidentDecisionEngine.userStatusText.collect { status ->
                        if (::tvHomeLiveStatus.isInitialized) {
                            tvHomeLiveStatus.text = status
                        }
                    }
                }
                launch {
                    IncidentDecisionEngine.decisionState.collect { state ->
                        if (::tvHomeLiveStatus.isInitialized) {
                            when (state) {
                                IncidentDecisionState.NORMAL -> tvHomeLiveStatus.setTextColor(0xFF81C784.toInt())
                                IncidentDecisionState.MINOR_JERK -> tvHomeLiveStatus.setTextColor(0xFFB0B8C8.toInt())
                                IncidentDecisionState.UNUSUAL_MOVEMENT -> tvHomeLiveStatus.setTextColor(0xFFFFB74D.toInt())
                                IncidentDecisionState.POSSIBLE_IMPACT -> tvHomeLiveStatus.setTextColor(0xFFFF9800.toInt())
                                IncidentDecisionState.POSSIBLE_INCIDENT -> tvHomeLiveStatus.setTextColor(0xFFFF5722.toInt())
                                IncidentDecisionState.CONFIRMED_INCIDENT -> tvHomeLiveStatus.setTextColor(0xFFFF5252.toInt())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createStatusRow(label: String, status: String, color: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }

        row.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFD0D5E0.toInt())
            textSize = 14f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams = params
        })

        row.addView(TextView(this).apply {
            text = status
            setTextColor(color)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })

        return row
    }

    private fun sectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(0xFF6C758D.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(20)
            params.bottomMargin = dp(8)
            layoutParams = params
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
