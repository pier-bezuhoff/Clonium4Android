package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.impossibleCaseOf
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import kotlin.Comparator
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

class G(
    val game: Game = run {
        val board = BoardFactory.spawn4players(EmptyBoardFactory.SMALL_TOWER)
        val bots: Set<BotPlayer> =
            setOf(
//                RandomPickerBot(PlayerId0),
                LevelMaximizerBot(PlayerId0, depth = 1),
                RandomPickerBot(PlayerId1),
//                RandomPickerBot(PlayerId2),
//                ChipCountMaximizerBot(PlayerId2, depth = 1),
                RandomPickerBot(PlayerId3)
            )
        val order = listOf(PlayerId0, PlayerId1, PlayerId2, PlayerId3)
        SimpleGame(PrimitiveBoard(board), bots, order)
    }
) {
    private val linkedTurns = LinkedTurns(
        PrimitiveBoard(game.board),
        game.order.map { it.playerId },
        game.players,
        GlobalScope
    )

    val f get() = println(linkedTurns.focus)
    val w: Int get() = linkedTurns.computeWidth()
    val c get() = println(linkedTurns.computing)
    val sc get() = println(linkedTurns.scheduledComputings)

    fun g(turn: Pos) {
        linkedTurns.givenHumanTurn(turn)
        println(linkedTurns.focus)
    }

    fun r() {
        linkedTurns.requestBotTurnAsync()
    }

    fun d() {
        linkedTurns.discoverUnknowns()
    }
}

