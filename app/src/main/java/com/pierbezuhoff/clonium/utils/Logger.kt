package com.pierbezuhoff.clonium.utils

import android.util.Log
import androidx.appcompat.widget.MenuItemHoverListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
    interface MilestoneScope {
        fun milestone(name: String)
        fun milestoneEndOf(name: String)
    }

    fun logV(message: String)
    fun logD(message: String)
    fun logI(message: String)
    fun logW(message: String)
    fun logE(message: String)

    fun <R> logIElapsedTime(
        prefix: String = "elapsed:",
        postfix: String = "",
        depthMarker: String = "-",
        startMarker: String = "[",
        endMarker: String = "]",
        block: () -> R
    ): R
    fun <R> logIMilestoneScope(
        scopeName: String = "MilestoneScope",
        milestonePrefix: String = "*",
        startMarker: String = "{",
        endMarker: String = "}",
        block: MilestoneScope.() -> R
    ): R

    suspend fun sLogV(message: String)
    suspend fun sLogD(message: String)
    suspend fun sLogI(message: String)
    suspend fun sLogW(message: String)
    suspend fun sLogE(message: String)
}

abstract class AbstractLogger(
    protected val logTag: String,
    protected val minLogImportance: Logger.Importance
) : Logger {
    private var measuredCounter = 0

    private fun log(importance: Logger.Importance, message: String) {
        if (importance >= minLogImportance)
            _log(importance, message)
    }

    override fun logV(message: String) = log(Logger.Importance.VERBOSE, message)
    override fun logD(message: String) = log(Logger.Importance.DEBUG, message)
    override fun logI(message: String) = log(Logger.Importance.INFO, message)
    override fun logW(message: String) = log(Logger.Importance.WARNING, message)
    override fun logE(message: String) = log(Logger.Importance.ERROR, message)

    private inline fun <R> logElapsedTime(
        importance: Logger.Importance,
        prefix: String, postfix: String,
        depthMarker: String, startMarker: String, endMarker: String,
        block: () -> R
    ): R {
        log(importance, depthMarker.repeat(measuredCounter) + startMarker)
        measuredCounter ++
        val (elapsedPretty, result) = measureElapsedTimePretty(block)
        measuredCounter --
        log(importance, depthMarker.repeat(measuredCounter) + endMarker + " $prefix $elapsedPretty $postfix")
        return result
    }

    override fun <R> logIElapsedTime(prefix: String, postfix: String, depthMarker: String, startMarker: String, endMarker: String, block: () -> R): R =
        logElapsedTime(Logger.Importance.INFO, prefix, postfix, depthMarker, startMarker, endMarker, block)

    private inline fun <R> logMilestoneScope(
        importance: Logger.Importance,
        scopeName: String,
        milestonePrefix: String,
        startMarker: String, endMarker: String,
        block: Logger.MilestoneScope.() -> R
    ): R {
        log(importance, "$startMarker $scopeName")
        val milestoneScope = object : Logger.MilestoneScope {
            var startTime = System.currentTimeMillis()
            var previousMilestoneName = startMarker

            override fun milestone(name: String) {
                val elapsedTime = ElapsedTime(System.currentTimeMillis() - startTime, Unit)
                startTime = System.currentTimeMillis()
                log(importance, "$milestonePrefix ($previousMilestoneName >> $name): ${elapsedTime.prettyTime()}")
                previousMilestoneName = name
            }

            override fun milestoneEndOf(name: String) {
                val elapsedTime = ElapsedTime(System.currentTimeMillis() - startTime, Unit)
                startTime = System.currentTimeMillis()
                log(importance, "$milestonePrefix $name: ${elapsedTime.prettyTime()}")
                previousMilestoneName = name
            }
        }
        val (elapsedPretty, result) = measureElapsedTimePretty { milestoneScope.block() }
        log(importance, "$endMarker $scopeName: $elapsedPretty")
        return result
    }

    override fun <R> logIMilestoneScope(scopeName: String, milestonePrefix: String, startMarker: String, endMarker: String, block: Logger.MilestoneScope.() -> R): R =
        logMilestoneScope(Logger.Importance.INFO, scopeName, milestonePrefix, startMarker, endMarker, block)

    private suspend fun sLog(importance: Logger.Importance, message: String) {
        if (importance >= minLogImportance)
            _sLog(importance, message)
    }

    override suspend fun sLogV(message: String) = sLog(Logger.Importance.VERBOSE, message)
    override suspend fun sLogD(message: String) = sLog(Logger.Importance.DEBUG, message)
    override suspend fun sLogI(message: String) = sLog(Logger.Importance.INFO, message)
    override suspend fun sLogW(message: String) = sLog(Logger.Importance.WARNING, message)
    override suspend fun sLogE(message: String) = sLog(Logger.Importance.ERROR, message)

    abstract fun _log(importance: Logger.Importance, message: String)

    open suspend fun _sLog(importance: Logger.Importance, message: String) {
        _log(importance, message)
    }
}

class StandardLogger(
    logTag: String = "StandardLogger",
    minLogImportance: Logger.Importance = Logger.Importance.VERBOSE
) : AbstractLogger(logTag, minLogImportance) {
    override fun _log(importance: Logger.Importance, message: String) {
        println("${importance.shorten()}/$logTag: ${message.trimEnd('\n')}\n")
    }
}

object NoLogger : AbstractLogger("NoLogger", Logger.Importance.INF) {
    override fun _log(importance: Logger.Importance, message: String) { }
}

class AndroidLogger(
    logTag: String = "AndroidLogger",
    minLogImportance: Logger.Importance = Logger.Importance.VERBOSE
) : AbstractLogger(logTag, minLogImportance) {
    override fun _log(importance: Logger.Importance, message: String) {
        when (importance) {
            Logger.Importance.VERBOSE -> Log.v(logTag, message)
            Logger.Importance.DEBUG -> Log.d(logTag, message)
            Logger.Importance.INFO -> Log.i(logTag, message)
            Logger.Importance.WARNING -> Log.w(logTag, message)
            Logger.Importance.ERROR -> Log.e(logTag, message)
            Logger.Importance.INF -> throw Logger.Importance.InfImportanceException
        }
    }

    override suspend fun _sLog(importance: Logger.Importance, message: String) {
        withContext(Dispatchers.Main) {
            this@AndroidLogger._log(importance, message)
        }
    }
}