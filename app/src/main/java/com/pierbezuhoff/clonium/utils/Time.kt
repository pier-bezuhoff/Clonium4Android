package com.pierbezuhoff.clonium.utils

import kotlin.math.roundToInt
import kotlin.math.roundToLong

typealias Minutes = Int
typealias Seconds = Int
typealias Milliseconds = Long
typealias Nanoseconds = Long

data class ElapsedTime<A>(val inNanoseconds: Nanoseconds, val result: A) {
    // milli, micro, nano
    val inMilliseconds: Milliseconds = (inNanoseconds / 1e6f).roundToLong()
    val inSeconds: Seconds = (inNanoseconds / 1e9f).roundToInt()
    val inMinutes: Minutes = (inNanoseconds / 60e9f).roundToInt()

    override fun toString(): String {
        val nanoseconds: Nanoseconds = inNanoseconds % 1_000_000
        val milliseconds: Milliseconds = inMilliseconds % 1000
        val seconds: Seconds = inSeconds % 60
        val minutes: Minutes = inMinutes
        val showMinutes = minutes > 0
        val showSeconds = seconds > 0 || showMinutes
        val showMilliseconds = !showMinutes && milliseconds > 0
        val showNanoseconds = !showMinutes && !showSeconds && !showMilliseconds
        val m = if (showMinutes) "${minutes}m " else ""
        val s = if (showSeconds) "${seconds}s " else ""
        val ms = if (showMilliseconds) {
            if (showSeconds || milliseconds >= 100)
                "${milliseconds}ms"
            else "%.3f".format(milliseconds + nanoseconds * 1e-6f) + "ms"
        } else ""
        val ns = if (showNanoseconds) "${nanoseconds * 1e-6f}ms" else ""
        return m + s + ms + ns
    }
}

data class PrettyTimeAndResult<A>(val prettyTime: String, val result: A, val inNanoseconds: Nanoseconds)

inline fun <A> measureElapsedTimePretty(block: () -> A): PrettyTimeAndResult<A> {
    val startTime = System.nanoTime()
    val result = block()
    val inNanoseconds = System.nanoTime() - startTime
    val elapsedTime = ElapsedTime(inNanoseconds, result)
    return PrettyTimeAndResult(elapsedTime.toString(), elapsedTime.result, inNanoseconds)
}

class AverageTime {
    private var sum: Nanoseconds = 0 // NOTE: can it overflow?
    var n: Int = 0
        private set
    val time: ElapsedTime<Unit>?
        get() =
            if (n == 0) ElapsedTime(sum, Unit)
            else ElapsedTime(sum / n, Unit)

    operator fun plusAssign(nextTime: Nanoseconds) {
        sum += nextTime
        n += 1
    }

    fun toModestString(): String =
        if (n > 1) " (average ${time})" else ""
}