@file:Suppress("ClassName")

package com.pierbezuhoff.clonium.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

// TODO: document WithLog operators and Logger.Command's constructors

private typealias Name = String
private typealias Tag = Name
private typealias ScopeName = Name
private typealias SectionName = Name
private typealias Message = String

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
    interface Command<R>

    fun log(level: Level, message: Message)
    suspend fun sLog(level: Level, message: Message)

    fun <R> logElapsedTime(
        level: Level,
        prefix: String,
        postfix: String,
        depthMarker: String,
        startMarker: String?,
        endMarker: String?,
        block: () -> R
    ): R

    /**
     * Start/end style:
     * ```
     * logMilestoneScope(...) {
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
     * logMilestoneScope(...) {
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
    fun <R> logMilestoneScope(
        level: Level,
        scopeName: ScopeName,
        milestonePrefix: String,
        startMarker: String?, endMarker: String?,
        measureScope: Boolean,
        block: MilestoneScope.() -> R
    ): R

    @Suppress("UNCHECKED_CAST")
    fun <R> perform(level: Level, command: Command<R>): R =
        when (command) {
            is elapsedTime -> with(command) { logElapsedTime(level, prefix, postfix, depthMarker, startMarker, endMarker, block) }
            is withMilestoneScope -> with(command) { logMilestoneScope(level, scopeName, milestonePrefix, startMarker, endMarker, measureScope, block) }
            is withStaggeredScope -> with(command) { logStaggeredScope(level, scopeName, prefix, startMarker, endMarker, measureScope, block) }
            is staggeredStartOf -> staggeredStartOf(level, command.scopeName, command.sectionName) as R
            is staggeredEndOf -> staggeredEndOf(command.scopeName, command.sectionName) as R
            else -> impossibleCaseOf(command)
        }
}

interface StaggeredScoping {
    fun <R> logStaggeredScope(
        level: Logger.Level,
        scopeName: ScopeName,
        prefix: String,
        startMarker: String?, endMarker: String?,
        measureScope: Boolean,
        block: () -> R
    ): R
    fun staggeredStartOf(level: Logger.Level = Logger.Level.INFO, scopeName: ScopeName? = null, sectionName: SectionName)
    fun staggeredEndOf(scopeName: ScopeName? = null, sectionName: SectionName)
    operator fun SectionName.unaryPlus() =
        staggeredStartOf(sectionName = this)
    operator fun SectionName.unaryMinus() =
        staggeredEndOf(sectionName = this)
    operator fun ScopeName.plusAssign(sectionName: SectionName) =
        staggeredStartOf(scopeName = this, sectionName = sectionName)
    operator fun ScopeName.minusAssign(sectionName: SectionName) =
        staggeredEndOf(this, sectionName)
}

private class StaggeredScope(
    val level: Logger.Level = Logger.Level.INFO,
    val scopeName: ScopeName? = null,
    val prefix: String = "#",
    val startMarker: String? = null, val endMarker: String? = null
) {
    var lastTime: Nanoseconds = 0
    val sections: MutableMap<SectionName, Nanoseconds> = mutableMapOf()

    override fun equals(other: Any?) =
        other is StaggeredScope && scopeName == other.scopeName
    override fun hashCode()=
        scopeName.hashCode()
}

abstract class AbstractLogger(
    protected val logTag: Tag,
    protected val minLogLevel: Logger.Level
) : Logger {
    private var measuredCounter = 0
    private val defaultStaggeredScope: StaggeredScope = StaggeredScope()
    private val staggeredScopes: MutableSet<StaggeredScope> = mutableSetOf()

    private val elapsedTimeAverages: MutableMap<String, AverageTime> = mutableMapOf()
    private val milestoneScopeAverages: MutableMap<Pair<ScopeName, SectionName>, AverageTime> = mutableMapOf()
    private val staggeredScopeAverages: MutableMap<Pair<ScopeName?, SectionName>, AverageTime> = mutableMapOf()

    override fun log(level: Logger.Level, message: Message) {
        if (level >= minLogLevel)
            _log(level, message)
    }

    abstract fun _log(level: Logger.Level, message: Message)

    override suspend fun sLog(level: Logger.Level, message: Message) {
        if (level >= minLogLevel)
            _sLog(level, message)
    }

    open suspend fun _sLog(level: Logger.Level, message: Message) {
        _log(level, message)
    }

    override fun <R> logElapsedTime(
        level: Logger.Level,
        prefix: String, postfix: String,
        depthMarker: String, startMarker: String?, endMarker: String?,
        block: () -> R
    ): R {
        startMarker?.let {
            log(level, depthMarker.repeat(measuredCounter) + startMarker)
        }
        measuredCounter ++
        val (elapsedPretty, result, inNanoseconds) = measureElapsedTimePretty(block)
        val average = elapsedTimeAverages.getOrPut(prefix + postfix) { AverageTime() }
        average += inNanoseconds
        measuredCounter --
        val depthIndent = depthMarker.repeat(measuredCounter)
        log(level, depthIndent + (endMarker ?: "") + " $prefix $elapsedPretty $postfix" + average.toModestString())
        return result
    }

    override  fun <R> logMilestoneScope(
        level: Logger.Level,
        scopeName: ScopeName,
        milestonePrefix: String,
        startMarker: String?, endMarker: String?,
        measureScope: Boolean,
        block: Logger.MilestoneScope.() -> R
    ): R {
        startMarker?.takeIf { measureScope }?.let {
            log(level, "$startMarker $scopeName")
        }
        val milestoneScope = object : Logger.MilestoneScope {
            var startTime = System.nanoTime()
            val startTimes: MutableMap<String, Nanoseconds> = mutableMapOf()
            var previousMilestoneName = startMarker

            override fun milestoneStartOf(name: SectionName) {
                startTimes[name] = System.nanoTime()
                startMarker?.let {
                    log(level, "$milestonePrefix$startMarker")
                }
            }

            override fun milestoneEndOf(name: SectionName) {
                val elapsed = System.nanoTime() - (startTimes.remove(name) ?: startTime)
                val elapsedTime = ElapsedTime(elapsed, Unit)
                val average = milestoneScopeAverages.getOrPut(scopeName to name) { AverageTime() }
                average += elapsedTime.inNanoseconds
                log(level, "$milestonePrefix${endMarker ?: ""} $name: $elapsedTime" + average.toModestString())
                startTime = System.nanoTime()
                previousMilestoneName = name
            }
        }
        val (elapsedPretty, result, inNanoseconds) = measureElapsedTimePretty { milestoneScope.block() }
        endMarker?.takeIf { measureScope }?.let {
            val average = milestoneScopeAverages.getOrPut(scopeName to scopeName) { AverageTime() }
            average += inNanoseconds
            log(level, "$endMarker $scopeName: $elapsedPretty" + average.toModestString())
        }
        return result
    }

    override fun <R> logStaggeredScope(level: Logger.Level, scopeName: ScopeName, prefix: String, startMarker: String?, endMarker: String?, measureScope: Boolean, block: () -> R): R {
        val scope = StaggeredScope(level, scopeName, prefix, startMarker, endMarker)
        startMarker?.takeIf { measureScope }?.let {
            log(level, "$startMarker $scopeName")
        }
        staggeredScopes += scope
        val (elapsedPretty, r, inNanoseconds) = measureElapsedTimePretty(block)
        staggeredScopes -= scope
        endMarker?.takeIf { measureScope }?.let {
            val average = staggeredScopeAverages.getOrPut(scopeName to scopeName) { AverageTime() }
            average += inNanoseconds
            log(level, "$endMarker $scopeName: $elapsedPretty" + average.toModestString())
        }
        return r
    }
    override fun staggeredStartOf(level: Logger.Level, scopeName: ScopeName?, sectionName: SectionName) {
        val scope =
            if (scopeName == null) defaultStaggeredScope
            else staggeredScopes.find { it.scopeName == scopeName } ?: run {
                StaggeredScope(level, scopeName).also { staggeredScopes += it }
            }
        scope.sections[sectionName] = System.nanoTime()
        scope.startMarker?.let {
            log(scope.level, "${scope.prefix}${scope.startMarker}")
        }
    }

    override fun staggeredEndOf(scopeName: ScopeName?, sectionName: SectionName) {
        val scope = staggeredScopes.find { it.scopeName == scopeName } ?: defaultStaggeredScope
        val time = System.nanoTime()
        val elapsed = time - (scope.sections[sectionName] ?: scope.lastTime)
        scope.sections -= sectionName
        scope.lastTime = time
        val elapsedTime = ElapsedTime(elapsed, Unit)
        val average = staggeredScopeAverages.getOrPut(scope.scopeName to sectionName) { AverageTime() }
        average += elapsedTime.inNanoseconds
        log(scope.level, "${scope.prefix}${scope.endMarker ?: ""} ${scopeName ?: ""}/$sectionName: $elapsedTime" + average.toModestString())
    }
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


/** log [message] on main thread */
data class s(
    val message: Message
)

