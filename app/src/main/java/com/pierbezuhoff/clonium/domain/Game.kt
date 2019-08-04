package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.*
import java.io.Serializable

interface Game {
    /** Initial [Game] parameters, [Game] can be constructed from it */
    data class State(
        val board: SimpleBoard,
        /** All the rest of [board]'s [Player]s are [HumanPlayer]s */
        val bots: Map<PlayerId, PlayerTactic.Bot>,
        /** `null` means shuffle before starting game */
        val order: List<PlayerId>? = null
    ) : Serializable {
        companion object {
            val example = run {
                val board = BoardFactory.spawn4players(EmptyBoardFactory.SMALL_TOWER)
                val bots: Map<PlayerId, PlayerTactic.Bot> =
                    mapOf(
                        PlayerId1 to PlayerTactic.Bot.RandomPicker,
                        PlayerId2 to PlayerTactic.Bot.RandomPicker,
                        PlayerId3 to PlayerTactic.Bot.RandomPicker
//                LevelMaximizerBot(PlayerId(2), depth = 1),
//                ChipCountMaximizerBot(PlayerId(3), depth = 1)
                    )
                val order: List<PlayerId>? = listOf(
                    PlayerId0,
                    PlayerId1,
                    PlayerId2,
                    PlayerId3
                )
                State(board, bots, order)
            }
        }
    }

    val board: Board
    val players: Map<PlayerId, Player>
    val order: List<Player>
    val lives: Map<Player, Boolean>
    val currentPlayer: Player

    val lastTurn: Pos?

    val coroutineScope: CoroutineScope

    fun isAlive(player: Player): Boolean =
        lives.getValue(player)

    /** <= 1 alive player */
    fun isEnd(): Boolean =
        lives.values.filter { it }.size <= 1


    /** Possible turns ([Pos]s) of [currentPlayer] */
    fun possibleTurns(): Set<Pos> =
        board.possOf(currentPlayer.playerId)

    fun nextPlayer(): Player {
        val ix = order.indexOf(currentPlayer)
        return (order.drop(ix + 1) + order).first { isAlive(it) }
    }

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

    fun humanTurn(pos: Pos): Sequence<Transition>

    fun botTurnAsync(): Deferred<Sequence<Transition>>

    interface Builder {
        fun of(gameState: State, coroutineScope: CoroutineScope): Game
    }
}

class SimpleGame(
    override val board: EvolvingBoard,
    bots: Set<BotPlayer>,
    initialOrder: List<PlayerId>? = null,
    override val coroutineScope: CoroutineScope
) : Game {
    override val players: Map<PlayerId, Player>
    override val order: List<Player>
    override val lives: MutableMap<Player, Boolean>
    @Suppress("RedundantModalityModifier") // or else "error: property must be initialized or be abstract"
    final override var currentPlayer: Player
    override var lastTurn: Pos? = null
        private set

    init {
        initialOrder?.let {
            require(board.players() == initialOrder.toSet()) { "order is incomplete: $initialOrder instead of ${board.players()}" }
        }
        val playerIds = initialOrder ?: board.players().shuffled().toList()
        require(bots.map { it.playerId }.all { it in playerIds }) { "Not all bot ids are on the board: external bots ${bots.filter { it.playerId !in playerIds }}" }
        val botIds = bots.map { it.playerId }
        val botMap = bots.associateBy { it.playerId }
        val humanMap = (playerIds - botIds).associateWith { HumanPlayer(it) }
        players = botMap + humanMap
        order = playerIds.map { players.getValue(it) }
        require(order.isNotEmpty()) { "SimpleGame should have players" }
        lives = order.associateWith { board.possOf(it.playerId).isNotEmpty() }.toMutableMap()
        require(lives.values.any()) { "Someone should be alive" }
        currentPlayer = order.first { isAlive(it) }
    }

    constructor(gameState: Game.State, coroutineScope: CoroutineScope) : this(
        PrimitiveBoard(gameState.board),
        gameState.bots
            .map { (playerId, tactic) -> tactic.toPlayer(playerId) }
            .toSet(),
        gameState.order,
        coroutineScope
    )

    private fun makeTurn(turn: Pos): Sequence<Transition> {
        require(turn in possibleTurns())
        lastTurn = turn
        val transitions = board.incAnimated(turn)
        lives.clear()
        order.associateWithTo(lives) { board.possOf(it.playerId).isNotEmpty() }
        currentPlayer = nextPlayer()
        return transitions
    }

    override fun humanTurn(pos: Pos): Sequence<Transition> {
        require(currentPlayer is HumanPlayer)
        return makeTurn(pos)
    }

    override fun botTurnAsync(): Deferred<Sequence<Transition>> {
        require(currentPlayer is BotPlayer)
        return coroutineScope.async(Dispatchers.Default) {
            val turn =
                with(currentPlayer as BotPlayer) {
                    makeTurnAsync(board, order.map { it.playerId })
                }.await()
            return@async makeTurn(turn)
        }
    }

    object Builder : Game.Builder {
        override fun of(gameState: Game.State, coroutineScope: CoroutineScope): Game =
            SimpleGame(gameState, coroutineScope)
    }
}
