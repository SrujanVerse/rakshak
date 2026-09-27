package com.rakshak.core.log

import android.content.Context
import android.util.Log
import java.io.File

/**
 * IncidentLogIntegrityChecker — validates the on-disk incident log.
 *
 * For this initial scaffold the log is a simple append-only text file.
 * Integrity check verifies:
 *  1. The log directory exists and is writable.
 *  2. If a log file exists, it is not corrupted (non-zero, readable).
 *
 * This class is intentionally simple for the scaffold phase.
 * A cryptographic checksum will be added when the full IncidentLogger is built (P0 prompt 2).
 */
class IncidentLogIntegrityChecker(private val context: Context) {

    data class IntegrityResult(
        val isIntact: Boolean,
        val detail: String,
    )

    fun check(): IntegrityResult {
        return try {
            val logDir = getLogDirectory()

            // Check 1: directory exists or can be created
            if (!logDir.exists() && !logDir.mkdirs()) {
                return IntegrityResult(
                    isIntact = false,
                    detail = "Cannot create log directory at ${logDir.absolutePath}",
                )
            }

            // Check 2: directory is writable
            if (!logDir.canWrite()) {
                return IntegrityResult(
                    isIntact = false,
                    detail = "Log directory is not writable",
                )
            }

            // Check 3: if log file exists, it must be readable and non-corrupted
            val logFile = File(logDir, LOG_FILE_NAME)
            if (logFile.exists()) {
                if (!logFile.canRead()) {
                    return IntegrityResult(
                        isIntact = false,
                        detail = "Log file exists but is not readable — may be corrupted",
                    )
                }
                if (logFile.length() == 0L) {
                    // Empty file is acceptable (no incidents yet)
                    return IntegrityResult(isIntact = true, detail = "Log ready (empty)")
                }
                // Verify at least one complete line exists
                val firstLine = logFile.bufferedReader().use { it.readLine() }
                if (firstLine == null) {
                    return IntegrityResult(
                        isIntact = false,
                        detail = "Log file appears truncated or corrupt",
                    )
                }
            }

            val entryCount = countEntries(logFile)
            IntegrityResult(
                isIntact = true,
                detail = if (entryCount == 0) "Log ready — no incidents recorded"
                         else "$entryCount incident(s) recorded",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Log integrity check failed", e)
            IntegrityResult(
                isIntact = false,
                detail = "Integrity check error: ${e.message}",
            )
        }
    }

    /**
     * Returns the canonical log directory inside the app's private files dir.
     * Not accessible to other apps; survives app updates but cleared on uninstall.
     */
    fun getLogDirectory(): File = File(context.filesDir, LOG_DIR_NAME)

    private fun countEntries(logFile: File): Int {
        if (!logFile.exists() || logFile.length() == 0L) return 0
        return try {
            logFile.bufferedReader().use { reader ->
                var count = 0
                while (reader.readLine() != null) count++
                count
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not count log entries: ${e.message}")
            0
        }
    }

    companion object {
        private const val TAG = "LogIntegrityChecker"
        const val LOG_DIR_NAME = "incidents"
        const val LOG_FILE_NAME = "incident_log.jsonl"
    }
}
