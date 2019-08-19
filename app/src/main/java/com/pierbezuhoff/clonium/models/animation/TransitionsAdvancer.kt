package com.pierbezuhoff.clonium.models.animation

import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.ChipSymmetry
import com.pierbezuhoff.clonium.utils.Milliseconds

typealias ExplosionsStep = TransitionStep.Stateful.Explosions
typealias SwiftRotationsStep = TransitionStep.Stateful.SwiftRotations
typealias IdleStep = TransitionStep.Stateful.Idle
typealias FalloutsStep = TransitionStep.Stateless.Fallouts
private typealias ProgressingStep = WithProgress<TransitionStep>
private typealias StepAdvancer = Advancer<ProgressingStep>

object TransitionsAdvancer {

    fun make(transitions: Sequence<Transition>, symmetry: ChipSymmetry): Advancer<List<ProgressingStep>> {
        val list = transitions.toList()
        val useSwiftRotations = symmetry !is ChipSymmetry.Four
        return when {
            list.isEmpty() -> EmptyAdvancer
            else -> with(Advancers) {
                list.dropLast(1)
                    .foldRight(transitionAdvancer(list.last(), useSwiftRotations)) { t, sequence ->
                        transitionAdvancer(t, useSwiftRotations) then idle(t) then sequence
                    }
            }
        }
    }

    private fun transitionAdvancer(transition: Transition, useSwiftRotations: Boolean): AdvancerSequence<ProgressingStep> {
        val explosions = explosions(transition)
        val swiftRotations = swiftRotations(transition)
        val fallouts = fallouts(transition)
        return with(Advancers) {
            if (useSwiftRotations)
                explosions then (swiftRotations and fallouts)
            else
                explosions then fallouts
        }
    }

    private fun explosions(transition: Transition): StepAdvancer =
        StepAdvancer(ExplosionsStep(transition), Duration.EXPLOSIONS)

    private fun swiftRotations(transition: Transition): StepAdvancer =
        StepAdvancer(SwiftRotationsStep(transition), Duration.SWIFT_ROTATION)

    private fun idle(transition: Transition): StepAdvancer =
        StepAdvancer(IdleStep(transition), Duration.IDLE)

    private fun fallouts(transition: Transition): StepAdvancer =
        StepAdvancer(FalloutsStep(transition), Duration.FALLOUTS)

    @Suppress("FunctionName")
    private fun <S : TransitionStep> StepAdvancer(step: S, duration: Long): StepAdvancer =
        object : Advancer<WithProgress<S>>(
            duration = duration,
            blockingDuration = if (step is TransitionStep.Stateful) duration else 0L
        ) {
            override val blocking: Boolean = step is TransitionStep.Stateful
            override fun advance(timeDelta: Milliseconds): WithProgress<S> {
                elapse(timeDelta)
                return WithProgress(step, progress)
            }
        }

    /** [TransitionStep]s durations */
    object Duration {
        const val EXPLOSIONS: Milliseconds = 1_000L
        const val SWIFT_ROTATION: Milliseconds = 300L
        const val IDLE: Milliseconds = 500L
        const val FALLOUTS: Milliseconds = 7_000L
    }
}

// it's a lie, BTW, secondary constructors ARE used in TransitionsAdvancer(.explosions, ...) (by aliases)
@Suppress("unused")
sealed class TransitionStep : AnimatedStep {

    sealed class Stateful(val boardState: Board) : TransitionStep() {
        override val blocking: Boolean = true

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
            val places: Map<Pos, Pair<PlayerId, Direction>>
        ) : Stateful(boardState) {
            constructor(transition: Transition) : this(
                SimpleBoard(transition.endBoard).apply {
                    transition.explosions
                        .flatMap { neighbors(it.center) }
                        .filter { levelAt(it) == Level1 }
                        .forEach {
                            posMap[it] = null
                        }
                },
                with(transition.endBoard) {
                    transition.explosions
                        .flatMap { directedNeighbors(it.center)
                            .filterValues { pos -> levelAt(pos) == Level1 }
                            .map { (direction, pos) -> pos to Pair(it.playerId, direction) }
                        }.toMap()
                }
            )
        }

        class Idle(boardState: Board) : Stateful(boardState) {
            constructor(transition: Transition) : this(transition.endBoard)
        }
    }

    sealed class Stateless : TransitionStep() {
        override val blocking: Boolean = false

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

data class WithProgress<out A : AnimatedStep>(
    val value: A,
    val progress: Progress
) : AnimatedStep by value

