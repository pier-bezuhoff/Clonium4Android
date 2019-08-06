package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.Milliseconds
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.withClue
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlin.system.measureNanoTime

class MaximizingStrategyTest : FreeSpec() {
    init {
        "allVariations" - {
            "measure different implementations" {
                val impls: MutableMap<String, Float> = setOf(
                    "a", // all turns, current
                    "d", // distinct
                    "s", // shifting <- the best here
                    "ds", // distinct + shifting
                    "dst" // distinct + shifting + tailrec
                ).associateWithTo(mutableMapOf()) { 0f }
                var timesTested = 0
                VeryPopulatedPrimitiveBoardGenerator(chipRatio = 0.5).assertAll(iterations = 1/*50*/) { board: EvolvingBoard ->
//                    println(board)
                    val players = board.players()
                    val order = players.shuffled()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        val playerId = order.first()
                        timesTested += 1
                        val nTurns = Gen.choose(1, 3).random().first()
//                        withClue("nTurns = $nTurns, order = $order") {}
                        with(MaximizingStrategy) {
                            var variations: Sequence<EvolvingBoard> = emptySequence()
                            var otherVariations: Sequence<EvolvingBoard> = emptySequence()
                            impls.addElapsedTime("a") {
                                variations = allVariations(nTurns, playerId, order, board)
                            }
                            impls.addElapsedTime("s") {
                                otherVariations = allVariations_shifting(nTurns, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                            impls.addElapsedTime("d") {
                                otherVariations = allVariations_distinct(nTurns, playerId, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                            impls.addElapsedTime("ds") {
                                otherVariations = allVariations_distinct_shifting(nTurns, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                            impls.addElapsedTime("dst") {
                                otherVariations = allVariations_tailrec(nTurns, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                        }
                    }
                }
                println("$timesTested times tested")
                for ((name, time) in impls)
                    println("$name: ${time/1_000_000/timesTested}ms per board")
            }
        }

        "estimateTurn1" - {
            "measure different allVariations implementations" {
                val impls: MutableMap<String, Float> = setOf(
                    "a", // current, very good
                    "a'", // very good
                    "a1", // best
                    "d",
                    "s", // very good
                    "ds",
                    "dst"
                ).associateWithTo(mutableMapOf()) { 0f }
                var timesTested = 0
                VeryPopulatedPrimitiveBoardGenerator(posRatio = 1.0, chipRatio = 0.8).assertAll(iterations = 1000) { board: EvolvingBoard ->
                    val players = board.players()
                    val order = players.shuffled()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        println(board)
                        val playerId = order.first()
                        val turn = board.possOf(playerId).shuffled().first()
                        timesTested += 1
                        val estimate = { board: Board -> board.possOf(playerId).sumBy { board.levelAt(it)?.ordinal ?: 0 } }
                        with(MaximizingStrategy) {
                            impls.addElapsedTime("a") {
                                estimateTurn1(turn, estimate, playerId, order, board)
                            }
                            impls.addElapsedTime("a1") {
                                estimateTurn(turn, 1, estimate, playerId, order, board)
                            }
                            impls.addElapsedTime("a'") {
                                metaEstimateTurn1_(
                                    { a,b,c,d -> allVariations(a,b,c,d)},
                                    turn, estimate, playerId, order, board
                                )
                            }
                            impls.addElapsedTime("d") {
                                metaEstimateTurn1_(
                                    { a,b,c,d -> allVariations_distinct(a,b,c,d)},
                                    turn, estimate, playerId, order, board
                                )
                            }
                            impls.addElapsedTime("s") {
                                metaEstimateTurn1(
                                    {a,b,c -> allVariations_shifting(a,b,c)},
                                    turn, estimate, order, board
                                )
                            }
                            impls.addElapsedTime("ds") {
                                metaEstimateTurn1(
                                    {a,b,c -> allVariations_distinct_shifting(a,b,c) },
                                    turn, estimate, order, board
                                )
                            }
                            impls.addElapsedTime("dst") {
                                metaEstimateTurn1(
                                    {a,b,c -> allVariations_tailrec(a,b,c) },
                                    turn, estimate, order, board
                                )
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
            TODO()
        }
    }

    private inline fun <K> MutableMap<K, Float>.addElapsedTime(key: K, block: () -> Unit) {
        this[key] = this[key]!! + measureNanoTime(block)
    }

    private fun <K> MutableMap<K, Milliseconds>.add(key: K, timeDelta: Milliseconds) {
        this[key] = this[key]!! + timeDelta
    }
}