data class elapsedTime<R>(
    val prefix: String = "elapsed:",
    val postfix: String = "",
    val depthMarker: String = "-",
    val startMarker: String? = "[",
    val endMarker: String? = if (startMarker != null) "]" else null,
    val block: () -> R
) : Logger.Command<R>

data class withMilestoneScope<R>(
    val scopeName: ScopeName = "MilestoneScope",
    val milestonePrefix: String = "*",
    val startMarker: String? = null,
    val endMarker: String? = null,
    val measureScope: Boolean = false,
    val block: Logger.MilestoneScope.() -> R
) : Logger.Command<R>

data class withStaggeredScope<R>(
    val level: Logger.Level,
    val scopeName: ScopeName,
    val prefix: String = "#",
    val startMarker: String? = null,
    val endMarker: String? = null,
    val measureScope: Boolean = false,
    val block: () -> R
) : Logger.Command<R>

data class staggeredStartOf(
    val level: Logger.Level = Logger.Level.INFO,
    val scopeName: ScopeName? = null,
    val sectionName: SectionName
) : Logger.Command<Unit>

data class staggeredEndOf(
    val scopeName: ScopeName? = null,
    val sectionName: SectionName
) : Logger.Command<Unit>

interface WithLog {
    val log: Logger

    infix fun Logger.v(message: Message) =
        log(Logger.Level.VERBOSE, message)
    infix fun Logger.d(message: Message) =
        log(Logger.Level.DEBUG, message)
    infix fun Logger.i(message: Message) =
        log(Logger.Level.INFO, message)
    infix fun Logger.w(message: Message) =
        log(Logger.Level.WARNING, message)
    infix fun Logger.e(message: Message) =
        log(Logger.Level.ERROR, message)

