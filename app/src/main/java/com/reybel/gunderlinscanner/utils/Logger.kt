package com.reybel.gunderlinscanner.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "GunderlinScanner"
    private lateinit var logFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val today = dateFormat.format(Date())
        logFile = File(logDir, "${today}_log.txt")
    }

    fun log(message: String, level: String = "INFO") {
        // Log to Android logcat
        when (level) {
            "ERROR" -> Log.e(TAG, message)
            "WARN" -> Log.w(TAG, message)
            "DEBUG" -> Log.d(TAG, message)
            else -> Log.i(TAG, message)
        }

        // Log to file
        logToFile(message)
    }

    private fun logToFile(message: String) {
        try {
            if (::logFile.isInitialized) {
                val timestamp = timeFormat.format(Date())
                val logEntry = "[$timestamp] $message\n"

                FileWriter(logFile, true).use { writer ->
                    writer.append(logEntry)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to log file", e)
        }
    }

    fun getLogFile(): File? {
        return if (::logFile.isInitialized) logFile else null
    }
}