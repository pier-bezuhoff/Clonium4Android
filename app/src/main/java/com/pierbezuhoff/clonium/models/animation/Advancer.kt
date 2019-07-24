package com.pierbezuhoff.clonium.models.animation

import kotlin.math.max

typealias Milliseconds = Long
typealias Progress = Double

data class WithProgress<out A>(val value: A, val progress: Progress)

interface Advanceable<out T> {
    val duration: Milliseconds
    val blocking: Boolean
    val progress: Progress
    val ended: Boolean

    fun advance(timeDelta: Milliseconds): T
}

/** Stackable proto-animation */
abstract class Advancer<out A>(
    final override val duration: Milliseconds
) : Advanceable<A> {
    /** Should be non-increasing (once it's `false` it will not become `true`) */
    abstract override val blocking: Boolean
    private var elapsed: Milliseconds = 0L
    override val progress: Progress
        get() = elapsed.toDouble() / duration
    override val ended: Boolean
        get() = elapsed >= duration

    /** Should be called in overridden [advance] */
    protected fun elapse(timeDelta: Milliseconds) {
        elapsed += timeDelta
    }

    override fun toString(): String =
        "Advancer(${elapsed}ms of ${duration}ms: progress = $progress, blocking = $blocking, ended = $ended)"
}

object EmptyAdvancer : Advancer<Nothing>(0L) {
    override val blocking: Boolean = true
    override fun advance(timeDelta: Milliseconds): Nothing =
        throw IllegalStateException("EmptyAdvancer: You Can (Not) Advance")
}

class AdvancerPack<A>(
    advancers: List<Advancer<A>>
) : Advancer<Set<A>>(
    advancers.fold(0L) { d, p -> max(d, p.duration) }
) {
    private val advancers: MutableList<Advancer<A>> = advancers.toMutableList()
    override val blocking: Boolean
        get() = advancers.any { it.blocking }

    constructor(advancer: Advancer<A>) : this(listOf(advancer))

    override fun advance(timeDelta: Milliseconds): Set<A> {
        elapse(timeDelta)
        val result = advancers.map { it.advance(timeDelta) }
        advancers.removeAll { it.ended }
        return result.toSet()
    }

    infix fun pAnd(advancer: Advancer<A>): AdvancerPack<A> =
        AdvancerPack(advancers + advancer)

    infix fun pAndP(pack: AdvancerPack<A>): AdvancerPack<A> =
        AdvancerPack(advancers + pack.advancers)

}

class AdvancerSequence<A>(
    private val packs: List<AdvancerPack<A>>
) : Advancer<List<A>>(
    packs.fold(0L) { d, p -> d + p.duration }
) {
    private var ix = 0
    // playing part of [packs]; last one is [blocking], the rest is not [blocking]
    private var pack: AdvancerPack<A>? = packs.first()
    private val nonBlockingPacks: MutableList<AdvancerPack<A>> = mutableListOf()
    override val blocking: Boolean
        get() = pack?.blocking ?: false

    constructor(pack: AdvancerPack<A>) : this(listOf(pack))

    constructor(advancer: Advancer<A>) : this(
        AdvancerPack(
            advancer
        )
    )

    override fun advance(timeDelta: Milliseconds): List<A> {
        elapse(timeDelta)
        val lastResult = pack?.advance(timeDelta)
        val results = nonBlockingPacks.flatMap { it.advance(timeDelta) }
        nonBlockingPacks.removeAll { it.ended }
        pack?.let { pack ->
            if (!pack.blocking)
                nonBlockingPacks.add(pack)
            if (pack.ended || !pack.blocking) {
                if (ix < packs.lastIndex)
                    this.pack = packs[++ix]
                else
                    this.pack = null
            }
        }
        return results + (lastResult ?: emptyList())
    }

    infix fun sThenP(pack: AdvancerPack<A>): AdvancerSequence<A> =
        AdvancerSequence(packs + pack)

    infix fun sThen(advancer: Advancer<A>): AdvancerSequence<A> =
        sThenP(AdvancerPack(listOf(advancer)))

    infix fun sThenS(sequence: AdvancerSequence<A>): AdvancerSequence<A> =
        AdvancerSequence(packs + sequence.packs)
}

object Advancers {
    // and:
    infix fun <A> Advancer<A>.and(advancer: Advancer<A>): AdvancerPack<A> =
        AdvancerPack(this) pAnd advancer
    infix fun <A> Advancer<A>.and(pack: AdvancerPack<A>): AdvancerPack<A> =
        AdvancerPack(this) pAndP pack
    infix fun <A> AdvancerPack<A>.and(advancer: Advancer<A>): AdvancerPack<A> =
        pAnd(advancer)
    infix fun <A> AdvancerPack<A>.and(pack: AdvancerPack<A>): AdvancerPack<A> =
        this pAndP pack
    // then:
    infix fun <A> Advancer<A>.then(advancer: Advancer<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThen advancer
    infix fun <A> Advancer<A>.then(pack: AdvancerPack<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThenP pack
    infix fun <A> Advancer<A>.then(sequence: AdvancerSequence<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThenS sequence
    infix fun <A> AdvancerPack<A>.then(advancer: Advancer<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThen advancer
    infix fun <A> AdvancerPack<A>.then(pack: AdvancerPack<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThenP pack
    infix fun <A> AdvancerPack<A>.then(sequence: AdvancerSequence<A>): AdvancerSequence<A> =
        AdvancerSequence(this) sThenS sequence
    infix fun <A> AdvancerSequence<A>.then(advancer: Advancer<A>): AdvancerSequence<A> =
        this sThen advancer
    infix fun <A> AdvancerSequence<A>.then(pack: AdvancerPack<A>): AdvancerSequence<A> =
        this sThenP pack
    infix fun <A> AdvancerSequence<A>.then(sequence: AdvancerSequence<A>): AdvancerSequence<A> =
        this sThenS sequence
}
