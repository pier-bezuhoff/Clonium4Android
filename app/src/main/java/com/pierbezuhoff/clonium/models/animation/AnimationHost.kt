package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas
import com.pierbezuhoff.clonium.domain.Game

interface AnimationsHost {
    /** Whether animations are blocking user input (i.e. [Game.humanTurn]) */
    val blocking: Boolean
    /** Advance animation time */
    fun advance(timeDelta: Milliseconds)
    /** Draw all animations */
    fun Canvas.draw()
}

interface TransitionAnimationsHost : AnimationsHost {
    fun startAdvancer(animatedAdvancer: AnimatiedAdvancer<*>)
}

class TransitionAnimationsPool : Any()
    , TransitionAnimationsHost
{
    private val pool: MutableList<AnimatiedAdvancer<*>> = mutableListOf()
    override val blocking: Boolean
        get() = pool.any { it.blocking }

    override fun advance(timeDelta: Milliseconds) {
        for (advancer in pool) {
            advancer.advance(timeDelta)
        }
        pool.removeAll { it.ended }
    }

    override fun Canvas.draw() {
        val (blockingPart, nonBlockingPart) =
            pool.partition { it.blocking }
        for (advancer in (blockingPart + nonBlockingPart))
            with(advancer) { draw() }
    }

    override fun startAdvancer(animatedAdvancer: AnimatiedAdvancer<*>) {
        if (!animatedAdvancer.ended) {
            require(!blocking)
            pool.add(animatedAdvancer)
            animatedAdvancer.advance(0L)
        }
    }

}