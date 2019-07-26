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
        val (blocking, nonBlocking) =
            pool.flatMap { advancer -> advancer.lastOutput
                .map { step -> advancer to step }
            }.partition { (_, step) -> step.blocking }
        // heterogeneous list => type of [step] is lost
        for ((advancer, step) in (blocking + nonBlocking)) {
            unsafeDrawOne(advancer, step)
        }
    }

    private fun <S : AnimatedStep> Canvas.unsafeDrawOne(advancer: AnimatiedAdvancer<*>, step: S) {
        with(advancer as AnimatiedAdvancer<S>) { drawOne(step) }
    }

    override fun startAdvancer(animatedAdvancer: AnimatiedAdvancer<*>) {
        require(!blocking) { "Should not have 2 blocking [AnimatedAdvancer]s: pool = ${pool.joinToString()}, trying to add $animatedAdvancer" }
        if (!animatedAdvancer.ended) {
            pool.add(animatedAdvancer)
            animatedAdvancer.advance(0L) // emit initial advance result
        }
    }

}