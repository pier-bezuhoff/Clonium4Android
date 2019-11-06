package com.pierbezuhoff.clonium.utils

inline fun <T, X, C : MutableCollection<in X>> Iterable<T>.filterMapTo(destination: C, predicate: (T) -> Boolean, transform: (T) -> X): C {
    for (element in this)
        if (predicate(element))
            destination.add(transform(element))
    return destination
}

inline fun <T, X> Iterable<T>.filterMap(predicate: (T) -> Boolean, transform: (T) -> X): List<X> {
    return filterMapTo(ArrayList<X>(), predicate, transform)
}

inline fun <T, X, C : MutableCollection<in X>> Iterable<T>.mapFilterTo(destination: C, transform: (T) -> X, predicate: (X) -> Boolean): C {
    for (element in this) {
        val newElement = transform(element)
        if (predicate(newElement))
            destination.add(newElement)
    }
    return destination
}

inline fun <T, X> Iterable<T>.mapFilter(transform: (T) -> X, predicate: (X) -> Boolean): List<X> {
    return mapFilterTo(ArrayList<X>(), transform, predicate)
}

