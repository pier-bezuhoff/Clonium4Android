package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.*

class AsyncGame(
    override val board: EvolvingBoard,
    bots: Set<BotPlayer>,
    initialOrder: List<PlayerId>? = null
) : Game {
    override val players: Map<PlayerId, Player>
    override val order: List<Player>
    override val lives: MutableMap<Player, Boolean>
    @Suppress("RedundantModalityModifier") // or else "error: property must be initialized or be abstract"
    final override var currentPlayer: Player

    init {
        initialOrder?.let {
            require(board.players() == initialOrder.toSet()) { "order is incomplete: $initialOrder instead of ${board.players()}" }
        }
        val playerIds = initialOrder ?: board.players().shuffled().toList()
        require(bots.map { it.playerId }.all { it in playerIds }) { "Not all bot ids are on the board" }
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

    constructor(gameState: Game.State) : this(
        PrimitiveBoard(gameState.board),
        gameState.bots
            .map { (playerId, tactic) -> tactic.toPlayer(playerId) }
            .toSet(),
        gameState.order
    )

    private fun makeTurn(turn: Pos): Sequence<Transition> {
        require(turn in possibleTurns())
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

    override fun CoroutineScope.botTurnAsync(): Deferred<Sequence<Transition>> {
        require(currentPlayer is BotPlayer)
        return async(Dispatchers.Default) {
            val turn =
                with(currentPlayer as BotPlayer) {
                    makeTurnAsync(board, order.map { it.playerId })
                }.await()
            return@async makeTurn(turn)
        }
    }
}

private class _TurnSequence(
    board: EvolvingBoard,
    order: List<PlayerId>,
    private val players: Map<PlayerId, Player>,
    private val coroutineScope: CoroutineScope
) {
    private var turns: MutableMap<HumanTurns, Chain> = mutableMapOf()
    private var layout: MutableMap<HumanTurns, ChainLink> = mutableMapOf()

    init {
        require(order.isNotEmpty())
        if (order.size > 1) {
            scheduleInitialTurns(board, order)
        }
    }

    private fun scheduleInitialTurns(board: EvolvingBoard, order: List<PlayerId>) {
        val playerId = order.first()
        val player = players.getValue(playerId)
        if (player is BotPlayer) {
            layout[HumanTurns(emptyList())] = ChainLink.Computing(Turn.Bot.Computing(playerId))
        } else {
            val chains = board.chains()
            turns = mutableMapOf<HumanTurns, Chain>().apply {
                board.distinctTurns(playerId)
                    .map { turn ->
                        val endBoard = board.copy()
                        Turn.Human(
                            playerId,
                            turn,
                            if (board.levelAt(turn)!! != Level3)
                                null
                            else
                                chains.first { turn in it }
                                    .filter { board.playerAt(it) == playerId }
                                    .toSet()
                            ,
                            endBoard.incAnimated(turn)
                        ) to endBoard
                    }.associateTo(this) { (humanTurn: Turn.Human, endBoard: EvolvingBoard) ->
                        HumanTurns(listOf(humanTurn)) to Chain(emptyList(), BoardState(endBoard, shiftedOrderOn(order, endBoard)))
                    }
            }
        }
    }

    private fun shiftedOrderOn(order: List<PlayerId>, endBoard: EvolvingBoard): List<PlayerId> {
        require(order.isNotEmpty())
        val playerId = order.first()
        val filteredOrder = order.filter { endBoard.isAlive(playerId) }
        return filteredOrder.drop(1) + filteredOrder.first()
    }
}

private class LinkedTurns(
    board: EvolvingBoard,
    order: List<PlayerId>,
    private val players: Map<PlayerId, Player>,
    private val coroutineScope: CoroutineScope
) {
    private lateinit var focus: Link

    init {
        scheduleInitialTurns(board, order)
    }

    fun scheduleInitialTurns(board: EvolvingBoard, order: List<PlayerId>) {
        focus = Link.Start(board, "")
    }
}

private sealed class Link(val startBoard: EvolvingBoard) {
    interface Terminal

    class Start(
        startBoard: EvolvingBoard,
        val next: FutureTurn
    ) : Link(startBoard)

    sealed class FutureTurn(
        startBoard: EvolvingBoard,
        val playerId: PlayerId
    ) : Link(startBoard) {

        sealed class Human(
            startBoard: EvolvingBoard, playerId: PlayerId
        ) : FutureTurn(startBoard, playerId) {

            class OneOf(
                startBoard: EvolvingBoard, playerId: PlayerId,
                val possibleTurns: Set<Pos>,
                val nexts: Map<Pos, FutureTurn>
            ) : Human(startBoard, playerId)

            class Deferred(
                startBoard: EvolvingBoard, playerId: PlayerId
            ) : Human(startBoard, playerId), Terminal
        }

        sealed class Bot(
            startBoard: EvolvingBoard, playerId: PlayerId
        ) : FutureTurn(startBoard, playerId) {

            class Computed(
                startBoard: EvolvingBoard, playerId: PlayerId,
                val pos: Pos,
                val next: FutureTurn
            ) : Bot(startBoard, playerId)

            class Computing(
                startBoard: EvolvingBoard, playerId: PlayerId
            ) : Bot(startBoard, playerId), Terminal
        }

    }
}

private class HumanTurns(turns: List<Turn.Human>) : List<Turn.Human> by turns

private data class Chain(val turns: List<Turn.Bot>, val endState: BoardState)

private sealed class ChainLink {
    class Computed(val turn: Turn.Bot.Computed, val boardState: BoardState) : ChainLink()
    class Computing(val turn: Turn.Bot.Computing) : ChainLink()
    class Unknown(val turn: Turn.Bot.Unknown) : ChainLink()
}

private data class BoardState(val board: EvolvingBoard, val order: List<PlayerId>)

private sealed class Turn(val playerId: PlayerId) {
    interface Known {
        val turn: Pos
        val transitions: Sequence<Transition>
    }

    class Human(
        playerId: PlayerId,
        override val turn: Pos,
        /** [Set] of [Pos] with [playerId]'s [Chip]s with [Level3] if [Chip] at [turn] has [Level3] else `null` */
        val posChain: Set<Pos>?,
        override val transitions: Sequence<Transition>
    ) : Turn(playerId), Known

    sealed class Bot(playerId: PlayerId) : Turn(playerId) {

        class Computed(
            playerId: PlayerId,
            override val turn: Pos,
            override val transitions: Sequence<Transition>
        ) : Bot(playerId), Known

        class Computing(playerId: PlayerId) : Bot(playerId)

        class Unknown(playerId: PlayerId) : Bot(playerId)
    }
}