    suspend infix fun Logger.v(s: s) =
        sLog(Logger.Level.VERBOSE, s.message)
    suspend infix fun Logger.d(s: s) =
        sLog(Logger.Level.DEBUG, s.message)
    suspend infix fun Logger.i(s: s) =
        sLog(Logger.Level.INFO, s.message)
    suspend infix fun Logger.w(s: s) =
        sLog(Logger.Level.WARNING, s.message)
    suspend infix fun Logger.e(s: s) =
        sLog(Logger.Level.ERROR, s.message)

    infix fun <R> Logger.v(command: Logger.Command<R>): R =
        perform(Logger.Level.VERBOSE, command)
    infix fun <R> Logger.d(command: Logger.Command<R>): R =
        perform(Logger.Level.DEBUG, command)
    infix fun <R> Logger.i(command: Logger.Command<R>): R =
        perform(Logger.Level.INFO, command)
    infix fun <R> Logger.w(command: Logger.Command<R>): R =
        perform(Logger.Level.WARNING, command)
    infix fun <R> Logger.e(command: Logger.Command<R>): R =
        perform(Logger.Level.ERROR, command)

    operator fun SectionName.unaryPlus() =
        log.staggeredStartOf(sectionName = this)
    operator fun SectionName.unaryMinus() =
        log.staggeredEndOf(sectionName = this)
    operator fun ScopeName.plusAssign(sectionName: SectionName) =
        log.staggeredStartOf(scopeName = this, sectionName = sectionName)
    operator fun ScopeName.minusAssign(sectionName: SectionName) =
        log.staggeredEndOf(this, sectionName)
}

data class LogHolder(override val log: Logger) : WithLog

@Suppress("FunctionName")
inline fun <reified C> AndroidLogOf(minLogLevel: Logger.Level = Logger.Level.VERBOSE): LogHolder =
    LogHolder(AndroidLoggerOf<C>(minLogLevel))

@Suppress("FunctionName")
fun AndroidLogOf(logTag: Tag, minLogLevel: Logger.Level = Logger.Level.VERBOSE): LogHolder =
    LogHolder(AndroidLogger(logTag, minLogLevel))
