package com.nepalime.keyboard

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent logger. Every entry goes to Logcat (tag "NepaliIME") AND
 * gets appended to a plain text file in the app's private storage, so logs
 * can be pulled off a tablet with no ADB/laptop — just open the app and use
 * LogViewerActivity's Refresh/Share buttons.
 */
object Logger {
    private const val TAG = "NepaliIME"
    private const val FILE_NAME = "ime_log.txt"
    private const val MAX_LINES = 3000
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile == null) {
            logFile = File(context.filesDir, FILE_NAME)
        }
    }

    fun d(message: String) {
        Log.d(TAG, message)
        appendToFile(message)
    }

    @Synchronized
    private fun appendToFile(message: String) {
        val file = logFile ?: return
        val line = "${timeFormat.format(Date())}  $message\n"
        try {
            file.appendText(line)
            trimIfNeeded(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log file", e)
        }
    }

    private fun trimIfNeeded(file: File) {
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }

    fun readAll(context: Context): String {
        init(context)
        val file = logFile ?: return "(no log file yet)"
        return if (file.exists() && file.length() > 0) file.readText()
        else "(log is empty — type something in a text field with the IME active first)"
    }

    fun clear(context: Context) {
        init(context)
        logFile?.let { if (it.exists()) it.writeText("") }
    }

    fun getFile(context: Context): File {
        init(context)
        return logFile!!
    }
}