@Suppress("unused")
private class LinkedTurns(
    board: EvolvingBoard,
    order: List<PlayerId>,
    private val players: Map<PlayerId, Player>,
    private val coroutineScope: CoroutineScope
) {
    internal var focus: Link
    internal var computing: Link.FutureTurn.Bot.Computing? = null
    internal val scheduledComputings: MutableSet<Link.FutureTurn.Bot.ScheduledComputing> = mutableSetOf()
    // MAYBE: use PriorityBlockingQueue
    internal val unknowns: PriorityQueue<Link.Unknown> =
        PriorityQueue(10, Comparator { a, b -> a.depth.compareTo(b.depth) })

    init {
        require(order.isNotEmpty())
        if (order.size == 1) {
            focus = Link.End
        } else {
            focus = scheduleTurnOf(board, order, depth = 0)
        }
    }

    private fun scheduleTurnOf(board: EvolvingBoard, order: List<PlayerId>, depth: Int): Link {
        println("scheduleTurnOf($board, $order, depth = $depth)\n")
        val playerId = order.first()
        val player = players.getValue(playerId)
        val link: Link.FutureTurn
        if (player.tactic is PlayerTactic.Human) {
            val possibleTurns = board.possOf(player.playerId)
            link = Link.FutureTurn.Human.OneOf(
                player.playerId,
                possibleTurns.associateWith { turn ->
                    val nextBoard = board.copy()
                    val transitions = nextBoard.incAnimated(turn)
                    val nextOrder = nextBoard.shiftOrder(order)
                    val nextLink: Link =
                        if (nextOrder.size <= 1) Link.End
                        else Link.Unknown(depth = depth + 1)
                    return@associateWith Next(Trans(nextBoard, nextOrder, transitions), nextLink)
                }
            )
//            if (depth == 0 || computeWidth() < STOP_WIDTH) // depth == 0 => launched from init
//                scheduleTurnsAfterHuman(board, link)
        } else {
            link = scheduleComputation(player as BotPlayer, board, order, depth = depth)
        }
        println("scheduleTurnOf -> \n$link\n")
        return link
    }

    private fun scheduleTurnsAfterHuman(rootBoard: EvolvingBoard, root: Link.FutureTurn.Human.OneOf) {
        println("scheduleTurnsAfterHuman($rootBoard,\n$root\n)\n")
        val (chained, free) =
            root.nexts.entries.partition { rootBoard.levelAt(it.key) == Level3 }
        for ((_, next) in free) {
            rescheduleUnknownFutureTurn(next)
        }
        val turns = chained.map { it.key }
        val chains = rootBoard.chains().toList()
        val chainIds = turns.associateWith { turn -> chains.indexOfFirst { turn in it } }
        val chainedLinks: Map<Int, Link> = chains
            .mapIndexed { id, chain ->
                id to rescheduleUnknownFutureTurn(root.nexts.getValue(chain.first()))
            }.toMap()
        for ((turn, next) in chained) {
            // turns in the same chain have common next.link
            next.link = chainedLinks.getValue(chainIds.getValue(turn))
        }
    }

    private fun rescheduleUnknownFutureTurn(next: Next): Link {
        println("rescheduleUnknownFutureTurn(\n$next\n)\n")
        val link = next.link
        val nextLink = if (link is Link.Unknown)
            scheduleTurnOf(next.trans.board, next.trans.order, link.depth)
        else link
        next.link = nextLink
        return nextLink
    }

    private fun scheduleComputation(bot: BotPlayer, board: EvolvingBoard, order: List<PlayerId>, depth: Int): Link.FutureTurn.Bot {
        println("scheduleComputation($bot, $board,\n$order, depth = $depth)\n")
        val computation = Computation(bot, board, order, depth)
        synchronized(ComputingLock) {
            return if (computing == null) {
                val newComputing = Link.FutureTurn.Bot.Computing(
                    bot.playerId, depth, computation, runComputationAsync(computation)
                )
                computing = newComputing
                newComputing
            } else {
                val scheduledComputing = Link.FutureTurn.Bot.ScheduledComputing(bot.playerId, depth, computation)
                scheduledComputings.add(scheduledComputing)
                scheduledComputing
            }
        }
    }

    private fun runComputationAsync(computation: Computation): Deferred<Link.FutureTurn.Bot.Computed> =
        with(computation) { coroutineScope.runAsync { runNext(it) } }

    private fun runNext(computed: Link.FutureTurn.Bot.Computed) {
        println("runNext(\n$computed\n)\n")
        synchronized(ComputingLock) {
            require(computing != null)
            val (next, foundComputing) = findComputing()!!
            require(foundComputing == computing)
            if (next == null)
                focus = computed
            else
                next.link = computed
            computing = null
        }
        rescheduleUnknownFutureTurn(computed.next)
        runNext()
    }

    private fun runNext() {
        synchronized(ComputingLock) {
            require(computing == null)
            scheduledComputings.minBy { it.depth }?.let { scheduled ->
                scheduledComputings.remove(scheduled)
                val newComputing = Link.FutureTurn.Bot.Computing(
                    scheduled.playerId, scheduled.depth, scheduled.computation,
                    runComputationAsync(scheduled.computation)
                )
                println("new computing = $newComputing\n")
                computing = newComputing
            } ?: discoverUnknowns()
        }
    }

    private fun findComputing(next: Next? = null, root: Link = focus): Pair<Next?, Link.FutureTurn.Bot.Computing>? =
        when (root) {
            is Link.FutureTurn.Bot.Computing -> next to root
            is Link.FutureTurn.Bot.Computed -> findComputing(root.next, root.next.link)
            is Link.FutureTurn.Human.OneOf -> root.nexts.values.map { findComputing(it, it.link) }.firstOrNull()
            is Link.Terminal -> null
            else -> impossibleCaseOf(root)
        }

    internal fun discoverUnknowns() {
        println("discoverUnknowns()\n")
        when (val focus = focus) {
            is Link.Unknown -> impossibleCaseOf(focus)
            is Link.FutureTurn.Bot.Computed -> discoverUnknowns(focus.next)
            is Link.FutureTurn.Human.OneOf -> for ((_, next) in focus.nexts) discoverUnknowns(next)
        }
    }

    private fun discoverUnknowns(next: Next) {
        if (computeWidth() < STOP_WIDTH)
            when (val root = next.link) {
                is Link.Unknown -> rescheduleUnknownFutureTurn(next)
                is Link.FutureTurn.Bot.Computed -> discoverUnknowns(root.next)
                is Link.FutureTurn.Human.OneOf -> for ((_, newNext) in root.nexts) discoverUnknowns(newNext)
            }
    }

    fun givenHumanTurn(turn: Pos): Trans {
        require(focus is Link.FutureTurn.Human.OneOf)
        val focus = focus as Link.FutureTurn.Human.OneOf
        require(turn in focus.nexts.keys)
        val (trans, nextFocus) = focus.nexts.getValue(turn)
        this.focus = nextFocus
        collapseComputations(focus, turn)
        if (computeWidth() < STOP_WIDTH)
            discoverUnknowns()
        return trans
    }

    private fun collapseComputations(root: Link.FutureTurn.Human.OneOf, actualTurn: Pos) {
        println("collapseComputations(\n$root,\nactualTurn = $actualTurn)")
        for ((turn, next) in root.nexts)
            if (turn != actualTurn)
                stopComputations(next)
        synchronized(ComputingLock) {
            if (computing == null) // when it was stopped
                runNext()
        }
    }

    private fun stopComputations(next: Next) {
        when (val root = next.link) {
            is Link.FutureTurn.Bot.ScheduledComputing -> {
                scheduledComputings.remove(root)
                next.link = Link.Unknown(root.depth)
            }
            is Link.FutureTurn.Bot.Computing -> {
                synchronized(ComputingLock) {
                    require(computing == root)
                    computing = null
                }
                root.deferred.cancel()
                next.link = Link.Unknown(root.depth)
            }
            is Link.FutureTurn.Human.OneOf -> for (newNext in root.nexts.values) stopComputations(newNext)
            is Link.FutureTurn.Bot.Computed -> stopComputations(root.next)
        }
    }

    fun requestBotTurnAsync(): Deferred<Pair<Pos, Trans>> {
        require(focus is Link.FutureTurn.Bot)
        return when (val focus = focus as Link.FutureTurn.Bot) {
            is Link.FutureTurn.Bot.Computed -> {
                this.focus = focus.next.link
                return coroutineScope.async { focus.pos to focus.next.trans }
            }
            is Link.FutureTurn.Bot.Computing -> coroutineScope.async {
                val computed = focus.deferred.await()
                this@LinkedTurns.focus = computed.next.link
                return@async computed.pos to computed.next.trans
            }
            else -> impossibleCaseOf(focus)
        }
    }

    /** Depth of [link] + [depth], `null` means infinite: all possibilities are computed */
    internal fun computeDepth(link: Link = focus, depth: Int = 0): Int? =
        when (link) {
            is Link.End -> null
            is Link.TemporalTerminal -> depth
            is Link.FutureTurn.Bot.Computed -> computeDepth(link.next.link, depth + 1)
            is Link.FutureTurn.Human.OneOf -> link.nexts.values
                .map { next -> computeDepth(next.link, depth + 1) }
                .fold(null as Int?) { d0: Int?, d1: Int? ->
                    if (d0 == null) d1 else if (d1 == null) d0 else min(d0, d1)
                }
            else -> impossibleCaseOf(link)
        }

    internal fun computeWidth(root: Link = focus): Int =
        when (root) {
            is Link.FutureTurn.Bot.Computed -> computeWidth(root.next.link)
            is Link.FutureTurn.Human.OneOf -> root.nexts.values.sumBy { computeWidth(it.link) }
            is Link.Terminal -> 1
            else -> impossibleCaseOf(root)
        }

    object ComputingLock
    companion object {
        private const val STOP_WIDTH = 100 // soft max width
    }
}

