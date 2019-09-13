package com.pierbezuhoff.clonium.utils

import kotlin.math.roundToInt
import kotlin.math.roundToLong

abstract class Average<X : Number, Y> {
    private var sum: Double = 0.0
    var n: Long = 0
        private set
    val value: Y?
        get() = if (n == 0L) null else fromDouble(sum / n)

    operator fun plusAssign(x: X) {
        sum += x.toDouble()
        n += 1
    }

    abstract fun fromDouble(double: Double): Y
}

class AverageInt : Average<Int, Int>() {
    override fun fromDouble(double: Double): Int =
        double.roundToInt()
}

class AverageLong : Average<Long, Long>() {
    override fun fromDouble(double: Double): Long =
        double.roundToLong()
}