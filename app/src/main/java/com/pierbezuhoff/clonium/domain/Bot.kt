package com.pierbezuhoff.clonium.domain

interface Bot {
    val player: Player
    val difficultyName: String

    fun makeTurn(board: Board): Pos
}

class RandomPickerBot(override val player: Player) : Bot {
    override val difficultyName = "Random picker"

    override fun makeTurn(board: Board): Pos {
        val possibleTurns = board.asPosMap()
            .filterValues { chip -> chip?.player == player }
            .keys
        return possibleTurns.random()
    }
}