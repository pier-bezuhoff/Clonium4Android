package com.pierbezuhoff.clonium.domain

class Game(
    val board: EvolvingBoard,
    bots: Set<Bot>,
    initialOrder: List<PlayerId>? = null
) {
    val players: Map<PlayerId, Player>
    val order: List<Player>
    val playerLives: Map<Player, Boolean>
    var currentPlayer: Player

    init {
        val playerIds = initialOrder ?: board.players().shuffled().toList()
        val botIds = bots.map { it.playerId }
        val botMap = bots.map { it.playerId to it }.toMap()
        val humanMap = (playerIds - botIds).map { it to HumanPlayer(it) }.toMap()
        players = botMap + humanMap
        order = playerIds.map { players.getValue(it) }
        require(order.isNotEmpty())
        playerLives = order.map { it to true }.toMap()
        currentPlayer = order.first()
    }

    private fun isAlive(player: Player): Boolean =
        playerLives.getValue(player)

    private fun nextPlayer(): Player {
        val ix = order.indexOf(currentPlayer)
        return (order.drop(ix) + order).first { isAlive(it) }
    }

    fun possibleTurns(): Set<Pos> =
        board.possOf(currentPlayer.playerId)

    /** (# of [Chip]s, sum of [Chip] [Level]s) or `null` if dead */
    fun statOf(player: Player): Pair<Int, Int>? {
        if (!isAlive(player))
            return null
        else {
            val ownedChips = board.asPosMap().values
                .filterNotNull()
                .filter { it.playerId == player.playerId }
            return Pair(ownedChips.size, ownedChips.sumBy { it.level.ordinal })
        }
    }

    /** [Player] to (# of [Chip]s, sum of [Chip] [Level]s) or `null` if dead */
    fun stat(): Map<Player, Pair<Int, Int>?> =
        order.map { player ->
            player to statOf(player)
        }.toMap()
}

