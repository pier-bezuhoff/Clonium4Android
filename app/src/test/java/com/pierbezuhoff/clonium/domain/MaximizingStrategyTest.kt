package com.pierbezuhoff.clonium.domain

import io.kotlintest.properties.assertAll
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.runBlocking
import kotlin.system.measureNanoTime

class MaximizingStrategyTest : FreeSpec() {
    init {
        "estimateTurn1" - {
            "measure different allVariations implementations" {
                val impls: MutableMap<String, Float> = setOf(
                    "d"
                ).associateWithTo(mutableMapOf()) { 0f }
                var timesTested = 0
                VeryPopulatedPrimitiveBoardGenerator(posRatio = 1.0, chipRatio = 0.8).assertAll(iterations = 100) { board: EvolvingBoard ->
                    val players = board.players()
                    val order = players.shuffled()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        println(board)
                        val playerId = order.first()
                        val turn = board.possOf(playerId).shuffled().first()
                        timesTested += 1
                        val estimate = { board: Board -> board.possOf(playerId).sumBy { board.levelAt(it)?.ordinal ?: 0 } }
                        with(MaximizingStrategy) {
                            impls.addElapsedTime("d") {
                                estimateTurn(turn, 1, estimate, playerId, order, board)
                            }
                        }
                    }
                }
                println("$timesTested times tested")
                for ((name, time) in impls)
                    println("$name: ${time/1_000_000/timesTested}ms per board")
            }
        }

        "MaximizerBot" - {
            "measure different Level Maximizer 1 implementations" {
                var timesTested = 0
                val impls: MutableMap<String, Float> = setOf(
                    "deferred-max"
                ).associateWithTo(mutableMapOf()) { 0f }
                VeryPopulatedPrimitiveBoardGenerator(posRatio = 0.95, chipRatio = 0.9).assertAll(iterations = 100) { board: EvolvingBoard ->
                    val players = board.players()
                    val order = players.shuffled()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        println(board)
                        val playerId = order.first()
                        val lmn1 = LevelMaximizerBot(playerId, depth = 1)
                        timesTested += 1
                        runBlocking {
                            with(lmn1) {
                                impls.addElapsedTime("deferred-max") {
                                    makeTurn(board, order)
                                }
                            }
                        }
                    }
                }
                println("$timesTested times tested")
                for ((name, time) in impls)
                    println("$name: ${time/1_000_000/timesTested}ms per board")
            }
        }
    }

    private inline fun <K> MutableMap<K, Float>.addElapsedTime(key: K, block: () -> Unit) {
        this[key] = this[key]!! + measureNanoTime(block)
    }
}
