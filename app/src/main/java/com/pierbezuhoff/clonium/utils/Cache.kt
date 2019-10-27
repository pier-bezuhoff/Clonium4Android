package com.pierbezuhoff.clonium.utils

// should be reflexive, symmetric, transitive
typealias Differ<P> = (P, P) -> Boolean

class Cached<P, V>(
    private var param: P? = null,
    private val create: (P) -> V,
    private val differ: Differ<P>
) {
    private var value: V? = null

    operator fun get(newParam: P): V =
        retrieve(newParam)

    fun retrieve(newParam: P): V =
        synchronized(this) {
            if (param == null || value == null || differ(param!!, newParam)) {
                param = newParam
                value = create(newParam)
            }
            return@synchronized value!!
        }

    fun invalidate() {
        param = null
    }
}

class CachedMap<P, K, V>(
    private var param: P? = null,
    private val create: (P, K) -> V,
    private var differ: Differ<P>
) {
    private val map: MutableMap<K, V> = mutableMapOf()

    operator fun get(newParam: P, key: K) =
        retrieve(newParam, key)

    fun retrieve(newParam: P, key: K): V =
        synchronized(this) {
            if (param == null || differ(param!!, newParam)) {
                map.clear()
                param = newParam
            }
            return map.getOrPut(key) { create(newParam, key) }
        }

    fun invalidate() {
        param = null
    }
}