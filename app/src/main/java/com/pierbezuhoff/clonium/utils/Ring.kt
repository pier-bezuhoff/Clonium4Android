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

    fun focusOn(e: E): E {
        val i = elements.indexOf(e)
        if (i == -1)
            throw NoSuchElementException("Ring $this does not contain element $e")
        ix = i
        return focus
    }

    override fun toString(): String =
        when (size) {
            0 -> "ringOf()"
            1 -> "ringOf($focus)"
            else -> {
                val es = elements.drop(ix) + elements.take(ix)
                es.joinToString(prefix = "ringOf(", postfix = "\n)", separator = ",") { e -> "\n$e" }
            }
        }
}

fun <E> ringOf(vararg elements: E): Ring<E> =
    Ring(elements.toList())