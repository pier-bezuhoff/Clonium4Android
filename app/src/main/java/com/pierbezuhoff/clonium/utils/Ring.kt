package com.pierbezuhoff.clonium.utils

class Ring<E>(private val elements: List<E>) {
    private val size = elements.size
    private var ix = 0
    val focus: E
        get() = elements[ix]

    fun next(): E {
        ix = (ix + 1) % size
        return focus
    }

    fun previous(): E {
        ix = (ix - 1 + size) % size
        return focus
    }
}

fun <E> ringOf(vararg elements: E): Ring<E> =
    Ring(elements.toList())