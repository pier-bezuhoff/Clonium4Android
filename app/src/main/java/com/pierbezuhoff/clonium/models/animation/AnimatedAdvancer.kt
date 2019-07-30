package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas

interface AnimatedStep {
    val blocking: Boolean
}

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
