package com.rakshak.ui.incident

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rakshak.core.incident.IncidentRepository

/**
 * IncidentDetailActivity — shows complete incident report, timeline, and Qwen AI reasoning.
 */
class IncidentDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incidentId = intent.getStringExtra("incident_id") ?: ""
        IncidentRepository.init(applicationContext)
        val record = IncidentRepository.getRecordById(incidentId)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1117.toInt())
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        if (record == null) {
            container.addView(TextView(this).apply {
                text = "Incident record not found."
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
            })
            scrollView.addView(container)
            setContentView(scrollView)
            return
        }

        // Title
        container.addView(TextView(this).apply {
            text = "INCIDENT REPORT"
            setTextColor(0xFF8E95A5.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = record.title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        })

        container.addView(TextView(this).apply {
            text = record.formattedTime()
            setTextColor(0xFF9AA0B0.toInt())
            textSize = 13f
        })

        // Status Banner
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(16)
            layoutParams = params
        }

        statusCard.addView(TextView(this).apply {
            text = "STATUS: "
            setTextColor(0xFF8E95A5.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })

        statusCard.addView(TextView(this).apply {
            text = record.status
            setTextColor(
                when (record.status) {
                    "SOS SENT" -> 0xFFFF5252.toInt()
                    "RESOLVED BY RIDER" -> 0xFF4CAF50.toInt()
                    else -> 0xFFFFB74D.toInt()
                }
            )
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(statusCard)

        // Section: AI Reasoning
        container.addView(sectionHeader("ON-DEVICE AI REASONING (Qwen 2.5)"))

        val aiCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        aiCard.addView(TextView(this).apply {
            text = "● RUNNING LOCALLY ON PHONE"
            setTextColor(0xFF81C784.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        })

        aiCard.addView(TextView(this).apply {
            text = record.aiReasoning
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })

        container.addView(aiCard)

        // Section: Timeline
        container.addView(sectionHeader("INCIDENT TIMELINE"))

        val timelineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161922.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        for (item in record.timeline) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }

            itemRow.addView(TextView(this).apply {
                text = item.first
                setTextColor(0xFF8E95A5.toInt())
                textSize = 12f
                typeface = Typeface.MONOSPACE
                val params = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = params
            })

            itemRow.addView(TextView(this).apply {
                text = item.second
                setTextColor(0xFFD0D5E0.toInt())
                textSize = 13f
            })

            timelineCard.addView(itemRow)
        }

        container.addView(timelineCard)

        // Location URL if present
        if (!record.locationUrl.isNullOrEmpty()) {
            container.addView(sectionHeader("GPS EVIDENCE"))
            val locCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF161922.toInt())
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            locCard.addView(TextView(this).apply {
                text = record.locationUrl
                setTextColor(0xFF64B5F6.toInt())
                textSize = 13f
                typeface = Typeface.MONOSPACE
            })
            container.addView(locCard)
        }

        // Back button
        container.addView(Button(this).apply {
            text = "BACK TO INCIDENTS"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF252B3A.toInt())
            textSize = 14f
            setOnClickListener { finish() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(24)
            layoutParams = params
        })

        scrollView.addView(container)
        setContentView(scrollView)
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
