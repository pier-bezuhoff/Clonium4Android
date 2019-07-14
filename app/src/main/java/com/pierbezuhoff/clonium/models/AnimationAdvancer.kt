package com.pierbezuhoff.clonium.models

import android.graphics.Canvas
import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.domain.Explosion
import com.pierbezuhoff.clonium.domain.Transition

interface AnimationAdvancer {
    fun advance(timeDelta: Long)
    fun hasBlockingAnimations(): Boolean
    fun Canvas.drawAnimations()
    fun startAnimation(animation: Animation)
}

class PoolingAnimationAdvancer : AnimationAdvancer {
    private var pool: List<Animation> = emptyList()

    override fun advance(timeDelta: Long) {
        pool = pool.filterNot { it.advance(timeDelta) } // remove finished animations
    }

    override fun hasBlockingAnimations(): Boolean =
        pool.any { it.blocking }

    override fun Canvas.drawAnimations() {
        for (animation in pool)
            with(animation) { draw() }
    }

    override fun startAnimation(animation: Animation) {
        pool = pool + animation
    }
}

class Animation(
    private val duration: Long,
    val blocking: Boolean = true,
    private val progressingDraw: Canvas.(progress: Double) -> Unit
) {
    /** in 1..0 */
    private var progress: Double = 0.0

    /** Return false if animation has finished */
    fun advance(deltaTime: Long): Boolean {
        progress += deltaTime.toDouble() / duration
        return progress < 1.0
    }

    fun Canvas.draw() {
        progressingDraw(progress)
    }
}