package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.AndroidLogger
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.measureElapsedTimePretty
import kotlinx.coroutines.*

interface BotPlayer : Player {
    val difficultyName: String

    fun CoroutineScope.makeTurnAsync(
        board: Board,
        order: List<PlayerId>
    ): Deferred<Pos>
}

class RandomPickerBot(override val playerId: PlayerId) : BotPlayer {
    override val difficultyName = "Random picker"
    override val tactic = PlayerTactic.Bot.RandomPicker

    override fun CoroutineScope.makeTurnAsync(
        board: Board,
        order: List<PlayerId>
    ): Deferred<Pos> = async {
        val possibleTurns = board.possOf(playerId)
        return@async possibleTurns.random()
    }

    override fun toString(): String =
        "RandomPickerBot($playerId)"
}

object MaximizingStrategy {

    fun nextPlayerId(
        playerId: PlayerId, order: List<PlayerId>,
        board: Board
    ): PlayerId? {
        val ix = order.indexOf(playerId)
        return (order.drop(ix + 1) + order.take(ix)).firstOrNull { board.isAlive(it) }
    }

    /** All possible [EvolvingBoard]s after [nTurns] turns starting from [playerId] according to [order] */
    fun allVariations(
        nTurns: Int,
        playerId: PlayerId?, order: List<PlayerId>,
        board: EvolvingBoard
    ): Sequence<EvolvingBoard> {
        if (playerId == null || nTurns == 0)
            return sequenceOf(board)
        val possibleTurns = board.possOf(playerId)
        if (possibleTurns.isEmpty())
            return sequenceOf(board)
        val nextPlayerId = nextPlayerId(playerId, order, board)
        return possibleTurns.asSequence()
            .flatMap { turn ->
                allVariations(
                    nTurns - 1,
                    nextPlayerId, order,
                    board.copy().apply { inc(turn) }
                )
            }
    }

    // MAYBE: try to use sequence { ... } builder
    internal fun allVariations_distinct(
        nTurns: Int,
        playerId: PlayerId?, order: List<PlayerId>,
        board: EvolvingBoard
    ): Sequence<EvolvingBoard> {
        if (playerId == null || nTurns == 0)
            return sequenceOf(board)
        val distinctTurns = board.distinctTurns(playerId)
        if (distinctTurns.isEmpty())
            return sequenceOf(board)
        val nextPlayerId = nextPlayerId(playerId, order, board)
        return distinctTurns.asSequence()
            .flatMap { turn ->
                allVariations_distinct(
                    nTurns - 1,
                    nextPlayerId, order,
                    board.copy().apply { inc(turn) }
                )
            }
    }

    internal fun allVariations_shifting(nTurns: Int, order: List<PlayerId>, board: EvolvingBoard): Sequence<EvolvingBoard> {
        if (nTurns == 0 || order.isEmpty()) {
            return sequenceOf(board)
        } else {
            val playerId = order.first()
            val possibleTurns = board.possOf(playerId)
            if (possibleTurns.isEmpty())
                return sequenceOf(board)
            if (nTurns == 1)
                return possibleTurns.asSequence().map {
                    val nextBoard = board.copy()
                    nextBoard.inc(it)
                    nextBoard
                }
            return possibleTurns.asSequence()
                .flatMap { turn ->
                    val nextBoard = board.copy()
                    nextBoard.inc(turn)
                    allVariations_shifting(nTurns - 1, nextBoard.shiftOrder(order), nextBoard)
                }
        }
    }

    internal fun allVariations_distinct_shifting(nTurns: Int, order: List<PlayerId>, board: EvolvingBoard): Sequence<EvolvingBoard> {
        if (nTurns == 0 || order.isEmpty()) {
            return sequenceOf(board)
        } else {
            val playerId = order.first()
            val distinctTurns = board.distinctTurns(playerId)
            if (distinctTurns.isEmpty())
                return sequenceOf(board)
            return distinctTurns.asSequence()
                .flatMap { turn ->
                    val nextBoard = board.copy()
                    nextBoard.inc(turn)
                    allVariations_distinct_shifting(nTurns - 1, nextBoard.shiftOrder(order), nextBoard)
                }
        }
    }

    internal fun allVariations_tailrec(
        nTurns: Int, order: List<PlayerId>, board: EvolvingBoard
    ): Sequence<EvolvingBoard> =
        _allVariations_tailrec(sequenceOf(Triple(nTurns, order, board)), emptySequence())

    private tailrec fun _allVariations_tailrec(
        args: Sequence<Triple<Int, List<PlayerId>, EvolvingBoard>>,
        result: Sequence<EvolvingBoard>
    ): Sequence<EvolvingBoard> {
        val arg = args.firstOrNull()
        if (arg == null) {
            return result
        } else {
            val (nTurns, order, board) = arg
            val restArgs = args.drop(1)
            if (nTurns == 0)
                return _allVariations_tailrec(restArgs, result + sequenceOf(board))
            if (order.isEmpty())
                return _allVariations_tailrec(restArgs, result)
            if (order.size == 1)
                return _allVariations_tailrec(restArgs, result + sequenceOf(board))
            val playerId = order.first()
            val distinctTurns = board.distinctTurns(playerId) // non-empty
            if (nTurns == 1)
                return _allVariations_tailrec(restArgs, result + distinctTurns.asSequence().map { board.afterInc(it) })
            val newNTurns = nTurns - 1
            val newArgs = distinctTurns.asSequence()
                .map {
                    val newBoard = board.copy()
                    newBoard.inc(it)
                    Triple(newNTurns, newBoard.shiftOrder(order), newBoard)
                }
            return _allVariations_tailrec(restArgs + newArgs, result)
        }
    }

