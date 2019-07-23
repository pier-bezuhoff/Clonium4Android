package com.pierbezuhoff.clonium.models

import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.domain.PlayerId
import com.pierbezuhoff.clonium.domain.Pos
import com.pierbezuhoff.clonium.domain.Transition

class TransitionsAdvancer(
    private val transitions: Sequence<Transition>
) {
    // i & explosions; i' & swiftRotation || fallouts; e & idle

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
                TODO(),
                TODO()
            )
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
