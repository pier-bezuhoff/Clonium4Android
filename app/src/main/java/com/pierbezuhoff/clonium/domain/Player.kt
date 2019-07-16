package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

interface Player {
    val playerId: PlayerId
}

class HumanPlayer(override val playerId: PlayerId) : Player

interface Bot : Player {
    val difficultyName: String

    fun CoroutineScope.makeTurnAsync(board: Board): Deferred<Pos>
}

