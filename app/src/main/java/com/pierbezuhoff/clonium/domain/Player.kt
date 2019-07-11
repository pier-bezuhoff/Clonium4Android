package com.pierbezuhoff.clonium.domain

interface Player {
    val playerId: PlayerId
}

class HumanPlayer(override val playerId: PlayerId) : Player

interface Bot : Player {
    val difficultyName: String

    suspend fun makeTurn(board: Board): Pos
}

class RandomPickerBot(override val playerId: PlayerId) : Bot {
    override val difficultyName = "Random picker"

    override suspend fun makeTurn(board: Board): Pos {
        val possibleTurns = board.asPosMap()
            .filterValues { chip -> chip?.playerId == playerId }
            .keys
        return possibleTurns.random()
    }
}