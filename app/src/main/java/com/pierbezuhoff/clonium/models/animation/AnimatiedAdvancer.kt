package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas

abstract class AnimatiedAdvancer<A>(
    private val advancer: Advancer<List<A>>
) : Any()
    , Advanceable<List<A>> by advancer
{
    private lateinit var lastOutput: List<A>

    final override fun advance(timeDelta: Milliseconds): List<A> {
        lastOutput = advancer.advance(timeDelta)
        return lastOutput
    }

    fun Canvas.draw() {
        for (a in lastOutput)
            drawOne(a)
    }

    abstract fun Canvas.drawOne(output: A)
}
