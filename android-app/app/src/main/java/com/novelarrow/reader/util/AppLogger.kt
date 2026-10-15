package com.novelarrow.reader.util

import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogItem(
    val id: Long,
    val timestampMillis: Long,
    val timeFormatted: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

object AppLogger {

    private const val MAX_LOGS = 1000
    private const val RETENTION_WINDOW_MS = 60 * 60 * 1000L // 60 minutes retention
    private val idCounter = AtomicLong(0)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _logsState = MutableStateFlow<List<LogItem>>(emptyList())
    val logsState: StateFlow<List<LogItem>> = _logsState.asStateFlow()

    fun d(tag: String, message: String, t: Throwable? = null) {
        Log.d(tag, message, t)
        record(LogLevel.DEBUG, tag, message, t)
    }

    fun i(tag: String, message: String, t: Throwable? = null) {
        Log.i(tag, message, t)
        record(LogLevel.INFO, tag, message, t)
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        Log.w(tag, message, t)
        record(LogLevel.WARN, tag, message, t)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e(tag, message, t)
        record(LogLevel.ERROR, tag, message, t)
    }

    private fun record(level: LogLevel, tag: String, message: String, t: Throwable?) {
        val now = System.currentTimeMillis()
        val timeStr = timeFormat.format(Date(now))
        val stackStr = t?.let { getStackTraceString(it) }

        val item = LogItem(
            id = idCounter.incrementAndGet(),
            timestampMillis = now,
            timeFormatted = timeStr,
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackStr
        )

        _logsState.update { current ->
            val cutoff = now - RETENTION_WINDOW_MS
            // Newest item at the beginning (index 0), purge items older than 60m, cap at MAX_LOGS
            buildList {
                add(item)
                for (old in current) {
                    if (old.timestampMillis >= cutoff) {
                        add(old)
                    }
                    if (size >= MAX_LOGS) break
                }
            }
        }
    }

    fun clear() {
        _logsState.value = emptyList()
    }

    suspend fun dumpSystemLogcat(): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        try {
            val pid = Process.myPid().toString()
            val command = arrayOf("logcat", "-d", "-v", "time", "--pid=$pid")
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.destroy()

            val now = System.currentTimeMillis()
            val newItems = mutableListOf<LogItem>()

            for (line in lines.takeLast(300)) {
                if (line.isBlank()) continue
                val level = when {
                    line.contains(" E/") || line.contains(" E ") -> LogLevel.ERROR
                    line.contains(" W/") || line.contains(" W ") -> LogLevel.WARN
                    line.contains(" I/") || line.contains(" I ") -> LogLevel.INFO
                    else -> LogLevel.DEBUG
                }
                newItems.add(
                    LogItem(
                        id = idCounter.incrementAndGet(),
                        timestampMillis = now,
                        timeFormatted = timeFormat.format(Date(now)),
                        level = level,
                        tag = "SystemLogcat",
                        message = line
                    )
                )
                addedCount++
            }

            _logsState.update { current ->
                // Newest system items first
                (newItems.reversed() + current).take(MAX_LOGS)
            }
        } catch (e: Exception) {
            e("AppLogger", "Failed to dump system logcat: ${e.message}", e)
        }
        addedCount
    }

    fun formatForExport(appVersion: String): String {
        val sb = StringBuilder()
        sb.appendLine("=== NovelArrow Reader Debug Logs ===")
        sb.appendLine("App Version: $appVersion")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Exported At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Total Entries: ${_logsState.value.size}")
        sb.appendLine("------------------------------------")

        for (item in _logsState.value) {
            val levelChar = item.level.name.first()
            sb.appendLine("[${item.timeFormatted}] $levelChar/${item.tag}: ${item.message}")
            if (!item.stackTrace.isNullOrBlank()) {
                sb.appendLine(item.stackTrace)
            }
        }
        return sb.toString()
    }

    fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
