package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class RandomPickerBot(override val playerId: PlayerId) : Bot {
    override val difficultyName = "Random picker"

    override fun CoroutineScope.makeTurnAsync(board: Board): Deferred<Pos> = async {
        val possibleTurns = board.possOf(playerId)
        return@async possibleTurns.random()
    }
}

class MaxHolesBot1(override val playerId: PlayerId): Bot {
    override val difficultyName: String = "Holes maximizer-1"

    override fun CoroutineScope.makeTurnAsync(board: Board): Deferred<Pos> {
        return async(Dispatchers.Main) {
            TODO("pos")
        }
    }
}