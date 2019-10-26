package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.utils.AndroidLogOf
import com.pierbezuhoff.clonium.utils.Milliseconds
import com.pierbezuhoff.clonium.utils.WithLog

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
    , WithLog by AndroidLogOf<TransitionAnimationsPool>()
{
    private val pool: MutableList<AnimatedAdvancer<*>> = mutableListOf()
    override val blocking: Boolean
        get() = synchronized(PoolLock) {
            pool.any { it.blocking }
        }

    override fun advanceAnimations(timeDelta: Milliseconds) {
        synchronized(PoolLock) {
            for (advancer in pool) {
                advancer.advance(timeDelta)
            }
            pool.removeAll { it.ended }
        }
    }

    override fun drawAnimations(canvas: Canvas) {
        synchronized(PoolLock) {
            val (blocking, nonBlocking) =
                pool.flatMap { advancer ->
                    advancer.lastOutput // BUG: [rare] lastOutput has not been initialized
                        .map { step -> advancer to step }
                }.partition { (_, step) -> step.blocking }
            // heterogeneous list => type of [step] is lost
            for ((advancer, step) in (blocking + nonBlocking)) {
                canvas.unsafeDrawOne(advancer, step)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : AnimatedStep> Canvas.unsafeDrawOne(advancer: AnimatedAdvancer<*>, step: S) {
        with(advancer as AnimatedAdvancer<S>) { drawOne(step) }
    }

    override fun startAdvancer(animatedAdvancer: AnimatedAdvancer<*>) {
        synchronized(PoolLock) {
            require(!animatedAdvancer.blocking || !blocking) { "Should not have 2 blocking [AnimatedAdvancer]s: pool = ${pool.joinToString()}, trying to add $animatedAdvancer" }
            if (!animatedAdvancer.ended) {
                pool.add(animatedAdvancer)
                animatedAdvancer.advance(0L) // emit initial advance result
            }
        }
    }

    object PoolLock
}