package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.*
import java.io.Serializable

typealias GameStat = Map<Player, Game.PlayerStat>
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
                val board = SimpleBoard.Factory.spawn4players(SimpleEmptyBoard.Examples.SMALL_TOWER)
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

    data class PlayerStat(val chipCount: Int, val sumLevel: Int, val conquered: Double)

    val board: Board
    val players: Map<PlayerId, Player>
    val order: List<Player>
    val lives: Map<Player, Boolean>
    val currentPlayer: Player
    val nPlayers: Int
        get() = order.filter { lives.getValue(it) }.size

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

    /** [Player] to (# of [Chip]s, sum of [Chip]'s [Level]s, percent of total [board]'s [Pos]s) */
    fun stat(): GameStat {
        val nPoss = board.asPosSet().size
        return order.associateWith { player ->
            val chipCount = board.possOf(player.playerId).size
            return@associateWith PlayerStat(
                chipCount = chipCount,
                sumLevel = board.levelOf(player.playerId),
                conquered = chipCount.toDouble() / nPoss
            )
        }
    }

    fun humanTurn(pos: Pos): Sequence<Transition>

    suspend fun botTurn(): Pair<Pos, Sequence<Transition>>

    interface Factory {
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

    override suspend fun botTurn(): Pair<Pos, Sequence<Transition>> {
        require(currentPlayer is BotPlayer)
        val turn = (currentPlayer as BotPlayer).makeTurn(board, order.filter { lives.getValue(it) }.map { it.playerId })
        return turn to makeTurn(turn)
    }

    object Factory : Game.Factory {
        override fun of(gameState: Game.State, coroutineScope: CoroutineScope): Game =
            SimpleGame(gameState, coroutineScope)
    }
}
