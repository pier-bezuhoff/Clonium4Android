package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.min

// TODO: do not store transitions in Trans: useless
class AsyncGame(
    override val board: EvolvingBoard,
    bots: Set<BotPlayer>,
    initialOrder: List<PlayerId>? = null,
    override val coroutineScope: CoroutineScope
) : Any()
    , Game
    , Logger by AndroidLogger("AsyncGame")
{
    override val players: Map<PlayerId, Player>
    override val order: List<Player>
    override val lives: MutableMap<Player, Boolean>
    @Suppress("RedundantModalityModifier") // or else "error: property must be initialized or be abstract"
    final override var currentPlayer: Player
    private val linkedTurns: LinkedTurns
    override var lastTurn: Pos? = null
        private set

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
        require(order.isNotEmpty()) { "AsyncGame should have players" }
        lives = order.associateWith { board.possOf(it.playerId).isNotEmpty() }.toMutableMap()
        require(lives.values.any()) { "Someone should be alive" }
        currentPlayer = order.first { isAlive(it) }
        linkedTurns = LinkedTurns(board, order.map { it.playerId }, players, coroutineScope)
    }

    constructor(gameState: Game.State, coroutineScope: CoroutineScope) : this(
        PrimitiveBoard(gameState.board),
        gameState.bots
            .map { (playerId, tactic) -> tactic.toPlayer(playerId) }
            .toSet(),
        gameState.order,
        coroutineScope
    )

    private fun makeTurn(turn: Pos, trans: Trans): Sequence<Transition> {
        require(turn in possibleTurns()) { "turn $turn of $currentPlayer is not possible on board $board" }
        logI("makeTurn($turn, trans)")
        lastTurn = turn
        val transitions = board.incAnimated(turn)
        require(board == trans.board)
        lives.clear()
        order.associateWithTo(lives) { board.possOf(it.playerId).isNotEmpty() }
        currentPlayer = nextPlayer()
        require(currentPlayer.playerId == trans.order.first())
        return transitions
    }

    override fun humanTurn(pos: Pos): Sequence<Transition> {
        require(currentPlayer is HumanPlayer)
        val trans = linkedTurns.givenHumanTurn(pos)
        return makeTurn(pos, trans)
    }

    override fun botTurnAsync(): Deferred<Sequence<Transition>> {
        require(currentPlayer is BotPlayer)
        return coroutineScope.async(Dispatchers.Default) {
            val (pos, trans) = linkedTurns.requestBotTurnAsync().await()
            return@async makeTurn(pos, trans)
        }
    }

    object Builder : Game.Builder {
        override fun of(gameState: Game.State, coroutineScope: CoroutineScope): Game =
            AsyncGame(gameState, coroutineScope)
    }
}

/**
 *                                            (check next.trans.order.first())
 * discoverUnknowns ---> Next(Unknown).scheduleTurn => * Next(Unknown -> OneOf(Unknown...)) ->|                          (check computing is null)
 *  (use unknowns)  \--> ...                           * ------------------------------------> Next(Unknown).scheduledComputation => * Next(Unknown) to scheduledComputings ->|
 * ^                 \-> ...                                                                  (use computing & scheduledComputings)  * Next(Unknown) to computing ----------->|:~>.
 * |                                                                                                                                                                              |
 * |                                                                                                                                                                              |
 * |                                 .~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~> (async) |
 * |                                 ^ (async)                                                                                                                                   \|
 * | (may try to interrupt           |                              (check scheduledComputings is empty)  (computing = null; add unknown after computed to unknowns)              |
 * | parent branch => synchronize)   .~~~~~:|<- scheduledComputing to computing * <================= runNext <---------------------- runNext(Computed) <~~~~~~~~~~~~~~~~~~~~~~~~~~.
 * .<-------------------------------------------------------------------------- *    (use computing & scheduledComputings)      (use computing & unknowns)
 * ^                                                                                                      ^
 * | (secondary branch => synchronize)                                                                    \--------------.
 * .<---------------------------------------------------------------------------------------------------.                |
 *                                                                                                      |                | (secondary branch => synchronize)
 *                                                        (structural recursion)                        |                |
 *  givenHumanTurn -> setFocus -> collapseComputations \---> stopComputations =====> discoverUnknowns --.                |
 *                                                      \--> ...             /       * (if computing has been stopped) ->.
 *                                                       \-> ...            /
 *                               (use computing, scheduledComputings, unknowns)
 *
 *        (focus is Computed | Computing)
 *  requestBotTurnAsync => * setFocus -> return
 *                         * ~~~~~~> wait bot -> setFocus -> return
 *                           (async)
 */
