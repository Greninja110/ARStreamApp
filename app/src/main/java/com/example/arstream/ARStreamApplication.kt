// File: ARStreamApp/app/src/main/java/com/example/arstream/ARStreamApplication.kt
package com.example.arstream

import android.app.Application
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ARStreamApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        // Use standard BuildConfig.DEBUG instead of DEBUG_MODE
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLoggingTree(this))
        }
    }

    /**
     * Custom Timber tree that logs to both console and a file
     */
    private class FileLoggingTree(private val application: Application) : Timber.Tree() {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val logFolder = File(application.getExternalFilesDir(null), "logs")

        init {
            if (!logFolder.exists()) {
                logFolder.mkdirs()
            }
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            try {
                val fileName = "log_${dateFormat.format(Date())}.txt"
                val logFile = File(logFolder, fileName)

                if (!logFile.exists()) {
                    logFile.createNewFile()
                }

                // Format: [Timestamp] [Priority/Tag] Message
                val priorityChar = when (priority) {
                    android.util.Log.VERBOSE -> 'V'
                    android.util.Log.DEBUG -> 'D'
                    android.util.Log.INFO -> 'I'
                    android.util.Log.WARN -> 'W'
                    android.util.Log.ERROR -> 'E'
                    android.util.Log.ASSERT -> 'A'
                    else -> '?'
                }

                val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val logEntry = "[$timeStamp] [$priorityChar/${tag ?: ""}] $message\n"

                // Append to file
                logFile.appendText(logEntry)

                // If there's a throwable, also log the stack trace
                if (t != null) {
                    logFile.appendText("${t.stackTraceToString()}\n")
                }
            } catch (e: Exception) {
                android.util.Log.e("FileLoggingTree", "Error writing to log file", e)
            }
        }
    }
}