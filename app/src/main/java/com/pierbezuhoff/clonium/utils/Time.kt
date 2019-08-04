package com.pierbezuhoff.clonium.utils

typealias Milliseconds = Long

data class TimeMilis<A>(val elapsed: Milliseconds, val result: A)

inline fun <A> measureTimeMillisWithResult(block: () -> A): TimeMilis<A> {
    val startTime = System.currentTimeMillis()
    val a = block()
    val elapsed = System.currentTimeMillis() - startTime
    return TimeMilis(elapsed, a)
}