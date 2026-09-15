package com.helium4.localscreenshare

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val PREFS = "zerolink"
    private const val KEY_ENABLED = "logging_enabled"
    private const val LOG_FILE = "zerolink_log.txt"
    private const val MAX_SIZE_BYTES = 400_000L
    private const val KEEP_BYTES = 200_000L

    private val lock = Any()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var appContext: Context? = null
    private var enabled: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
        enabled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Force-enable and write the crash even if logging was off — a crash is always useful.
            val wasEnabled = enabled
            enabled = true
            e("CRASH", "Uncaught exception on thread '${thread.name}'", throwable)
            enabled = wasEnabled
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
        if (!value) clearLogs()
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        write("I", tag, msg, null)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        write("W", tag, msg, null)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        write("E", tag, msg, t)
    }

    fun readLogs(): String {
        val ctx = appContext ?: return ""
        val file = File(ctx.filesDir, LOG_FILE)
        return if (file.exists()) file.readText() else ""
    }

    fun clearLogs() {
        val ctx = appContext ?: return
        synchronized(lock) {
            File(ctx.filesDir, LOG_FILE).delete()
        }
    }

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        if (!enabled) return
        val ctx = appContext ?: return
        synchronized(lock) {
            val file = File(ctx.filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                val content = file.readText()
                val trimmed = content.takeLast(KEEP_BYTES.toInt())
                file.writeText(trimmed)
            }
            val timestamp = fmt.format(Date())
            val line = buildString {
                append("$timestamp $level/ZeroLink: [$tag] $msg")
                if (t != null) {
                    append("\n  ${t.javaClass.name}: ${t.message}")
                    t.stackTrace.take(12).forEach { frame ->
                        append("\n    at $frame")
                    }
                    var cause = t.cause
                    while (cause != null) {
                        append("\n  Caused by ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.take(6).forEach { frame ->
                            append("\n    at $frame")
                        }
                        cause = cause.cause
                    }
                }
                append("\n")
            }
            file.appendText(line)
        }
    }
}
