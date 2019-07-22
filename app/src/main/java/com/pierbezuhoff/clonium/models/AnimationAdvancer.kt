package com.pierbezuhoff.clonium.models

import android.graphics.Canvas
import kotlin.math.max

interface Advanceable<T> {
    fun advance(timeDelta: Long): T
}

interface AnimationAdvancer : Advanceable<Unit> {
    fun hasBlockingAnimations(): Boolean
    fun Canvas.drawAnimations()
    fun startAnimation(animation: Animation)
    fun startAnimationEmitter(animationEmitter: AnimationEmitter)
}

class PoolingAnimationAdvancer : AnimationAdvancer {
    private var animationPool: List<Animation> = emptyList()
    private var emitterPool: List<AnimationEmitter> = emptyList()

    override fun advance(timeDelta: Long) {
        animationPool = animationPool.filter { it.advance(timeDelta) } // remove finished animations
        emitterPool = emitterPool.filter { it.advance(timeDelta).let { output ->
            when (output) {
                is AnimationEmitter.Output.End -> false
                is AnimationEmitter.Output.Continue -> true
                is AnimationEmitter.Output.Animations -> true.also {
                    for (animation in output.animations)
                        startAnimation(animation)
                }
            }
        } }
    }

    override fun hasBlockingAnimations(): Boolean =
        emitterPool.isNotEmpty() || animationPool.any { it.blocking }

    override fun Canvas.drawAnimations() {
        for (animation in animationPool)
            with(animation) { draw() }
    }

    override fun startAnimation(animation: Animation) {
        animationPool = animationPool + animation
    }

    override fun startAnimationEmitter(animationEmitter: AnimationEmitter) {
        emitterPool = emitterPool + animationEmitter
    }
}

interface AnimationEmitter : Advanceable<AnimationEmitter.Output> {
    sealed class Output {
        object End : Output()
        object Continue: Output()
        class Animations(val animations: List<Animation>) : Output()
    }
    override fun advance(timeDelta: Long): Output
}

// MAYBE: add z-ordering
class Animation(
    private val duration: Long,
    val blocking: Boolean = true,
    private val progressingDraw: Canvas.(progress: Double) -> Unit
) : Advanceable<Boolean> {
    /** in 1..0 */
    private var progress: Double = 0.0

    /** Return false if animation has finished */
    override fun advance(timeDelta: Long): Boolean {
        progress += timeDelta.toDouble() / duration
        return progress < 1.0
    }

    fun Canvas.draw() {
        progressingDraw(progress)
    }
}

class AnimatibleSequence<State, Animatible>

