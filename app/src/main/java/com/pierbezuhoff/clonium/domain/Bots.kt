package com.pierbezuhoff.clonium.domain

import android.util.Log
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

    private fun nextPlayerId(
        playerId: PlayerId, order: List<PlayerId>,
        board: Board
    ): PlayerId? {
        val ix = order.indexOf(playerId)
        return (order.drop(ix + 1) + order.take(ix)).firstOrNull { board.isAlive(it) }
    }

    /** All possible [EvolvingBoard]s after [nTurns] turns starting from [playerId] according to [order] */
    private fun allVariations(
        nTurns: Int,
        playerId: PlayerId?, order: List<PlayerId>,
        board: EvolvingBoard
    ) : Sequence<EvolvingBoard> {
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
                    board.copy().apply { inc(turn) } // FIX: cause InvalidTurn
                )
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
}

abstract class MaximizerBot(
    override val playerId: PlayerId,
    private val estimate: (Board) -> Int,
    private val depth: Int
): BotPlayer {

    override fun CoroutineScope.makeTurnAsync(
        board: Board, order: List<PlayerId>
    ): Deferred<Pos> {
        return async(Dispatchers.Default) {
            val possibleTurns = board.distinctTurns(playerId)
            require(possibleTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (possibleTurns.size == 1)
                return@async possibleTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val startTime = System.currentTimeMillis()
            val bestTurn = possibleTurns
                .maxBy { turn ->
                    MaximizingStrategy.estimateTurn(
                        turn, depth, estimate,
                        playerId, order, evolvingBoard
                    )
                }!!
            val elapsedTime = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                Log.i(difficultyName, "thought $elapsedTime ms")
            }
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
