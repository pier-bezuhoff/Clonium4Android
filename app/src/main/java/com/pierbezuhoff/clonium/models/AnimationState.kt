package com.pierbezuhoff.clonium.models

import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.domain.Explosion
import com.pierbezuhoff.clonium.domain.Transition

// MAYBE: just add objects with animations
// TODO: fallout animation!
sealed class AnimationState {
    object Normal : AnimationState()
    data class Transient(private val transitions: Iterator<Transition>) : AnimationState() {
        /** in 0..1 */
        var progress: Double = 0.0
            private set
        var transition: Transition? = if (transitions.hasNext()) transitions.next() else null
            private set

        fun advance(percent: Double) {
            progress += percent
            if (progress >= 1.0) {
                progress = 0.0
                transition = if (transitions.hasNext()) transitions.next() else null
            }
        }
    }
}
sealed class TransientPhase {
    data class Transition(val interimBoard: Board, val explosions: Set<Explosion>) : TransientPhase()
    data class Ending(val endBoard: Board) : TransientPhase()
}

interface AnimationStateHolder {
    val state: AnimationState
    /** First part of [Transient].progress in ms (dynamic explosions and static [Transition].interimState) */
    val transitionTime: Long
    /** Second part of [Transient].progress in ms (static [Transition].endState) */
    val endingTime: Long

    fun normalizeState()
    fun startTransitions(transitions: Iterator<Transition>)
    fun advance(/** in ms */ timeDelta: Long)
    fun transientPhase(): TransientPhase
}

class SimpleAnimationStateHolder : AnimationStateHolder {
    override var state: AnimationState = AnimationState.Normal
        private set
    override val transitionTime: Long = 2_000
    override val endingTime: Long = 500
    private val transientTime: Long = transitionTime + endingTime

    override fun normalizeState() {
        val currentState = state
        if (currentState is AnimationState.Transient && currentState.transition == null)
            state = AnimationState.Normal
    }

    override fun startTransitions(transitions: Iterator<Transition>) {
        require(state is AnimationState.Normal)
        state = AnimationState.Transient(transitions)
        normalizeState()
    }

    override fun advance(timeDelta: Long) {
        val currentState = state
        if (currentState is AnimationState.Transient) {
            currentState.advance(timeDelta.toDouble() / transientTime)
            normalizeState()
        }
    }

    override fun transientPhase(): TransientPhase {
        require(state is AnimationState.Transient) // normalized!
        val currentState = state as AnimationState.Transient
        val (interimState, endState, explosions) = currentState.transition!!
        return if (currentState.progress < transientTime.toDouble() / transientTime)
            TransientPhase.Transition(interimState, explosions)
        else
            TransientPhase.Ending(endState)
    }
}