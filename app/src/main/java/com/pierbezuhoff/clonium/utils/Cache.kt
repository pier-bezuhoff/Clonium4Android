package com.pierbezuhoff.clonium.utils

// should be reflexive, symmetric, transitive
typealias Differ<P> = (p0: P, p: P) -> Boolean

class Cached<P : Any, V : Any>(
    private var param: P? = null,
    private val create: (p: P, previous: Pair<P, V>?) -> V,
    private val differ: Differ<P> = { p0, p -> p0 != p }
) {
    private var value: V? = null

    operator fun get(newParam: P): V =
        retrieve(newParam)

    fun retrieve(newParam: P): V =
        synchronized(this) {
            if (param == null || value == null || differ(param!!, newParam)) {
                val previous = value?.let { param?.to(it) }
                value = create(newParam, previous)
                param = newParam
            }
            return@synchronized value!!
        }

    fun invalidate() {
        param = null
    }
}

class CachedBy<P, PK : Any, V : Any>(
    private var paramKey: PK? = null,
    private val create: (p: P) -> V,
    private val keyOf: (P) -> PK,
    private val differ: Differ<PK> = { pk0, pk -> pk0 != pk }
) {
    private var value: V? = null

    operator fun get(newParam: P): V =
        retrieve(newParam)

    fun retrieve(newParam: P): V =
        synchronized(this) {
            val newParamKey = keyOf(newParam)
            if (paramKey == null || value == null || differ(paramKey!!, newParamKey)) {
                paramKey = newParamKey
                value = create(newParam)
            }
            return@synchronized value!!
        }

    fun invalidate() {
        paramKey = null
    }
}

class CachedMap<P : Any, K, V : Any>(
    private var param: P? = null,
    private val create: (p: P, k: K, p0: P?) -> V,
    private var differ: Differ<P> = { p0, p -> p0 != p }
) {
    private val map: MutableMap<K, V> = mutableMapOf()

    operator fun get(newParam: P, key: K) =
        retrieve(newParam, key)

    fun retrieve(newParam: P, key: K): V =
        synchronized(this) {
            val oldParam = param
            if (param == null || differ(param!!, newParam)) {
                map.clear()
                param = newParam
            }
            return map.getOrPut(key) { create(newParam, key, oldParam) }
        }

    fun invalidate() {
        param = null
    }
}