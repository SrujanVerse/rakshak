package com.rakshak.ui.demo

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rakshak.core.ai.IncidentReportGenerator
import com.rakshak.core.ai.NoOpReportGenerator
import com.rakshak.core.ai.testing.FakeIncidentDataGenerator
import com.rakshak.core.incident.IncidentData
import kotlinx.coroutines.launch

/**
 * AiDemoActivity — test screen for the AI report generation pipeline.
 *
 * Allows generating fake incidents and verifying report output
 * without triggering real crashes. Uses NoOpReportGenerator by default;
 * switches to LocalLlmReportGenerator when the model is available.
 *
 * This screen does NOT interact with P0 components (no SMS, no real sensor data,
 * no crash detection). It is a pure P2 testing tool.
 *
 * Launch via adb:
 *   adb shell am start -n com.rakshak.debug/com.rakshak.ui.demo.AiDemoActivity
 */
class AiDemoActivity : AppCompatActivity() {

    private lateinit var tvIncidentJson: TextView
    private lateinit var tvReport: TextView
    private lateinit var tvMetrics: TextView
    private lateinit var reportGenerator: IncidentReportGenerator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use LocalLlmReportGenerator when model is ready, or NoOp as fallback
        reportGenerator = resolveReportGenerator()

        // Build UI programmatically
        setContentView(buildLayout())

        // Auto-run first scenario for immediate verification
        val scenarios = FakeIncidentDataGenerator.allScenarios()
        if (scenarios.isNotEmpty()) {
            val (name, incident) = scenarios.first()
            runScenario(name, incident)
        }
    }

    /**
     * Resolve which report generator to use.
     * Currently defaults to NoOp. When the LLM model file is present,
     * this can be extended to instantiate LocalLlmReportGenerator.
     */
    private fun resolveReportGenerator(): IncidentReportGenerator {
        val modelAssetPath = "models/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
        return try {
            val engine = com.rakshak.core.ai.llm.LlamaAndroidEngine(applicationContext)
            com.rakshak.core.ai.LocalLlmReportGenerator(
                engine = engine,
                modelPath = modelAssetPath
            )
        } catch (e: Exception) {
            android.util.Log.w("AiDemoActivity", "Falling back to NoOp report generator", e)
            NoOpReportGenerator()
        }
    }

    private fun buildLayout(): ScrollView {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1117.toInt())
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Title
        container.addView(TextView(this).apply {
            text = "\uD83D\uDEE1\uFE0F RAKSHAK AI Demo"
            setTextColor(0xFFF0F0F0.toInt())
            textSize = 24f
            setPadding(0, 0, 0, dp(4))
        })

        // Subtitle
        container.addView(TextView(this).apply {
            text = "Test incident report generation without real crashes"
            setTextColor(0xFF9AA0B0.toInt())
            textSize = 13f
            setPadding(0, 0, 0, dp(16))
        })

        // Generator status
        container.addView(TextView(this).apply {
            val generatorName = reportGenerator.javaClass.simpleName
            val ready = reportGenerator.isReady()
            text = "Generator: $generatorName (ready: $ready)"
            setTextColor(if (ready) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(16))
        })

        // Prominent Emergency Countdown Demo Button
        container.addView(Button(this).apply {
            text = "🚨 DEMO INCIDENT (EMERGENCY FLOW)"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF5252.toInt())
            textSize = 15f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(16)
            layoutParams = params
            setOnClickListener {
                startActivity(android.content.Intent(this@AiDemoActivity, com.rakshak.ui.emergency.EmergencyCountdownActivity::class.java))
            }
        })

        // Scenario buttons
        for ((name, incident) in FakeIncidentDataGenerator.allScenarios()) {
            container.addView(createScenarioButton(name, incident))
        }

        // Incident JSON display
        container.addView(sectionHeader("Incident Data (JSON)"))
        tvIncidentJson = TextView(this).apply {
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 11f
            setBackgroundColor(0xFF1C1F2A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            typeface = Typeface.MONOSPACE
            text = "\u2190 Select a scenario above"
        }
        container.addView(tvIncidentJson)

        // Report display
        container.addView(sectionHeader("Generated Report"))
        tvReport = TextView(this).apply {
            setTextColor(0xFFF0F0F0.toInt())
            textSize = 14f
            setBackgroundColor(0xFF1C1F2A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            text = "No report generated yet"
        }
        container.addView(tvReport)

        // Metrics display
        container.addView(sectionHeader("Metrics"))
        tvMetrics = TextView(this).apply {
            setTextColor(0xFF9AA0B0.toInt())
            textSize = 12f
            setBackgroundColor(0xFF1C1F2A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            typeface = Typeface.MONOSPACE
            text = "\u2014"
        }
        container.addView(tvMetrics)

        scrollView.addView(container)
        return scrollView
    }

    private fun createScenarioButton(name: String, incident: IncidentData): Button {
        return Button(this).apply {
            text = "\u25B6 $name"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2A2D3A.toInt())
            textSize = 13f
            isAllCaps = false
            setPadding(dp(16), dp(8), dp(16), dp(8))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(6)
            layoutParams = params

            setOnClickListener { runScenario(name, incident) }
        }
    }

    private fun runScenario(name: String, incident: IncidentData) {
        // Display incident JSON (pretty-printed)
        tvIncidentJson.text = incident.toJsonString()
            .replace(",", ",\n  ")
            .replace("{", "{\n  ")
            .replace("}", "\n}")

        tvReport.text = "Generating report..."
        tvReport.setTextColor(0xFF9AA0B0.toInt())
        tvMetrics.text = "Running..."

        // Generate report asynchronously
        lifecycleScope.launch {
            val result = reportGenerator.generateReport(incident)

            if (result.isSuccess) {
                tvReport.text = result.report
                tvReport.setTextColor(0xFFF0F0F0.toInt())
            } else {
                tvReport.text = "\u274C Generation failed: ${result.error}"
                tvReport.setTextColor(0xFFFF5722.toInt())
            }

            tvMetrics.text = buildString {
                append("Scenario:  $name\n")
                append("Generator: ${result.generatorType}\n")
                append("Latency:   ${result.latencyMs}ms\n")
                append("Tokens:    ${result.tokenCount}\n")
                append("Success:   ${result.isSuccess}\n")
                if (result.error != null) {
                    append("Error:     ${result.error}")
                }
            }
        }
    }

    private fun sectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(0xFF9AA0B0.toInt())
            textSize = 12f
            setPadding(0, dp(16), 0, dp(4))
            setTypeface(null, Typeface.BOLD)
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
