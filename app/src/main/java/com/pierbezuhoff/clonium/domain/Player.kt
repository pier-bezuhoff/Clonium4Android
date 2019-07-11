package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

interface Player {
    val playerId: PlayerId
}

class HumanPlayer(override val playerId: PlayerId) : Player

interface Bot : Player {
    val difficultyName: String

    fun CoroutineScope.makeTurnAsync(board: Board): Deferred<Pos>
}

class RandomPickerBot(override val playerId: PlayerId) : Bot {
    override val difficultyName = "Random picker"

    override fun CoroutineScope.makeTurnAsync(board: Board): Deferred<Pos> = async {
        val possibleTurns = board.possOf(playerId)
        return@async possibleTurns.random()
    }
}