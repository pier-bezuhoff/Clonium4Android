package com.pierbezuhoff.clonium.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlin.math.min

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

private class LinkedTurns(
    board: EvolvingBoard,
    order: List<PlayerId>,
    private val players: Map<PlayerId, Player>,
    private val coroutineScope: CoroutineScope
) {
    data class Computation(
        private val player: Player,
        private val board: EvolvingBoard,
        private val order: List<PlayerId>,
        val depth: Int
    ) {
        inline fun run(andThen: () -> Unit = {}): Deferred<Link.FutureTurn.Bot.Computed> =
            TODO()
    }

    private lateinit var focus: Link
    private var computing: Deferred<Link.FutureTurn.Bot.Computed>? = null
    private val computations: MutableSet<Computation> = mutableSetOf()

    init {
        require(order.isNotEmpty())
        if (order.size == 1) {
            focus = Link.End
        } else {
            scheduleInitialTurns(board, order)
        }
    }

    private fun scheduleInitialTurns(board: EvolvingBoard, order: List<PlayerId>) {
        val playerId = order.first()
        focus = scheduleTurnOf(board, order, playerId, depth = 0)
    }

    private fun scheduleTurnOf(board: EvolvingBoard, order: List<PlayerId>, playerId: PlayerId, depth: Int): Link {
        val player = players.getValue(playerId)
        val link: Link.FutureTurn
        if (player.tactic is PlayerTactic.Human) {
            val possibleTurns = board.possOf(player.playerId)
            link = Link.FutureTurn.Human.OneOf(
                player.playerId,
                possibleTurns.associateWith { turn ->
                    val nextBoard = board.copy()
                    val transitions = nextBoard.incAnimated(turn)
                    val nextOrder = shiftedOrderOn(order, nextBoard)
                    val nextLink =
                        if (nextOrder.isEmpty()) Link.End
                        else Link.FutureTurn.Unknown(nextOrder.first(), depth = depth + 1)
                    return@associateWith Next(Trans(nextBoard, nextOrder, transitions), nextLink)
                }
            )
            if (computeWidth() < STOP_WIDTH)
                scheduleTurnsAfterHuman(board, link, depth = depth + 1)
        } else {
            link = Link.FutureTurn.Bot.Computing(player.playerId, depth = depth + 1)
            scheduleComputation(player, board, order, depth = depth + 1)
        }
        return link
    }

    private fun scheduleTurnsAfterHuman(rootBoard: EvolvingBoard, root: Link.FutureTurn.Human.OneOf, depth: Int) {
        val (chained, free) = root.nexts.entries.partition { rootBoard.levelAt(it.key) == Level3 }
        for ((_, next) in free) {
            next.link = rescheduleUnknownFutureTurn(next, depth = depth)
        }
        val turns = chained.map { it.key }
        val chains = rootBoard.chains().toList()
        val chainIds = turns.associateWith { pos -> chains.indexOfFirst { pos in it } }
        val chainedLinks: Map<Int, Link> = chains
            .mapIndexed { id, chain ->
                id to rescheduleUnknownFutureTurn(root.nexts.getValue(chain.first()), depth = depth)
            }.toMap()
        for ((turn, next) in chained) {
            next.link = chainedLinks.getValue(chainIds.getValue(turn))
        }
    }

    private fun shiftedOrderOn(order: List<PlayerId>, endBoard: EvolvingBoard): List<PlayerId> {
        require(order.isNotEmpty())
        val playerId = order.first()
        val filteredOrder = order.filter { endBoard.isAlive(playerId) }
        return if (filteredOrder.isEmpty()) emptyList() else filteredOrder.drop(1) + filteredOrder.first()
    }

    private fun rescheduleUnknownFutureTurn(next: Next, depth: Int): Link {
        val link = next.link
        return if (link is Link.FutureTurn.Unknown)
            scheduleTurnOf(next.trans.board, next.trans.order, link.playerId, depth)
        else link
    }

    private fun scheduleComputation(player: Player, board: EvolvingBoard, order: List<PlayerId>, depth: Int) {
        val computation = Computation(player, board, order, depth)
        synchronized(this) {
            if (computing == null) {
                computing = computation.run { runNext() }
            } else {
                computations.add(computation)
            }
        }
    }

    private fun runNext() {
        synchronized(this) {
            computations.minBy { it.depth }?.let { computation ->
                computations.remove(computation)
                computing = computation.run { runNext() }
            }
        }
    }

    private fun discoverUnknowns() {
        TODO("discover and reschedule unknowns until STOP_WIDTH")
    }

    private fun givenHumanTurn(turn: Pos): Trans {
        require(focus is Link.FutureTurn.Human.OneOf)
        val focus = focus as Link.FutureTurn.Human.OneOf
        require(turn in focus.nexts.keys)
        val (trans, nextFocus) = focus.nexts.getValue(turn)
        this.focus = nextFocus
        // TODO: track computation origin
        // TODO: stop senseless computations here
        if (computeWidth() < STOP_WIDTH)
            discoverUnknowns()
        return trans
    }

    private fun requestBotTurn(): Deferred<Pair<Pos, Trans>> {
        require(focus is Link.FutureTurn.Bot)
        val focus = focus as Link.FutureTurn.Bot
        if (focus is Link.FutureTurn.Bot.Computed)
            return coroutineScope.async { focus.pos to focus.next.trans }
        else
            return TODO("fin computing one")
    }

    /** [focus] depth, `null` means infinite: all possibilities are computed */
    private fun computeExploredDepth(): Int? =
        computeDepthOf(focus, depth = 0)

    /** Depth of [link] + [depth], `null` means infinite: all possibilities are computed */
    private fun computeDepthOf(link: Link, depth: Int): Int? =
        when (link) {
            is Link.End -> null
            is Link.TemporalTerminal -> depth
            is Link.FutureTurn.Bot.Computed -> computeDepthOf(link.next.link, depth + 1)
            is Link.FutureTurn.Human.OneOf -> link.nexts.values
                .map { next -> computeDepthOf(next.link, depth + 1) }
                .fold(null) { d0: Int?, d1: Int? ->
                    if (d0 == null) d1 else if (d1 == null) d0 else min(d0, d1)
                }
            else -> throw IllegalArgumentException("Impossible case")
        }

    private fun computeWidth(): Int =
        computeWidthOf(focus)

    private fun computeWidthOf(link: Link): Int =
        when (link) {
            is Link.FutureTurn.Bot.Computed -> computeWidthOf(link.next.link)
            is Link.FutureTurn.Human.OneOf -> link.nexts.values.sumBy { computeWidthOf(it.link) }
            is Link.Terminal -> 1
            else -> throw IllegalArgumentException("Impossible case")
        }

    companion object {
        private const val STOP_WIDTH = 100 // soft max
    }
}

private sealed class Link {
    interface Terminal
    interface TemporalTerminal : Terminal { val depth: Int }

    object End : Link(), Terminal

    sealed class FutureTurn(
        val playerId: PlayerId
    ) : Link() {

        class Unknown(
            playerId: PlayerId,
            override val depth: Int
        ) : FutureTurn(playerId), TemporalTerminal

        sealed class Human(
            playerId: PlayerId
        ) : FutureTurn(playerId) {
            class OneOf(
                playerId: PlayerId,
                val nexts: Map<Pos, Next>
            ) : Human(playerId)
        }

        sealed class Bot(
            playerId: PlayerId
        ) : FutureTurn(playerId) {
            class Computed(
                playerId: PlayerId,
                val pos: Pos,
                val next: Next
            ) : Bot(playerId)
            class Computing(
                playerId: PlayerId,
                override val depth: Int
            ) : Bot(playerId), TemporalTerminal
        }
    }
}

private data class Next(
    val trans: Trans,
    var link: Link
)

private data class Trans(
    val board: EvolvingBoard,
    val order: List<PlayerId>,
    val transitions: Sequence<Transition>
)