    // MAYBE: inline recursion somehow ([depth] is decreasing)
    fun estimateTurn(
        turn: Pos,
        depth: Int,
        estimate: (Board) -> Int,
        playerId: PlayerId,
        order: List<PlayerId>,
        board: EvolvingBoard
    ): Int {
        if (depth == 0)
            return estimate(board)
        val resultingBoard = board.copy().apply { inc(turn) }
        // NOTE: if a player dies in this round we just analyze further development
        val variations = allVariations(
            order.size - 1, // all variations after 1 round
            nextPlayerId(playerId, order, board), order,
            resultingBoard
        )
        if (depth == 1) {
            val worstCase = variations.minBy(estimate)
            return worstCase?.let(estimate) ?: Int.MAX_VALUE
        }
        return variations.map { variation ->
            board.distinctTurns(playerId).map { turn ->
                estimateTurn(
                    turn, depth - 1, estimate,
                    playerId, order, variation
                )
            }.max() ?: Int.MIN_VALUE // no turns means death
        }.min() ?: Int.MAX_VALUE
    }

    inline fun estimateTurn1(
        turn: Pos,
        estimate: (Board) -> Int,
        playerId: PlayerId,
        order: List<PlayerId>,
        board: EvolvingBoard
    ): Int {
        val resultingBoard = board.copy().apply { inc(turn) }
        // NOTE: if a player dies in this round we just analyze further development
        val variations = allVariations(
            order.size - 1, // all variations after 1 round
            nextPlayerId(playerId, order, board), order,
            resultingBoard
        )
        val worstCase = variations.minBy(estimate)
        return worstCase?.let(estimate) ?: Int.MAX_VALUE
    }

    internal inline fun metaEstimateTurn1_(
        crossinline allVariations: (Int, PlayerId?, List<PlayerId>, EvolvingBoard) -> Sequence<EvolvingBoard>,
        turn: Pos,
        estimate: (Board) -> Int,
        playerId: PlayerId,
        order: List<PlayerId>,
        board: EvolvingBoard
    ): Int {
        val resultingBoard = board.copy().apply { inc(turn) }
        val variations = allVariations(
            order.size - 1, // all variations after 1 round
            playerId,
            order,
            resultingBoard
        )
        val worstCase = variations.minBy(estimate)
        return worstCase?.let(estimate) ?: Int.MAX_VALUE
    }

    internal inline fun metaEstimateTurn1(
        crossinline allVariations: (Int, List<PlayerId>, EvolvingBoard) -> Sequence<EvolvingBoard>,
        turn: Pos,
        estimate: (Board) -> Int,
        order: List<PlayerId>,
        board: EvolvingBoard
    ): Int {
        val resultingBoard = board.copy().apply { inc(turn) }
        val variations = allVariations(
            order.size - 1, // all variations after 1 round
            order,
            resultingBoard
        )
        val worstCase = variations.minBy(estimate)
        return worstCase?.let(estimate) ?: Int.MAX_VALUE
    }
}

