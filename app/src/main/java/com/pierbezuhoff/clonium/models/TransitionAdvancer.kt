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
        ) : Stateful(boardState)
        class SwiftRotations(
            boardState: Board,
            val places: Map<Pos, PlayerId>
        ) : Stateful(boardState)
    }
    sealed class Stateless : TransitionStep() {
        class Fallouts(
            val places: Map<Pos, PlayerId>
        ) : Stateless()
    }
}
