package com.aghielaan22.label

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Date

object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val version = try {
                    appContext.packageManager
                        .getPackageInfo(appContext.packageName, 0).versionName
                } catch (e: Exception) {
                    "unknown"
                }
                val report = buildString {
                    appendLine("LAbel crash report")
                    appendLine("Time: ${Date()}")
                    appendLine("App version: $version")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(Log.getStackTraceString(throwable))
                }
                File(appContext.filesDir, FILE_NAME).writeText(report)
            } catch (e: Throwable) {
                // Never let the reporter itself cause trouble
            }
            // Hand over to Android's normal crash handling
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun pendingReport(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}