package com.pierbezuhoff.clonium.models.animation

import kotlin.math.*

typealias Milliseconds = Long
typealias Progress = Double

interface Advanceable<out T> {
    val duration: Milliseconds
    val blockingDuration: Milliseconds
    val blocking: Boolean
    val progress: Progress
    val ended: Boolean

    fun advance(timeDelta: Milliseconds): T
}

/** Stackable proto-animation */
abstract class Advancer<out A>(
    final override val duration: Milliseconds,
    final override val blockingDuration: Milliseconds
) : Advanceable<A> {
    /** Should be non-increasing (once it's `false` it will not become `true`) */
    abstract override val blocking: Boolean
    private var elapsed: Milliseconds = 0L
    override var progress: Progress = 0.0
    override val ended: Boolean
        get() = elapsed >= duration

    /** Should be called in overridden [advance] */
    protected fun elapse(timeDelta: Milliseconds) {
        elapsed += timeDelta
        progress = min(1.0, elapsed.toDouble() / duration)
    }

    override fun toString(): String =
        "Advancer($elapsed ms of $duration ms: progress = $progress, blocking = $blocking, ended = $ended)"
}

object EmptyAdvancer : Advancer<Nothing>(0L, 0L) {
    override val blocking: Boolean = false
    override fun advance(timeDelta: Milliseconds): Nothing =
        throw IllegalStateException("EmptyAdvancer: You Can (Not) Advance")
    override fun toString(): String =
        "EmptyAdvancer"
}

class AdvancerPack<A>(
    advancers: List<Advancer<A>>
) : Advancer<List<A>>(
    duration = advancers.fold(0L) { d, p -> max(d, p.duration) },
    blockingDuration = advancers.fold(0L) { d, p -> max(d, p.blockingDuration) }
) {
    private val advancers: MutableList<Advancer<A>> = advancers.toMutableList()
    override val blocking: Boolean
        get() = !ended && advancers.any { it.blocking }

    constructor(advancer: Advancer<A>) : this(listOf(advancer))

    override fun advance(timeDelta: Milliseconds): List<A> {
        elapse(timeDelta)
        val result = advancers.map { it.advance(timeDelta) }
        advancers.removeAll { it.ended }
        return result
    }

    override fun toString(): String =
        "Pack [${super.toString()}], playing:\n\t${advancers.joinToString(separator = "\n\tand ")}"

    infix fun pAnd(advancer: Advancer<A>): AdvancerPack<A> =
        AdvancerPack(advancers + advancer)

    infix fun pAndP(pack: AdvancerPack<A>): AdvancerPack<A> =
        AdvancerPack(advancers + pack.advancers)

}

class AdvancerSequence<A>(
    private val packs: List<AdvancerPack<A>>
) : Advancer<List<A>>(
    duration = packs.fold(0L to 0L) { (duration, nonBlockingDuration), pack ->
        (duration + pack.blockingDuration) to (max(nonBlockingDuration, pack.duration) - pack.blockingDuration)
    }.let { (blockingDuration, lasting) -> blockingDuration + lasting },
    blockingDuration = packs.map { it.blockingDuration }.fold(0L, Long::plus)
) {
    private var ix = 0
    /** Currently playing blocking pack */
    private var pack: AdvancerPack<A>? = null
    private val nonBlockingPacks: MutableList<AdvancerPack<A>> = mutableListOf()
    override val blocking: Boolean
        get() = !ended && (pack?.blocking ?: false)

    init {
        pack = packs.firstOrNull { it.blocking }
        nonBlockingPacks.addAll(packs.takeWhile { !it.blocking })
    }

    constructor(pack: AdvancerPack<A>) : this(listOf(pack))

    constructor(advancer: Advancer<A>) : this(AdvancerPack(advancer))

    override fun advance(timeDelta: Milliseconds): List<A> {
        elapse(timeDelta)
        val lastResult = pack?.advance(timeDelta)
        val results = nonBlockingPacks.flatMap { it.advance(timeDelta) }
        nonBlockingPacks.removeAll { it.ended }
        pack?.let {
            while (pack?.blocking == false || pack?.ended == true) {
                if (pack!!.ended) {
                    pack = nextPack()
                } else if (!pack!!.blocking) {
                    nonBlockingPacks.add(pack!!)
                    pack = nextPack()
                }
            }
        }
        return results + (lastResult ?: emptyList())
    }

    private fun nextPack(): AdvancerPack<A>? =
        if (ix < packs.lastIndex)
            packs[++ix]
        else
            null

    override fun toString(): String =
        "Sequence [${super.toString()}]: \n\t${packs.joinToString(separator = "\n\tthen ")}"

    infix fun sThenP(pack: AdvancerPack<A>): AdvancerSequence<A> =
        AdvancerSequence(packs + pack)

    infix fun sThen(advancer: Advancer<A>): AdvancerSequence<A> =
        sThenP(AdvancerPack(listOf(advancer)))

    infix fun sThenS(sequence: AdvancerSequence<A>): AdvancerSequence<A> =
        AdvancerSequence(packs + sequence.packs)
}

/** Contains fully overloaded (Advancer, AdvancerPack, AdvancerSequence) extension methods `and` and `then` */
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
