package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class Game(
    val board: EvolvingBoard,
    bots: Set<Bot>,
    initialOrder: List<PlayerId>? = null
) {
    val players: Map<PlayerId, Player>
    val order: List<Player>
    val lives: Map<Player, Boolean>
    var currentPlayer: Player

    init {
        val playerIds = initialOrder ?: board.players().shuffled().toList()
        val botIds = bots.map { it.playerId }
        val botMap = bots.associateBy { it.playerId }
        val humanMap = (playerIds - botIds).associateWith { HumanPlayer(it) }
        players = botMap + humanMap
        order = playerIds.map { players.getValue(it) }
        require(order.isNotEmpty()) { "Game should have players" }
        lives = order.associateWith { board.possOf(it.playerId).isNotEmpty() }
        require(lives.values.any()) { "Someone should be alive" }
        currentPlayer = order.first { isAlive(it) }
    }

    private fun isAlive(player: Player): Boolean =
        lives.getValue(player)

    private fun nextPlayer(): Player {
        val ix = order.indexOf(currentPlayer)
        return (order.drop(ix) + order).first { isAlive(it) }
    }

    fun possibleTurns(): Set<Pos> =
        board.possOf(currentPlayer.playerId)

    /** (# of [Chip]s, sum of [Chip] [Level]s) or `null` if dead */
    private fun statOf(player: Player): Pair<Int, Int>? {
        return if (!isAlive(player))
            null
        else {
            val ownedChips = board.asPosMap().values
                .filterNotNull()
                .filter { it.playerId == player.playerId }
            Pair(ownedChips.size, ownedChips.sumBy { it.level.ordinal })
        }
    }

    /** [Player] to (# of [Chip]s, sum of [Chip] [Level]s) or `null` if dead */
    fun stat(): Map<Player, Pair<Int, Int>?> =
        order.associateWith { statOf(it) }

    fun isEnd(): Boolean =
        lives.values.filter { it }.size > 1

    private fun makeTurn(pos: Pos): Sequence<Transition> {
        require(pos in possibleTurns())
        val transitions = board.inc(pos)
        currentPlayer = nextPlayer()
        return transitions
    }

    @Throws(EvolvingBoard.InvalidTurn::class)
    fun humanTurn(pos: Pos): Sequence<Transition> {
        require(currentPlayer is HumanPlayer)
        return makeTurn(pos)
    }

    fun CoroutineScope.botTurnAsync(): Deferred<Sequence<Transition>> {
        require(currentPlayer is Bot)
        return async {
            val turn =
                with(currentPlayer as Bot) { makeTurnAsync(board) }.await()
            return@async makeTurn(turn)
        }
    }
}