private sealed class Link {
    interface Transient
    interface Terminal
    interface TemporalTerminal : Terminal

    object End : Link(), Terminal {
        override fun toString() =
            "Link.End"
    }

    class Unknown(
        val depth: Int
    ) : Link(), TemporalTerminal {
        override fun toString() =
            "Link.Unknown(depth = $depth)"
    }

    sealed class FutureTurn(
        val playerId: PlayerId
    ) : Link() {

        sealed class Human(
            playerId: PlayerId
        ) : FutureTurn(playerId) {
            class OneOf(
                playerId: PlayerId,
                val nexts: Map<Pos, Next>
            ) : Human(playerId), Transient {
                override fun toString(indent: Int) =
                    indentOf(indent) + "Link.FutureTurn.Human.OneOf($playerId):" + nexts.entries.joinToString(
                        prefix = "\n${indentOf(indent + 1)}[\n",
                        separator = ",\n",
                        postfix = "\n${indentOf(indent + 1)}]"
                    ) { (pos, next) -> indentOf(indent + 1) + "$pos ->\n${next.toString(indent + 2)}" }
            }
        }

        sealed class Bot(
            playerId: PlayerId
        ) : FutureTurn(playerId) {
            class Computed(
                playerId: PlayerId,
                val pos: Pos,
                val next: Next
            ) : Bot(playerId), Transient {
                override fun toString(indent: Int) =
                    indentOf(indent) + "Link.FutureTurn.Bot.Computed($playerId, $pos):\n" + next.toString(indent + 1)
            }
            class Computing(
                playerId: PlayerId,
                val depth: Int,
                val computation: Computation,
                val deferred: Deferred<Computed>
            ) : Bot(playerId), TemporalTerminal {
                override fun toString(): String =
                    "Link.FutureTurn.Bot.Computing($playerId, depth = $depth, $computation)"
            }
            class ScheduledComputing(
                playerId: PlayerId,
                val depth: Int,
                val computation: Computation
            ) : Bot(playerId), TemporalTerminal {
                override fun toString(): String =
                    "Link.FutureTurn.Bot.ScheduledComputing($playerId, depth = $depth, $computation)"
            }
        }
    }

    internal open fun toString(indent: Int): String =
        indentOf(indent) + toString()
    internal fun indentOf(indent: Int): String =
        "  ".repeat(indent)
    override fun toString(): String =
        toString(0)
}

private data class Next(
    val trans: Trans,
    var link: Link
) {
    fun toString(indent: Int): String =
        link.toString(indent)

    override fun toString(): String =
        toString(0)
}

private data class Trans(
    val board: EvolvingBoard,
    val order: List<PlayerId>,
    val transitions: Sequence<Transition>
)

private data class Computation(
    private val bot: BotPlayer,
    private val board: EvolvingBoard,
    private val order: List<PlayerId>,
    private val depth: Int
) {
    inline fun CoroutineScope.runAsync(
        crossinline andThen: (Link.FutureTurn.Bot.Computed) -> Unit = {}
    ): Deferred<Link.FutureTurn.Bot.Computed> =
        async {
            print("(...")
            val startTime = System.currentTimeMillis()
            val turn = with(bot) { makeTurnAsync(board, order).await() }
            val elapsed = System.currentTimeMillis() - startTime
            print("$elapsed ms)")
            val endBoard = board.copy()
            val transitions = endBoard.incAnimated(turn)
            val endOrder = endBoard.shiftOrder(order)
            val trans = Trans(endBoard, endOrder, transitions)
            val nextLink: Link = if (endOrder.size <= 1) Link.End else Link.Unknown(depth + 1)
            val computed = Link.FutureTurn.Bot.Computed(
                bot.playerId, turn, Next(trans, nextLink)
            )
            andThen(computed)
            computed
        }
}

