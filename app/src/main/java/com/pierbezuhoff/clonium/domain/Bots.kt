package com.pierbezuhoff.clonium.domain

import android.util.Rational
import com.pierbezuhoff.clonium.utils.AndroidLoggerOf
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.measureElapsedTimePretty
import kotlinx.coroutines.*
import kotlin.math.roundToInt

interface BotPlayer : Player {
    val difficultyName: String

    /** [BotPlayer] compute its turn on [board] given [order] of alive players */
    suspend fun makeTurn(board: Board, order: List<PlayerId>): Pos
}

class RandomPickerBot(override val playerId: PlayerId) : BotPlayer {
    override val difficultyName = "Random picker"
    override val tactic = PlayerTactic.Bot.RandomPicker

    override suspend fun makeTurn(board: Board, order: List<PlayerId>): Pos {
        val possibleTurns = board.possOf(playerId)
        return possibleTurns.random()
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
    ): Sequence<EvolvingBoard> { // TODO: filter order while making turns
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

    internal fun estimateTurn(
        turn: Pos,
        depth: Int,
        estimate: (Board) -> Int,
        playerId: PlayerId,
        /** should contain only alive players */
        order: List<PlayerId>,
        board: EvolvingBoard
    ): Int {
        if (depth == 0)
            return estimate(board)
        // MAYBE: filter order on alive players
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
            board.distinctTurnsOf(playerId).map { turn ->
                estimateTurn(
                    turn, depth - 1, estimate,
                    playerId, order, variation // TODO: change order if a player dies!
                )
            }.max() ?: Int.MIN_VALUE // no turns means death
        }.min() ?: Int.MAX_VALUE
    }
}

private typealias Estimator = (Board) -> Int

// MAYBE: optimize for multi-core (several threads)
abstract class MaximizerBot(
    override val playerId: PlayerId,
    private val estimator: Estimator,
    protected val depth: Int
): Any()
    , BotPlayer
    , Logger by AndroidLoggerOf<MaximizerBot>(minLogLevel = Logger.Level.INFO)
{

    suspend fun makeTurnTimed(
        board: Board, order: List<PlayerId>
    ): Pos {
        val (pretty, bestTurn) = measureElapsedTimePretty {
            makeTurn(board, order)
        }
        sLogI("$difficultyName thought $pretty")
        return bestTurn
    }

    override suspend fun makeTurn(board: Board, order: List<PlayerId>): Pos =

        withContext(Dispatchers.Default) {
            val distinctTurns = board.distinctTurnsOf(playerId)
            require(distinctTurns.isNotEmpty()) { "Bot $this should be alive on board $board" }
            if (distinctTurns.size == 1)
                return@withContext distinctTurns.first()
            val evolvingBoard = PrimitiveBoard(board)
            val estimations: MutableList<Pair<Pos, Deferred<Int>>> = mutableListOf()
            for (turn in distinctTurns) {
                estimations.add(
                    turn to async {
                        MaximizingStrategy.estimateTurn(
                            turn,
                            depth, estimator,
                            playerId, order, evolvingBoard
                        )
                    }
                )
            }
            return@withContext estimations.maxBy { it.second.await() }!!.first
        }

    override fun toString(): String =
        "MaximizerBot($playerId, $difficultyName)"
}

class LevelMaximizerBot(
    playerId: PlayerId,
    depth: Int
) : MaximizerBot(
    playerId,
    estimator = { board ->
        board.levelOf(playerId)
    },
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
    estimator = { board -> board.chipCountOf(playerId) },
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
    estimator = { board -> board.asInhabitedPosMap()
        .values
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
    private val enemyMinimization: Double
) : MaximizerBot(
    playerId,
    estimator = e@ { board ->
        val enemiesLevel = board.asInhabitedPosMap()
            .values
            .filter { it.playerId != playerId }
            .sumBy { it.level.ordinal }
        val botLevel = board.levelOf(playerId)
        return@e (botLevel - enemyMinimization * enemiesLevel).roundToInt()
    },
    depth = depth
) {
    override val difficultyName: String = "Level balancer $depth (1:$enemyMinimization)"
    override val tactic = PlayerTactic.Bot.LevelBalancer(depth, enemyMinimization)
}

class AlliedLevelBalancerBot(
    playerId: PlayerId,
    depth: Int,
    private val allyId: PlayerId,
    private val allyMaximization: Double,
    private val enemyMinimization: Double
) : MaximizerBot(
    playerId,
    estimator = e@ { board ->
        val botLevel = board.levelOf(playerId)
        val allyLevel = board.levelOf(allyId)
        val enemiesLevel = board.asInhabitedPosMap()
            .values
            .filter { it.playerId != playerId && it.playerId != allyId }
            .sumBy { it.level.ordinal }
        return@e (botLevel + allyMaximization * allyLevel - enemyMinimization * enemiesLevel).roundToInt()
    },
    depth = depth
) {
    override val difficultyName = "Allied level balancer $depth of $allyId 1:$allyMaximization:$enemyMinimization"
    override val tactic = PlayerTactic.Bot.AlliedLevelBalancer(depth, allyId, allyMaximization, enemyMinimization)
}
