package com.pierbezuhoff.clonium.utils

/** Delegates [Map] operation to submaps, for use in 'by' notation */
class MultiMapDelegate<K, V: Any>(vararg val maps: Map<K, V>) : Map<K, List<V>> {
    private data class Entry<K, V>(
        override val key: K,
        override val value: V
    ) : Map.Entry<K, V>

    override val entries: Set<Map.Entry<K, List<V>>>
        get() = keys.mapTo(mutableSetOf()) { Entry(it, get(it)!!) }
    override val keys: Set<K>
        get() = maps.flatMapTo(mutableSetOf()) { it.keys }
    override val size: Int
        get() = keys.size
    override val values: Collection<List<V>>
        get() = keys.map { get(it)!! }

    override fun containsKey(key: K): Boolean =
        maps.any { it.containsKey(key) }

    override fun containsValue(value: List<V>): Boolean =
        keys.any { get(it) == value }

    override fun get(key: K): List<V>? =
        maps.mapNotNull { it[key] }.ifEmpty { null }

    override fun isEmpty(): Boolean =
        maps.all { it.isEmpty() }
}
