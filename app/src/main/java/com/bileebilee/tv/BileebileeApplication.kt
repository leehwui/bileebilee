package com.bileebilee.tv

import android.app.Application
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter

class BileebileeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                getSharedPreferences(CRASH_PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(LAST_CRASH_KEY, trace)
                    .commit()
            } catch (_: Throwable) {
                // Never obscure the original failure if recording also fails.
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    companion object {
        const val CRASH_PREFERENCES = "crash_diagnostics"
        const val LAST_CRASH_KEY = "last_crash"
    }
}