typealias Estimator = (Board) -> Int
abstract class MaximizerBot(
    override val playerId: PlayerId,
    internal val estimate: Estimator,
    val depth: Int
): Any()
    , BotPlayer
    , Logger by AndroidLogger("MaximizerBot", minLogLevel = Logger.Level.WARNING)
{

    override fun CoroutineScope.makeTurnAsync(
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val possibleTurns = board.possOf(playerId)
            require(possibleTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (possibleTurns.size == 1)
                return@async possibleTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val (prettyElapsed, bestTurn) = measureElapsedTimePretty {
                possibleTurns
                    .maxBy { turn ->
                        MaximizingStrategy.estimateTurn(
                            turn, depth, estimate,
                            playerId, order, evolvingBoard
                        )
                    }!!
            }
            sLogI("$difficultyName thought $prettyElapsed")
            return@async bestTurn
        }
    }

    internal fun CoroutineScope.makeTurnAsync_distinct(
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val distinctTurns = board.distinctTurns(playerId)
            require(distinctTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (distinctTurns.size == 1)
                return@async distinctTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val (prettyElapsed, bestTurn) = measureElapsedTimePretty {
                distinctTurns
                    .maxBy { turn ->
                        MaximizingStrategy.estimateTurn(
                            turn, depth, estimate,
                            playerId, order, evolvingBoard
                        )
                    }!!
            }
            sLogI("$difficultyName thought $prettyElapsed")
            return@async bestTurn
        }
    }

    internal inline fun CoroutineScope.metaMakeTurnAsync(
        crossinline estimateTurn: (Pos, Int, Estimator, List<PlayerId>, EvolvingBoard) -> Int,
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val possibleTurns = board.possOf(playerId)
            require(possibleTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (possibleTurns.size == 1)
                return@async possibleTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val (prettyElapsed, bestTurn) = measureElapsedTimePretty {
                possibleTurns
                    .maxBy { turn ->
                        estimateTurn(
                            turn, depth, estimate,
                            order, evolvingBoard
                        )
                    }!!
            }
            sLogI("$difficultyName thought $prettyElapsed")
            return@async bestTurn
        }
    }

    internal inline fun CoroutineScope.metaMakeTurnAsync_distinct(
        crossinline estimateTurn: (Pos, Int, Estimator, List<PlayerId>, EvolvingBoard) -> Int,
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val distinctTurns = board.distinctTurns(playerId)
            require(distinctTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (distinctTurns.size == 1)
                return@async distinctTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val (prettyElapsed, bestTurn) = measureElapsedTimePretty {
                distinctTurns
                    .maxBy { turn ->
                        estimateTurn(
                            turn, depth, estimate,
                            order, evolvingBoard
                        )
                    }!!
            }
            sLogI("$difficultyName thought $prettyElapsed")
            return@async bestTurn
        }
    }

    internal inline fun CoroutineScope.metaMakeTurnAsync_(
        crossinline estimateTurn: (Pos, Int, Estimator, PlayerId, List<PlayerId>, EvolvingBoard) -> Int,
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val possibleTurns = board.possOf(playerId)
            require(possibleTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (possibleTurns.size == 1)
                return@async possibleTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val (prettyElapsed, bestTurn) = measureElapsedTimePretty {
                possibleTurns
                    .maxBy { turn ->
                        estimateTurn(
                            turn, depth, estimate,
                            playerId, order, evolvingBoard
                        )
                    }!!
            }
            sLogI("$difficultyName thought $prettyElapsed")
            return@async bestTurn
        }
    }

    internal inline fun CoroutineScope.metaMakeTurnAsync_distinct_(
        crossinline estimateTurn: (Pos, Int, Estimator, PlayerId, List<PlayerId>, EvolvingBoard) -> Int,
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val possibleTurns = board.distinctTurns(playerId)
            require(possibleTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (possibleTurns.size == 1)
                return@async possibleTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val (prettyElapsed, bestTurn) = measureElapsedTimePretty {
                possibleTurns
                    .maxBy { turn ->
                        estimateTurn(
                            turn, depth, estimate,
                            playerId, order, evolvingBoard
                        )
                    }!!
            }
            sLogI("$difficultyName thought $prettyElapsed")
            return@async bestTurn
        }
    }

    override fun toString(): String =
        "MaximizerBot($playerId, $difficultyName)"
}

class LevelMaximizerBot(
    playerId: PlayerId,
    depth: Int
) : MaximizerBot(
    playerId,
    estimate = { board -> board.possOf(playerId).sumBy { board.levelAt(it)?.ordinal ?: 0 } },
    depth = depth
) {
    override val difficultyName: String = "Level maximizer $depth"
    override val tactic = PlayerTactic.Bot.LevelMaximizer(depth)
}

internal class LevelMaximizer1Bot(playerId: PlayerId) : MaximizerBot(
    playerId,
    estimate = { board -> board.possOf(playerId).sumBy { board.levelAt(it)?.ordinal ?: 0 } },
    depth = 1
) {
    override val difficultyName: String = "Level maximizer 1"
    override val tactic = PlayerTactic.Bot.LevelMaximizer(depth = 1)
}

class ChipCountMaximizerBot(
    playerId: PlayerId,
    depth: Int
) : MaximizerBot(
    playerId,
    estimate = { board -> board.possOf(playerId).size },
    depth = depth
) {
    override val difficultyName: String = "Chip count maximizer $depth"
    override val tactic = PlayerTactic.Bot.ChipCountMaximizer(depth)
}

class LevelMinimizerBot(
    playerId: PlayerId,
    depth: Int
) : MaximizerBot(
    playerId,
    estimate = { board -> board.asPosMap()
        .values
        .filterNotNull()
        .filter { it.playerId != playerId }
        .sumBy { -it.level.ordinal }
    },
    depth = depth
) {
    override val difficultyName: String = "Enemy level minimizer $depth"
    override val tactic = PlayerTactic.Bot.LevelMinimizer(depth)
}

class LevelBalancerBot(
    playerId: PlayerId,
    depth: Int,
    /** Priority of own chips over enemies' */
    private val ratio: Float
) : MaximizerBot(
    playerId,
    estimate = { board ->
        (board.asPosMap()
            .values
            .filterNotNull()
            .filter { it.playerId != playerId }
            .sumBy { -it.level.ordinal }
                +
                ratio * board.possOf(playerId)
            .sumBy { board.levelAt(it)!!.ordinal })
            .toInt()
    },
    depth = depth
) {
    override val difficultyName: String = "Level balancer $depth ($ratio:1)"
    override val tactic = PlayerTactic.Bot.LevelBalancer(depth, ratio)
}
