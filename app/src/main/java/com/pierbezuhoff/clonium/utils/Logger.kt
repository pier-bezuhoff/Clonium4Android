package com.pierbezuhoff.clonium.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

private typealias Name = String
private typealias Tag = Name
private typealias ScopeName = Name
private typealias SectionName = Name
private typealias Message = String

// MAYBE: mk LoggerHolder interface with val logger: Logger

interface Logger : StaggeredScoping {
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
        fun milestoneStartOf(name: SectionName)
        /** Log elapsed time from previous [milestoneEndOf] or [milestoneStartOf] of [name] */
        fun milestoneEndOf(name: SectionName)
        /** Synonym to [milestoneStartOf], set start time for [this] */
        operator fun SectionName.unaryPlus() =
            milestoneStartOf(this)
        /** Synonym to [milestoneEndOf], log elapsed time from previous [milestoneEndOf] or [milestoneStartOf] of [this] */
        operator fun SectionName.unaryMinus() =
            milestoneEndOf(this)
    }

    val V: Level
    val D: Level
    val I: Level
    val W: Level
    val E: Level

    infix fun Level.log(message: Message)
    infix fun Message.log(level: Level) =
        level.log(this)

    fun logV(message: Message)
    fun logD(message: Message)
    fun logI(message: Message)
    fun logW(message: Message)
    fun logE(message: Message)

    fun <R> logIElapsedTime(
        prefix: String = "elapsed:",
        postfix: String = "",
        depthMarker: String = "-",
        startMarker: String? = "[",
        endMarker: String? = if (startMarker != null) "]" else null,
        block: () -> R
    ): R

    /**
     * Start/end style:
     * ```
     * withMilestoneScope(...) {
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
     * Section style:
     * ```
     * withMilestoneScope(...) {
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
    fun <R> withMilestoneScope(
        scopeName: ScopeName = "MilestoneScope",
        milestonePrefix: String = "*",
        startMarker: String? = null, endMarker: String? = null,
        measureScope: Boolean = false,
        block: MilestoneScope.() -> R
    ): R

    suspend fun sLogV(message: Message)
    suspend fun sLogD(message: Message)
    suspend fun sLogI(message: Message)
    suspend fun sLogW(message: Message)
    suspend fun sLogE(message: Message)
}

abstract class AbstractLogger(
    protected val logTag: Tag,
    protected val minLogLevel: Logger.Level
) : Logger {
    private var measuredCounter = 0
    private val defaultStaggeredScope: StaggeredScope = StaggeredScope()
    private val staggeredScopes: MutableSet<StaggeredScope> = mutableSetOf()

    override val V = Logger.Level.VERBOSE
    override val D = Logger.Level.DEBUG
    override val I = Logger.Level.INFO
    override val W = Logger.Level.WARNING
    override val E = Logger.Level.DEBUG

    override fun Logger.Level.log(message: Message) =
        log_(this, message)

    private fun log_(level: Logger.Level, message: Message) {
        if (level >= minLogLevel)
            _log(level, message)
    }

    final override fun logV(message: Message) = log_(Logger.Level.VERBOSE, message)
    final override fun logD(message: Message) = log_(Logger.Level.DEBUG, message)
    final override fun logI(message: Message) = log_(Logger.Level.INFO, message)
    final override fun logW(message: Message) = log_(Logger.Level.WARNING, message)
    final override fun logE(message: Message) = log_(Logger.Level.ERROR, message)

    private inline fun <R> logElapsedTime(
        level: Logger.Level,
        prefix: String, postfix: String,
        depthMarker: String, startMarker: String?, endMarker: String?,
        block: () -> R
    ): R {
        startMarker?.let {
            log_(level, depthMarker.repeat(measuredCounter) + startMarker)
        }
        measuredCounter ++
        val (elapsedPretty, result) = measureElapsedTimePretty(block)
        measuredCounter --
        log_(level, depthMarker.repeat(measuredCounter) + (endMarker ?: "") + " $prefix $elapsedPretty $postfix")
        return result
    }

    final override fun <R> logIElapsedTime(prefix: String, postfix: String, depthMarker: String, startMarker: String?, endMarker: String?, block: () -> R): R =
        logElapsedTime(Logger.Level.INFO, prefix, postfix, depthMarker, startMarker, endMarker, block)

    private inline fun <R> withMilestoneScope(
        level: Logger.Level,
        scopeName: ScopeName,
        milestonePrefix: String,
        startMarker: String?, endMarker: String?,
        measureScope: Boolean,
        block: Logger.MilestoneScope.() -> R
    ): R {
        startMarker?.takeIf { measureScope }?.let {
            log_(level, "$startMarker $scopeName")
        }
        val milestoneScope = object : Logger.MilestoneScope {
            var startTime = System.currentTimeMillis()
            val startTimes: MutableMap<String, Milliseconds> = mutableMapOf()
            var previousMilestoneName = startMarker

            override fun milestoneStartOf(name: SectionName) {
                startTimes[name] = System.currentTimeMillis()
                startMarker?.let {
                    log_(level, "$milestonePrefix$startMarker")
                }
            }

            override fun milestoneEndOf(name: SectionName) {
                val elapsed = System.currentTimeMillis() - (startTimes.remove(name) ?: startTime)
                val elapsedTime = ElapsedTime(elapsed, Unit)
                startTime = System.currentTimeMillis()
                log_(level, "$milestonePrefix${endMarker ?: ""} $name: ${elapsedTime.prettyTime()}")
                previousMilestoneName = name
            }
        }
        val (elapsedPretty, result) = measureElapsedTimePretty { milestoneScope.block() }
        endMarker?.takeIf { measureScope }?.let {
            log_(level, "$endMarker $scopeName: $elapsedPretty")
        }
        return result
    }

    final override fun <R> withMilestoneScope(scopeName: ScopeName, milestonePrefix: String, startMarker: String?, endMarker: String?, measureScope: Boolean, block: Logger.MilestoneScope.() -> R): R =
        withMilestoneScope(Logger.Level.INFO, scopeName, milestonePrefix, startMarker, endMarker, measureScope, block)

    override fun <R> withStaggeredScope(scopeName: ScopeName, prefix: String, startMarker: String?, endMarker: String?, measureScope: Boolean, block: () -> R): R {
        val scope = StaggeredScope(scopeName, prefix, startMarker, endMarker, measureScope)
        startMarker?.takeIf { measureScope }?.let {
            logI("$startMarker $scopeName")
        }
        staggeredScopes += scope
        val (elapsedPretty, r) = measureElapsedTimePretty(block)
        staggeredScopes -= scope
        endMarker?.takeIf { measureScope }?.let {
            logI("$endMarker $scopeName: $elapsedPretty")
        }
        return r
    }
    override fun staggeredStartOf(scopeName: ScopeName?, sectionName: SectionName) {
        val scope =
            if (scopeName == null) defaultStaggeredScope
            else staggeredScopes.find { it.scopeName == scopeName } ?: run {
                StaggeredScope(scopeName).also { staggeredScopes += it }
            }
        scope.sections[sectionName] = System.currentTimeMillis()
        scope.startMarker?.let {
            logI("${scope.prefix}${scope.startMarker}")
        }
    }

    override fun staggeredEndOf(scopeName: ScopeName?, sectionName: SectionName) {
        val scope = staggeredScopes.find { it.scopeName == scopeName } ?: defaultStaggeredScope
        val time = System.currentTimeMillis()
        val elapsed = time - (scope.sections[sectionName] ?: scope.lastTime)
        scope.sections -= sectionName
        scope.lastTime = time
        logI("${scope.prefix}${scope.endMarker ?: ""} ${scopeName ?: ""}/$sectionName: ${ElapsedTime(elapsed, Unit).prettyTime()}")
    }

    private suspend fun sLog(level: Logger.Level, message: Message) {
        if (level >= minLogLevel)
            _sLog(level, message)
    }

    final override suspend fun sLogV(message: Message) = sLog(Logger.Level.VERBOSE, message)
    final override suspend fun sLogD(message: Message) = sLog(Logger.Level.DEBUG, message)
    final override suspend fun sLogI(message: Message) = sLog(Logger.Level.INFO, message)
    final override suspend fun sLogW(message: Message) = sLog(Logger.Level.WARNING, message)
    final override suspend fun sLogE(message: Message) = sLog(Logger.Level.ERROR, message)

    abstract fun _log(level: Logger.Level, message: Message)

    open suspend fun _sLog(level: Logger.Level, message: Message) {
        _log(level, message)
    }
}

interface StaggeredScoping {
    fun <R> withStaggeredScope(
        scopeName: ScopeName,
        prefix: String = "#",
        startMarker: String? = null, endMarker: String? = null,
        measureScope: Boolean = false,
        block: () -> R
    ): R
    fun staggeredStartOf(scopeName: ScopeName? = null, sectionName: SectionName)
    fun staggeredEndOf(scopeName: ScopeName? = null, sectionName: SectionName)
    operator fun SectionName.unaryPlus() =
        staggeredStartOf(sectionName = this)
    operator fun SectionName.unaryMinus() =
        staggeredEndOf(sectionName = this)
    operator fun ScopeName.plusAssign(sectionName: SectionName) =
        staggeredStartOf(this, sectionName)
    operator fun ScopeName.minusAssign(sectionName: SectionName) =
        staggeredEndOf(this, sectionName)
}

private class StaggeredScope(
    val scopeName: ScopeName? = null,
    val prefix: String = "#",
    val startMarker: String? = null, val endMarker: String? = null,
    val measureScope: Boolean = false
) {
    var lastTime: Milliseconds = 0L
    val sections: MutableMap<SectionName, Milliseconds> = mutableMapOf()

    override fun equals(other: Any?) =
        other is StaggeredScope && scopeName == other.scopeName
    override fun hashCode()=
        scopeName.hashCode()
}

class StandardLogger(
    logTag: Tag = "StandardLogger",
    minLogLevel: Logger.Level = Logger.Level.VERBOSE
) : AbstractLogger(logTag, minLogLevel) {
    override fun _log(level: Logger.Level, message: Message) {
        val printer =
            if (level >= Logger.Level.ERROR) System.err
            else System.out
        printer.println("${level.shorten()}/$logTag: ${message.trimEnd('\n')}\n")
    }
}

object NoLogger : AbstractLogger("NoLogger", Logger.Level.INF) {
    override fun _log(level: Logger.Level, message: Message) { }
}

class AndroidLogger(
    logTag: Tag = "AndroidLogger",
    minLogLevel: Logger.Level = Logger.Level.VERBOSE
) : AbstractLogger(logTag, minLogLevel) {
    constructor(
        cls: KClass<*>, minLogLevel: Logger.Level = Logger.Level.VERBOSE
    ) : this(
        cls.simpleName ?: "<AnonymousClass>", minLogLevel
    )

    override fun _log(level: Logger.Level, message: Message) {
        when (level) {
            Logger.Level.VERBOSE -> Log.v(logTag, message)
            Logger.Level.DEBUG -> Log.d(logTag, message)
            Logger.Level.INFO -> Log.i(logTag, message)
            Logger.Level.WARNING -> Log.w(logTag, message)
            Logger.Level.ERROR -> Log.e(logTag, message)
            Logger.Level.INF -> throw Logger.Level.InfLevelException
        }
    }

    override suspend fun _sLog(level: Logger.Level, message: Message) {
        withContext(Dispatchers.Main) {
            this@AndroidLogger._log(level, message)
        }
    }
}

@Suppress("FunctionName")
inline fun <reified C> AndroidLoggerOf(minLogLevel: Logger.Level = Logger.Level.VERBOSE): AndroidLogger =
    AndroidLogger(C::class, minLogLevel)

interface WithLog {
    val log: Logger

    infix fun Logger.v(message: Message) =
        logV(message)
    infix fun Logger.d(message: Message) =
        logD(message)
    infix fun Logger.i(message: Message) =
        logI(message)
    infix fun Logger.w(message: Message) =
        logW(message)
    infix fun Logger.e(message: Message) =
        logE(message)
}

data class LogHolder(override val log: Logger) : WithLog

@Suppress("FunctionName")
inline fun <reified C> AndroidLogOf(minLogLevel: Logger.Level = Logger.Level.VERBOSE): LogHolder =
    LogHolder(AndroidLoggerOf<C>(minLogLevel))