private class LinkedTurns(
    board: EvolvingBoard,
    order: List<PlayerId>,
    private val players: Map<PlayerId, Player>,
    private val coroutineScope: CoroutineScope
) : Any()
    , Logger by AndroidLogger("LinkedTurns", Logger.Level.INFO)
{
    private var computing: NextAs<Link.FutureTurn.Bot.Computing>? = null
    private val scheduledComputings: AbstractQueue<NextAs<Link.FutureTurn.Bot.ScheduledComputing>> =
        PriorityBlockingQueue(1, NextAsDepthComparator())
    private val unknowns: AbstractQueue<NextAs<Link.Unknown>> =
        PriorityQueue(10, NextAsDepthComparator())
    private var start: Link.Start = Link.Start(nextUnknown(Trans(board, order, emptySequence()), depth = 1))
    private val focus: Link get() = start.next.link
    private var nOfTraversedTurns = 0

    init {
        require(order.isNotEmpty())
        if (order.size == 1) {
            unknowns.clear()
            start.next.link = Link.End
        } else {
            discoverUnknowns()
        }
    }

    // parent must wrap it in synchronized(UnknownsLock)
    private fun Next.scheduleTurn(depth: Int) {
        val board = trans.board
        val order = trans.order
        require(order.isNotEmpty())
        logV("${this}\n.scheduleTurn(depth = $depth)\n")
        val playerId = order.first()
        when (val player = players.getValue(playerId)) {
            is HumanPlayer -> {
                val possibleTurns = board.possOf(player.playerId)
                link = Link.FutureTurn.Human.OneOf(
                    player.playerId,
                    // TODO: connect chained turns' unknowns (as in scheduleTurnsAfterHuman)
                    possibleTurns.associateWith { turn ->
                        val nextBoard = board.copy()
                        val transitions = nextBoard.incAnimated(turn)
                        val nextOrder = nextBoard.shiftOrder(order)
                        val trans = Trans(nextBoard, nextOrder, transitions)
                        return@associateWith if (nextOrder.size <= 1)
                            Next(trans, Link.End)
                        else
                            nextUnknown(trans, depth + 1)
                    }
                )
            }
            is BotPlayer ->
                scheduleComputation(player, board, order, depth = depth)
            else -> impossibleCaseOf(player)
        }
    }

    // parent must wrap it in synchronized(UnknownsLock)
    private fun nextUnknown(trans: Trans, depth: Int): Next {
        val unknown = Link.Unknown(depth)
        val nextAs = NextAs(trans, unknown)
        unknowns.add(nextAs)
        return nextAs.next
    }

/*
    private fun scheduleTurnsAfterHuman(rootBoard: EvolvingBoard, root: Link.FutureTurn.Human.OneOf) {
        logV("scheduleTurnsAfterHuman($rootBoard,\n$root\n)\n")
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
*/

    private fun Next.scheduleComputation(bot: BotPlayer, board: EvolvingBoard, order: List<PlayerId>, depth: Int) {
        logV("scheduleComputation($bot, $board,\n$order, depth = $depth)\n")
        // MAYBE: random picker reports no possible turns (reproducible?)
        require(board.possOf(bot.playerId).isNotEmpty())
        val computation = Computation(bot, board, order, depth)
        synchronized(Lock) {
            if (computing == null) {
                val newComputing = Link.FutureTurn.Bot.Computing(
                    bot.playerId, depth, computation, runComputationAsync(computation)
                )
                computing = NextAs(this, newLink = newComputing)
            } else {
                val scheduledComputing = Link.FutureTurn.Bot.ScheduledComputing(bot.playerId, depth, computation)
                scheduledComputings.add(NextAs(this, scheduledComputing))
            }
            Unit
        }
    }

    private fun runComputationAsync(computation: Computation): Deferred<Link.FutureTurn.Bot.Computed> =
        with(computation) { coroutineScope.runAsync { runNext(it) } }

    private fun runNext(computed: Link.FutureTurn.Bot.Computed) {
        logV("runNext(\n$computed\n)")
        synchronized(Lock) {
            require(computing != null)
            computing!!.next.link = computed
            logV("runNext(computed) =>\ncomputing = $computing")
            computing = null
            // add external unknown (from Computation.runAsync)
            unknowns.add(NextAs(computed.next, computed.next.link as Link.Unknown))
            logV("runNext(computed) =>\nfocus = $focus")
            runNext()
        }
    }

    // parent must wrap it in synchronized(ComputingLock)
    private fun runNext() {
        require(computing == null)
        scheduledComputings.poll()?.let { scheduledNextAs ->
            val newComputing = with(scheduledNextAs.link) {
                Link.FutureTurn.Bot.Computing(
                    playerId, depth, computation,
                    runComputationAsync(computation)
                )
            }
            logV("new computing =\n$newComputing\n")
            computing = NextAs(scheduledNextAs.next, newLink = newComputing)
        } ?: discoverUnknowns()
    }

    private fun discoverUnknowns() {
        logV("discoverUnknowns()\n")
        synchronized(Lock) {
            while (unknowns.isNotEmpty() && (computeDepth(depth = 0)!! <= SOFT_MIN_DEPTH || computeWidth() < SOFT_MAX_WIDTH)) {
                val nextAs = unknowns.remove()
                nextAs.next.scheduleTurn(nextAs.link.depth)
            }
        }
        if (computeWidth() >= SOFT_MAX_WIDTH)
            logI("SOFT_MAX_WIDTH = $SOFT_MAX_WIDTH hit while discovering unknowns\n")
    }

    private fun setFocus(next: Next) {
        logV("focus = $focus")
        logV("move focus = $focus\nto new focus = ${next.link}")
        start = Link.Start(next)
        nOfTraversedTurns ++
    }

    fun givenHumanTurn(turn: Pos): Trans {
        logV("givenHumanTurn($turn):")
        logV("focus = $focus")
        logV("computing = $computing")
        logV("scheduledComputings = ${scheduledComputings.toPrettyString()}")
        logV("unknowns = ${unknowns.toPrettyString()}")
        require(focus is Link.FutureTurn.Human.OneOf) { "focus = $focus" }
        val focus = focus as Link.FutureTurn.Human.OneOf
        require(turn in focus.nexts.keys)
        val next = focus.nexts.getValue(turn)
        setFocus(next)
        coroutineScope.launch(Dispatchers.Default) {
            logIMilestoneScope("givenHumanTurn") {
                collapseComputations(focus, turn)
                milestoneEndOf("collapseComputations")
                discoverUnknowns()
                milestoneEndOf("discoverUnknowns")
            }
        }
        return next.trans
    }

    private fun collapseComputations(root: Link.FutureTurn.Human.OneOf, actualTurn: Pos) {
        logV("collapseComputations(\n$root,\nactualTurn = $actualTurn)\n")
        synchronized(Lock) {
            for ((turn, next) in root.nexts) {
                if (turn != actualTurn) {
                    next.stopComputations()
                } else { // actual turn branch
                    // rewrap next in saved NextAs (unknowns, scheduledComputings, computing) as child of start
                    when (val link = next.link) {
                        is Link.Unknown -> {
                            val nextAs = NextAs(next, link)
                            require(nextAs in unknowns)
                            unknowns.remove(nextAs)
                            unknowns.add(NextAs(start.next, link)) // shall be scheduled later
                        }
                        is Link.FutureTurn.Bot.ScheduledComputing -> {
                            val nextAs = NextAs(next, link)
                            require(nextAs in scheduledComputings)
                            scheduledComputings.remove(nextAs)
                            scheduledComputings.add(NextAs(start.next, link))
                        }
                        is Link.FutureTurn.Bot.Computing -> {
                            require(link == computing!!.link)
                            computing = NextAs(start.next, link)
                        }
                    }
                }
            }
            if (computing == null && scheduledComputings.isNotEmpty()) // when t'was stopped
                runNext()
        }
        logV("unknowns = ${unknowns.toPrettyString()}")
    }

    private fun Next.stopComputations() {
        val nextAs = NextAs(this, link)
        logIMilestoneScope("stopComputations") {
            when (val root = nextAs.link) {
                is Link.FutureTurn.Bot.ScheduledComputing -> {
                    + "scheduled"
                    scheduledComputings.remove(nextAs) // synchronized(ComputingLock) from collapseComputations
                    link = Link.Unknown(root.depth) // do not add to unknowns, obsolete branch
                    - "scheduled"
                }
                is Link.FutureTurn.Bot.Computing -> {
                    + "computing"
                    require(computing == nextAs)
                    computing = null // synchronized(ComputingLock) from collapseComputations
                    root.deferred.cancel()
                    link = Link.Unknown(root.depth)
                    - "computing"
                }
                is Link.Unknown -> {
                    + "unknown"
                    require(nextAs in unknowns) { "focus = $focus,\nunknowns = ${unknowns.toPrettyString()}" }
                    unknowns.remove(nextAs)
                    - "unknown"
                }
                is Link.FutureTurn.Human.OneOf ->
                    for ((_, next) in root.nexts)
                        next.stopComputations()
                is Link.FutureTurn.Bot.Computed ->
                    root.next.stopComputations()
            }
        }
    }

    fun requestBotTurnAsync(): Deferred<Pair<Pos, Trans>> {
        require(focus is Link.FutureTurn.Bot) { "focus = $focus" }
        logI("requestBotTurnAsync:")
        return when (val focus = focus as Link.FutureTurn.Bot) {
            is Link.FutureTurn.Bot.Computed -> {
                logV("computed to focus")
                setFocus(focus.next)
                return coroutineScope.async { sLogI("-> ${focus.pos}"); focus.pos to focus.next.trans }
            }
            is Link.FutureTurn.Bot.Computing -> coroutineScope.async {
                val (elapsedPretty, computed) = measureElapsedTimePretty {
                    focus.deferred.await()
                }
                logW("Slow bot ${players.getValue(computed.playerId)}: $elapsedPretty\n")
                logV("computing to focus")
                setFocus(computed.next)
                return@async computed.pos to computed.next.trans
            }
            else -> impossibleCaseOf(focus)
        }
    }

    /** Depth of [link] + [depth], `null` means infinite: all possibilities are computed */
    private fun computeDepth(link: Link = focus, depth: Int = nOfTraversedTurns): Int? =
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

    private fun computeWidth(root: Link = focus): Int =
        when (root) {
            is Link.FutureTurn.Bot.Computed -> computeWidth(root.next.link)
            is Link.FutureTurn.Human.OneOf -> root.nexts.values.sumBy { computeWidth(it.link) }
            is Link.Terminal -> 1
            else -> impossibleCaseOf(root)
        }

    private object Lock

    private class NextAsDepthComparator<L> : Comparator<NextAs<L>> where L : Link.WithDepth, L : Link {
        override fun compare(o1: NextAs<L>, o2: NextAs<L>): Int =
            o1.link.depth.compareTo(o2.link.depth)
    }

    companion object {
        private const val SOFT_MAX_WIDTH = 100
        private const val SOFT_MIN_DEPTH = 5
    }
}

