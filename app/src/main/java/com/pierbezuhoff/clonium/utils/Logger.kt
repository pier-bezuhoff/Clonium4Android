package com.pierbezuhoff.clonium.utils

import android.util.Log

interface Logger {
    enum class Importance {
        VERBOSE, DEBUG, INFO, WARNING, ERROR, INF;
        fun shorten(): String =
            when (this) {
                VERBOSE -> "V"
                DEBUG -> "D"
                INFO -> "I"
                WARNING -> "W"
                ERROR -> "E"
                INF -> throw InfImportanceException
            }
        object InfImportanceException : IllegalArgumentException("Importance.INF cannot be log-ged")
    }

    val logTag: String
    val minLogImportance: Importance

    fun log(importance: Importance, message: String)
    fun logV(message: String) = log(Importance.VERBOSE, message)
    fun logD(message: String) = log(Importance.DEBUG, message)
    fun logI(message: String) = log(Importance.INFO, message)
    fun logW(message: String) = log(Importance.WARNING, message)
    fun logE(message: String) = log(Importance.ERROR, message)
}

class StandardLogger(
    override val logTag: String = "StandardLogger",
    override val minLogImportance: Logger.Importance = Logger.Importance.VERBOSE
) : Logger {
    override fun log(importance: Logger.Importance, message: String) {
        if (importance >= minLogImportance)
            println("${importance.shorten()}/$logTag: ${message.trimEnd('\n')}\n")
    }
}

object NoLogger : Logger {
    override val logTag: String = "NoLogger"
    override val minLogImportance: Logger.Importance = Logger.Importance.INF
    override fun log(importance: Logger.Importance, message: String) { }
}

class AndroidLogger(
    override val logTag: String = "AndroidLogger",
    override val minLogImportance: Logger.Importance = Logger.Importance.VERBOSE
) : Logger {
    override fun log(importance: Logger.Importance, message: String) {
        if (importance >= minLogImportance)
            when (importance) {
                Logger.Importance.VERBOSE -> Log.v(logTag, message)
                Logger.Importance.DEBUG -> Log.d(logTag, message)
                Logger.Importance.INFO -> Log.i(logTag, message)
                Logger.Importance.WARNING -> Log.w(logTag, message)
                Logger.Importance.ERROR -> Log.e(logTag, message)
                Logger.Importance.INF -> throw Logger.Importance.InfImportanceException
            }
    }
}