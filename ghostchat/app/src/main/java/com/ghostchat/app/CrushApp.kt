package com.ghostchat.app

import android.app.Application
import java.io.File

class CrashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, "crash.txt").writeText(e.stackTraceToString().take(4000)) }
            old?.uncaughtException(t, e)
        }
    }
}
