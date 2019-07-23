package com.pierbezuhoff.clonium.models

import com.pierbezuhoff.clonium.domain.*

object TransitionsAdvancer {

    fun make(transitions: Sequence<Transition>): Advancer<List<WithProgress<TransitionStep>>> {
        val list = transitions.toList()
        return when {
            list.isEmpty() -> EmptyAdvancer
            else -> list.drop(1).fold(transitionAdvancer(list.first())) { sequence, transition ->
                val idle = stepAdvancer(TransitionStep.Stateful.Idle(transition))
                with(Advancers) {
                    sequence then idle then transitionAdvancer(transition)
                }
            }
        }
    }

    private fun transitionAdvancer(transition: Transition): AdvancerSequence<WithProgress<TransitionStep>> {
        val explosions = stepAdvancer(TransitionStep.Stateful.Explosions(transition))
        val swiftRotations = stepAdvancer(TransitionStep.Stateful.SwiftRotations(transition))
        val idle = stepAdvancer(TransitionStep.Stateful.Idle(transition))
        val fallouts = stepAdvancer(TransitionStep.Stateless.Fallouts(transition))
        return with(Advancers) {
            explosions then (swiftRotations and fallouts)
        }
    }

    private fun <S : TransitionStep> stepAdvancer(step: S): Advancer<WithProgress<TransitionStep>> =
        when (step) {
            is TransitionStep.Stateful.Explosions -> _stepAdvancer(step, Duration.EXPLOSIONS)
            is TransitionStep.Stateful.SwiftRotations -> _stepAdvancer(step, Duration.SWIFT_ROTATION)
            is TransitionStep.Stateful.Idle -> _stepAdvancer(step, Duration.IDLE)
            is TransitionStep.Stateless.Fallouts -> _stepAdvancer(step, Duration.FALLOUTS)
            else -> throw IllegalArgumentException("impossible case")
        }

    private fun <S : TransitionStep> _stepAdvancer(step: S, duration: Long): Advancer<WithProgress<S>> =
        object : Advancer<WithProgress<S>>(duration) {
            override val blocking: Boolean = step is TransitionStep.Stateful
            override fun advance(timeDelta: Long): WithProgress<S> {
                elapse(timeDelta)
                return WithProgress(step, progress)
            }
        }

    /** [TransitionStep]s durations in ms */
    object Duration {
        const val EXPLOSIONS: Long = 1_000L
        const val SWIFT_ROTATION: Long = 300L
        const val IDLE: Long = 500L
        const val FALLOUTS: Long = 4_000L
    }
}

sealed class TransitionStep {

    sealed class Stateful(val boardState: Board) : TransitionStep() {

        class Explosions(
            boardState: Board,
            val places: Map<Pos, PlayerId>
        ) : Stateful(boardState) {
            constructor(transition: Transition) : this(
                transition.interimBoard,
                transition.explosions.associate { it.center to it.playerId }
            )
        }

        class SwiftRotations(
            boardState: Board,
            val places: Map<Pos, PlayerId>
        ) : Stateful(boardState) {
            constructor(transition: Transition) : this(
                SimpleBoard(transition.endBoard).apply {
                    transition.explosions
                        .flatMap { neighbors(it.center) }
                        .filter { levelAt(it)?.ordinal == 1 }
                        .forEach {
                            posMap[it] = null
                        }
                },
                with(transition.endBoard) {
                    transition.explosions
                        .flatMap { neighbors(it.center)
                            .filter { pos -> levelAt(pos)?.ordinal == 1 }
                            .map { pos -> pos to it.playerId }
                        }.toMap()
                }
            )
        }

        class Idle(boardState: Board) : Stateful(boardState) {
            constructor(transition: Transition) : this(transition.endBoard)
        }
    }

    sealed class Stateless : TransitionStep() {
        class Fallouts(
            val places: Map<Pos, PlayerId>
        ) : Stateless() {
            constructor(transition: Transition) : this(
                transition.explosions
                    .flatMap { it.center.neighbors
                        .filterNot { pos -> transition.interimBoard.hasCell(pos) }
                        .map { pos -> pos to it.playerId }
                    }.toMap()
            )
        }
    }
}
