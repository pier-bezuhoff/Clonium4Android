package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.utils.Milliseconds

interface AnimationsHost {
    /** Whether animations are blocking user input (i.e. [Game.humanTurn]) */
    val blocking: Boolean
    /** Advance animation time */
    fun advanceAnimations(timeDelta: Milliseconds)
    /** Draw all animations */
    fun drawAnimations(canvas: Canvas)
}

interface TransitionAnimationsHost : AnimationsHost {
    fun startAdvancer(animatedAdvancer: AnimatedAdvancer<*>)
}

class TransitionAnimationsPool : Any()
    , TransitionAnimationsHost
{
    private val pool: MutableList<AnimatedAdvancer<*>> = mutableListOf()
    override val blocking: Boolean
        get() = pool.any { it.blocking }

    object AdvanceAnimations
    override fun advanceAnimations(timeDelta: Milliseconds) {
        synchronized(AdvanceAnimations) {
            for (advancer in pool) {
                advancer.advance(timeDelta)
            }
            pool.removeAll { it.ended }
        }
    }

    override fun drawAnimations(canvas: Canvas) {
        val (blocking, nonBlocking) =
            pool.flatMap { advancer -> advancer.lastOutput // BUG: [rare] lastOutput has not been initialized
                .map { step -> advancer to step }
            }.partition { (_, step) -> step.blocking }
        // heterogeneous list => type of [step] is lost
        for ((advancer, step) in (blocking + nonBlocking)) {
            canvas.unsafeDrawOne(advancer, step)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : AnimatedStep> Canvas.unsafeDrawOne(advancer: AnimatedAdvancer<*>, step: S) {
        with(advancer as AnimatedAdvancer<S>) { drawOne(step) }
    }

    override fun startAdvancer(animatedAdvancer: AnimatedAdvancer<*>) {
        require(!blocking) { "Should not have 2 blocking [AnimatedAdvancer]s: pool = ${pool.joinToString()}, trying to add $animatedAdvancer" }
        if (!animatedAdvancer.ended) {
            pool.add(animatedAdvancer)
            animatedAdvancer.advance(0L) // emit initial advance result
        }
    }

}