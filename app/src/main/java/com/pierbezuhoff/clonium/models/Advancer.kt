package com.pierbezuhoff.clonium.models

import kotlin.math.max

class WithProgress<out A>(val value: A, val progress: Double)

/** Stackable proto-animation */
abstract class Advancer<out A>(
    val duration: Long
) : Advanceable<A> {
    abstract val blocking: Boolean
    protected var elapsed: Long = 0L
        private set
    val progress: Double
        get() = elapsed.toDouble() / duration
    val ended: Boolean
        get() = elapsed >= duration

    /** Should be called in overridden [advance] */
    protected fun elapse(timeDelta: Long) {
        elapsed += timeDelta
    }
}

object EmptyAdvancer : Advancer<Nothing>(0L) {
    override val blocking: Boolean = true
    override fun advance(timeDelta: Long): Nothing =
        throw IllegalStateException("EmptyAdvancer can (not) advance")
}

class AdvancerPack<A>(
    advancers: List<Advancer<A>>
) : Advancer<Set<A>>(
    advancers.fold(0L) { d, p -> max(d, p.duration) }
) {
    private val advancers: MutableList<Advancer<A>> = advancers.toMutableList()
    // true => true or false, false => false
    override val blocking: Boolean
        get() = advancers.any { it.blocking }

    constructor(advancer: Advancer<A>) : this(listOf(advancer))

    override fun advance(timeDelta: Long): Set<A> {
        elapse(timeDelta)
        val result = advancers.map { it.advance(timeDelta) }
        advancers.removeAll { it.ended }
        return result.toSet()
    }

    infix fun sAnd(advancer: Advancer<A>): AdvancerPack<A> =
        AdvancerPack(advancers + advancer)

    infix fun and(progressionPack: AdvancerPack<A>): AdvancerPack<A> =
        AdvancerPack(advancers + progressionPack.advancers)

}

class AdvancerSequence<A>(
    private val packs: List<AdvancerPack<A>>
) : Advancer<List<A>>(packs.fold(0L, Long::plus)) {
    private var ix = 0
    // playing part of [packs]; last one is [blocking], the rest is not [blocking]
    private var pack = packs.first()
    private val nonBlockingPacks: MutableList<AdvancerPack<A>> = mutableListOf()
    override val blocking: Boolean
        get() = pack.blocking

    constructor(pack: AdvancerPack<A>) : this(listOf(pack))

    constructor(advancer: Advancer<A>) : this(AdvancerPack(advancer))

    override fun advance(timeDelta: Long): List<A> {
        elapse(timeDelta)
        val lastResult = pack.advance(timeDelta)
        val results = nonBlockingPacks.flatMap { it.advance(timeDelta) }
        nonBlockingPacks.removeAll { it.ended }
        if (pack.ended) {
            pack = packs[++ix]
        } else if (!pack.blocking) {
            nonBlockingPacks.add(pack)
            pack = packs[++ix]
        }
        return results + lastResult
    }

    infix fun sThen(pack: AdvancerPack<A>): AdvancerSequence<A> =
        AdvancerSequence(packs + pack)

    infix fun sThen(advancer: Advancer<A>): AdvancerSequence<A> =
        sThen(AdvancerPack(listOf(advancer)))

    infix fun then(sequence: AdvancerSequence<A>): AdvancerSequence<A> =
        AdvancerSequence(packs + sequence.packs)
}

object Advancers {
    infix fun <A> Advancer<A>.and(advancer: Advancer<A>): AdvancerPack<A> =
        AdvancerPack(this) sAnd advancer
    infix fun <A> AdvancerPack<A>.and(advancer: Advancer<A>): AdvancerPack<A> =
        sAnd(advancer)
    infix fun <A> Advancer<A>.then(advancer: Advancer<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThen advancer
    infix fun <A> Advancer<A>.then(pack: AdvancerPack<A>): AdvancerSequence<A> =
        AdvancerSequence(this).sThen(pack)
    infix fun <A> AdvancerSequence<A>.then(pack: AdvancerPack<A>): AdvancerSequence<A> =
        sThen(pack)
    infix fun <A> AdvancerSequence<A>.then(advancer: Advancer<A>): AdvancerSequence<A> =
        sThen(advancer)
}
