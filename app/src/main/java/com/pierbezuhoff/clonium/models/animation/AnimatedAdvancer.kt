package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas
import com.pierbezuhoff.clonium.utils.Milliseconds

abstract class AnimatedAdvancer<A : AnimatedStep>(
    private val advancer: Advancer<List<A>>
) : Any()
    , Advanceable<List<A>> by advancer
{
    lateinit var lastOutput: List<A>
        private set

    final override fun advance(timeDelta: Milliseconds): List<A> {
        lastOutput = advancer.advance(timeDelta)
        return lastOutput
    }

    abstract fun Canvas.drawOne(output: A)
}

interface AnimatedStep {
    val blocking: Boolean
}

class StepAdvancer<S : AnimatedStep>(
    private val step: S,
    duration: Milliseconds
) : Advancer<WithProgress<S>>(
        duration = duration,
        blockingDuration = if (step is TransitionStep.Stateful) duration else 0L
    ) {
        override val blocking: Boolean = step is TransitionStep.Stateful
        override fun advance(timeDelta: Milliseconds): WithProgress<S> {
            elapse(timeDelta)
            return WithProgress(step, progress)
        }
    }
