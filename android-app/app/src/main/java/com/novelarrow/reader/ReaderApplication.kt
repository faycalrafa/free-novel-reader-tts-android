package com.novelarrow.reader

import android.app.Application
import com.novelarrow.reader.util.AppErrorBus
import com.novelarrow.reader.util.AppLogger
import java.io.File

class ReaderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppLogger.i("ReaderApp", "BookReader starting up...")

        setupCrashHandler()
        checkForPreviousCrash()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.e("CrashHandler", "Fatal crash on thread ${thread.name}: ${throwable.message}", throwable)
                val crashFile = File(filesDir, "latest_crash.txt")
                val crashInfo = buildString {
                    appendLine("Thread: ${thread.name}")
                    appendLine("Timestamp: ${System.currentTimeMillis()}")
                    appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                    appendLine(AppLogger.getStackTraceString(throwable))
                }
                crashFile.writeText(crashInfo)
            } catch (ignored: Exception) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun checkForPreviousCrash() {
        try {
            val crashFile = File(filesDir, "latest_crash.txt")
            if (crashFile.exists() && crashFile.length() > 0) {
                val crashText = crashFile.readText()
                // Once read, clear or rename it so it doesn't alert repeatedly
                crashFile.delete()

                AppLogger.e("CrashRecovery", "Recovered crash log from previous session:\n$crashText")
                AppErrorBus.postError(
                    title = "Application Crashed in Previous Session",
                    message = "A fatal crash occurred before the app closed. You can inspect or copy the log below.",
                    throwable = RuntimeException(crashText)
                )
            }
        } catch (e: Exception) {
            AppLogger.w("CrashRecovery", "Failed reading previous crash log: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: ReaderApplication
            private set
    }
}