private sealed class Link {
    interface Transient // has next or nexts
    interface Terminal // has no next
    interface TemporalTerminal : Terminal // may become Transient in future
    interface WithDepth { val depth: Int }

    class Start(
        val next: Next
    ) : Link(), Transient {
        override fun toString(indent: Int) =
            indentOf(indent) + "Start:\n" + next.toString(indent + 1)
    }

    object End : Link(), Terminal {
        override fun toString() =
            "End"
    }

    class Unknown(
        override val depth: Int
    ) : Link(), TemporalTerminal, WithDepth {
        override fun toString() =
            "Unknown(depth = $depth)"
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
                    indentOf(indent) + "Human.OneOf($playerId):" + nexts.entries.joinToString(
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
                    indentOf(indent) + "Bot.Computed($playerId, $pos):\n" + next.toString(indent + 1)
            }
            class Computing(
                playerId: PlayerId,
                override val depth: Int,
                val computation: Computation,
                val deferred: Deferred<Computed>
            ) : Bot(playerId), TemporalTerminal, WithDepth {
                override fun toString(): String =
                    "Bot.Computing($playerId, depth = $depth, $computation)"
            }
            class ScheduledComputing(
                playerId: PlayerId,
                override val depth: Int,
                val computation: Computation
            ) : Bot(playerId), TemporalTerminal, WithDepth {
                override fun toString(): String =
                    "Bot.ScheduledComputing($playerId, depth = $depth, $computation)"
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

private class NextAs<out L : Link>(
    val next: Next,
    newLink: L
) {
    /** Typed next.link */
    val link: L = newLink
    init {
        next.link = link
    }

    constructor(trans: Trans, link: L) : this(Next(trans, link), link)

    override fun equals(other: Any?) =
        other is NextAs<*> && next == other.next
    override fun hashCode() =
        next.hashCode()
    override fun toString() =
        next.toString()
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

private fun <E> Iterable<E>.toPrettyString(): String =
    joinToString(prefix = "[\n", separator = ",\n", postfix = "\n]")

