package com.pierbezuhoff.clonium.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

interface Logger {
    enum class Level {
        VERBOSE, DEBUG, INFO, WARNING, ERROR, INF;
        fun shorten(): String =
            when (this) {
                VERBOSE -> "V"
                DEBUG -> "D"
                INFO -> "I"
                WARNING -> "W"
                ERROR -> "E"
                INF -> throw InfLevelException
            }
        object InfLevelException : IllegalArgumentException("Level.INF cannot be log-ged")
    }

    @DslMarker
    annotation class MilestoneScopeMarker
    @MilestoneScopeMarker
    interface MilestoneScope {
        /** Set start time for [name] */
        fun milestoneStartOf(name: String)
        /** Log elapsed time from previous [milestoneEndOf] or [milestoneStartOf] of [name] */
        fun milestoneEndOf(name: String)
        /** Synonym to [milestoneStartOf], set start time for [this] */
        operator fun String.unaryPlus() =
            milestoneStartOf(this)
        /** Synonym to [milestoneEndOf], log elapsed time from previous [milestoneEndOf] or [milestoneStartOf] of [this] */
        operator fun String.unaryMinus() =
            milestoneEndOf(this)
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
        startMarker: String? = "[",
        endMarker: String? = if (startMarker != null) "]" else null,
        block: () -> R
    ): R

    /**
     * Style 1:
     * ```
     * logIMilestoneScope(...) {
     *     val a = 1
     *     + "f1"
     *     f1(a)
     *     - "f1" // log elapsed time from '+' mark until this '-' mark
     *     val b = 2
     *     + "f2"
     *     f2(a, b, flag = true)
     *     - "f2"
     *     closeSomeFiles()
     *     - "ending" // log elapsed time from previous '-' mark until this '-' mark
     * }
     * ```
     * Style 2:
     * ```
     * logIMilestoneScope(...) {
     *     val a = 1
     *     f1(a)
     *     - "section f1" // log elapsed time from start of MilestoneScope until this '-' mark
     *     val b = 2
     *     f2(a, b, flag = true)
     *     - "section f2" // log elapsed time from previous '-' mark until this '-' mark
     *     closeSomeFiles()
     *     - "ending"
     * }
     * ```
     */
    fun logIMilestoneScope(
        scopeName: String = "MilestoneScope",
        milestonePrefix: String = "*",
        startMarker: String? = "(", endMarker: String? = if (startMarker != null) ")" else null,
        measureScope: Boolean = false,
        block: MilestoneScope.() -> Unit
    ) = logIMilestoneScopeWithResult(scopeName, milestonePrefix, startMarker, endMarker, measureScope, block)

