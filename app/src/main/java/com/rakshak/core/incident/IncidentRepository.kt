package com.rakshak.core.incident

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IncidentRecord(
    val id: String,
    val timestampMs: Long,
    val title: String,
    val status: String, // "SOS SENT", "RESOLVED BY RIDER", "CANCELLED"
    val severity: String,
    val aiReasoning: String,
    val locationUrl: String?,
    val timeline: List<Pair<String, String>>
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}

object IncidentRepository {

    private const val PREF_NAME = "rakshak_incident_records"
    private const val KEY_RECORDS = "records_json"

    private val inMemoryRecords = mutableListOf<IncidentRecord>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RECORDS, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                inMemoryRecords.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val timelineList = mutableListOf<Pair<String, String>>()
                    val tArr = obj.optJSONArray("timeline")
                    if (tArr != null) {
                        for (j in 0 until tArr.length()) {
                            val tObj = tArr.getJSONObject(j)
                            timelineList.add(Pair(tObj.getString("time"), tObj.getString("event")))
                        }
                    }
                    inMemoryRecords.add(
                        IncidentRecord(
                            id = obj.getString("id"),
                            timestampMs = obj.getLong("timestampMs"),
                            title = obj.getString("title"),
                            status = obj.getString("status"),
                            severity = obj.getString("severity"),
                            aiReasoning = obj.getString("aiReasoning"),
                            locationUrl = obj.optString("locationUrl", null),
                            timeline = timelineList
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    @Synchronized
    fun addRecord(context: Context, record: IncidentRecord) {
        inMemoryRecords.add(0, record)
        saveToPrefs(context)
    }

    @Synchronized
    fun getRecords(): List<IncidentRecord> {
        return inMemoryRecords.toList()
    }

    @Synchronized
    fun getRecordById(id: String): IncidentRecord? {
        return inMemoryRecords.find { it.id == id }
    }

    private fun saveToPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (r in inMemoryRecords) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("timestampMs", r.timestampMs)
                    put("title", r.title)
                    put("status", r.status)
                    put("severity", r.severity)
                    put("aiReasoning", r.aiReasoning)
                    put("locationUrl", r.locationUrl)
                    val tArr = JSONArray()
                    for (t in r.timeline) {
                        tArr.put(JSONObject().apply {
                            put("time", t.first)
                            put("event", t.second)
                        })
                    }
                    put("timeline", tArr)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
        } catch (e: Exception) {
            // Ignore write errors
        }
    }
}
