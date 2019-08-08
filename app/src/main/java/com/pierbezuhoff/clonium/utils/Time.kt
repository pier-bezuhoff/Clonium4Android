package com.pierbezuhoff.clonium.utils

typealias Milliseconds = Long

data class ElapsedTime<A>(val elapsed: Milliseconds, val result: A) {
    val elapsedSeconds = elapsed / 1_000f
    val elapsedMinutes = elapsedSeconds / 60f
    fun prettyTime(): String {
        val elapsedSeconds = (elapsed / 1_000) % 60
        val elapsedMinutes = elapsed / 60_000
        val m = if (elapsedMinutes > 0) "${elapsedMinutes}m " else ""
        val s = if (elapsedSeconds > 0) "${elapsedSeconds}s " else ""
        val ms = "${elapsed % 1_000}ms"
        return m + s + ms
    }
}

inline fun <A> measureElapsedTime(block: () -> A): ElapsedTime<A> {
    val startTime = System.currentTimeMillis()
    val result = block()
    val elapsed = System.currentTimeMillis() - startTime
    return ElapsedTime(elapsed, result)
}

data class PrettyElapsedTime<A>(val prettyElapsed: String, val result: A)

inline fun <A> measureElapsedTimePretty(block: () -> A): PrettyElapsedTime<A> {
    val elapsedTime = measureElapsedTime(block)
    return PrettyElapsedTime(elapsedTime.prettyTime(), elapsedTime.result)
}