    fun <R> logIMilestoneScopeWithResult(
        scopeName: String = "MilestoneScope",
        milestonePrefix: String = "*",
        startMarker: String? = null, endMarker: String? = null,
        measureScope: Boolean = false,
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
    protected val minLogLevel: Logger.Level
) : Logger {
    private var measuredCounter = 0

    private fun log(level: Logger.Level, message: String) {
        if (level >= minLogLevel)
            _log(level, message)
    }

    final override fun logV(message: String) = log(Logger.Level.VERBOSE, message)
    final override fun logD(message: String) = log(Logger.Level.DEBUG, message)
    final override fun logI(message: String) = log(Logger.Level.INFO, message)
    final override fun logW(message: String) = log(Logger.Level.WARNING, message)
    final override fun logE(message: String) = log(Logger.Level.ERROR, message)

    private inline fun <R> logElapsedTime(
        level: Logger.Level,
        prefix: String, postfix: String,
        depthMarker: String, startMarker: String?, endMarker: String?,
        block: () -> R
    ): R {
        startMarker?.let {
            log(level, depthMarker.repeat(measuredCounter) + startMarker)
        }
        measuredCounter ++
        val (elapsedPretty, result) = measureElapsedTimePretty(block)
        measuredCounter --
        log(level, depthMarker.repeat(measuredCounter) + (endMarker ?: "") + " $prefix $elapsedPretty $postfix")
        return result
    }

    final override fun <R> logIElapsedTime(prefix: String, postfix: String, depthMarker: String, startMarker: String?, endMarker: String?, block: () -> R): R =
        logElapsedTime(Logger.Level.INFO, prefix, postfix, depthMarker, startMarker, endMarker, block)

    private inline fun <R> logMilestoneScopeWithResult(
        level: Logger.Level,
        scopeName: String,
        milestonePrefix: String,
        startMarker: String?, endMarker: String?,
        measureScope: Boolean,
        block: Logger.MilestoneScope.() -> R
    ): R {
        startMarker?.takeIf { measureScope }?.let {
            log(level, "$startMarker $scopeName")
        }
        val milestoneScope = object : Logger.MilestoneScope {
            var startTime = System.currentTimeMillis()
            val startTimes: MutableMap<String, Milliseconds> = mutableMapOf()
            var previousMilestoneName = startMarker

            override fun milestoneStartOf(name: String) {
                startTimes[name] = System.currentTimeMillis()
                startMarker?.let {
                    log(level, "$milestonePrefix$startMarker")
                }
            }

            override fun milestoneEndOf(name: String) {
                val elapsed = System.currentTimeMillis() - (startTimes.remove(name) ?: startTime)
                val elapsedTime = ElapsedTime(elapsed, Unit)
                startTime = System.currentTimeMillis()
                log(level, "$milestonePrefix${endMarker ?: ""} $name: ${elapsedTime.prettyTime()}")
                previousMilestoneName = name
            }
        }
        val (elapsedPretty, result) = measureElapsedTimePretty { milestoneScope.block() }
        endMarker?.takeIf { measureScope }?.let {
            log(level, "$endMarker $scopeName: $elapsedPretty")
        }
        return result
    }

    final override fun <R> logIMilestoneScopeWithResult(scopeName: String, milestonePrefix: String, startMarker: String?, endMarker: String?, measureScope: Boolean, block: Logger.MilestoneScope.() -> R): R =
        logMilestoneScopeWithResult(Logger.Level.INFO, scopeName, milestonePrefix, startMarker, endMarker, measureScope, block)

    private suspend fun sLog(level: Logger.Level, message: String) {
        if (level >= minLogLevel)
            _sLog(level, message)
    }

    final override suspend fun sLogV(message: String) = sLog(Logger.Level.VERBOSE, message)
    final override suspend fun sLogD(message: String) = sLog(Logger.Level.DEBUG, message)
    final override suspend fun sLogI(message: String) = sLog(Logger.Level.INFO, message)
    final override suspend fun sLogW(message: String) = sLog(Logger.Level.WARNING, message)
    final override suspend fun sLogE(message: String) = sLog(Logger.Level.ERROR, message)

    abstract fun _log(level: Logger.Level, message: String)

    open suspend fun _sLog(level: Logger.Level, message: String) {
        _log(level, message)
    }
}

class StandardLogger(
    logTag: String = "StandardLogger",
    minLogLevel: Logger.Level = Logger.Level.VERBOSE
) : AbstractLogger(logTag, minLogLevel) {
    override fun _log(level: Logger.Level, message: String) {
        val printer =
            if (level >= Logger.Level.ERROR) System.err
            else System.out
        printer.println("${level.shorten()}/$logTag: ${message.trimEnd('\n')}\n")
    }
}

object NoLogger : AbstractLogger("NoLogger", Logger.Level.INF) {
    override fun _log(level: Logger.Level, message: String) { }
}

class AndroidLogger(
    logTag: String = "AndroidLogger",
    minLogLevel: Logger.Level = Logger.Level.VERBOSE
) : AbstractLogger(logTag, minLogLevel) {
    constructor(
        cls: KClass<*>, minLogLevel: Logger.Level = Logger.Level.VERBOSE
    ) : this(
        cls.simpleName ?: "<AnonymousClass>", minLogLevel
    )

    override fun _log(level: Logger.Level, message: String) {
        when (level) {
            Logger.Level.VERBOSE -> Log.v(logTag, message)
            Logger.Level.DEBUG -> Log.d(logTag, message)
            Logger.Level.INFO -> Log.i(logTag, message)
            Logger.Level.WARNING -> Log.w(logTag, message)
            Logger.Level.ERROR -> Log.e(logTag, message)
            Logger.Level.INF -> throw Logger.Level.InfLevelException
        }
    }

    override suspend fun _sLog(level: Logger.Level, message: String) {
        withContext(Dispatchers.Main) {
            this@AndroidLogger._log(level, message)
        }
    }
}

@Suppress("FunctionName")
inline fun <reified C> AndroidLoggerOf(minLogLevel: Logger.Level = Logger.Level.VERBOSE): AndroidLogger =
    AndroidLogger(C::class, minLogLevel)
