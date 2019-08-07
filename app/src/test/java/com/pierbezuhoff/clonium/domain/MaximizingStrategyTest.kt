package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.Milliseconds
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.withClue
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
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
            "measure different Level Maximizer 1 implementations" {
                val impls: MutableMap<String, Float> = setOf(
                    "lmn=1", // current, LevelMaximizerBot(depth = 1)
                    "lmn=1d", // only bot's distinct turns
                    "lmn=1,s",
                    "lmn=1,ds", // bad
                    "lmn=1,dst", // very bad
                    "lm1", // LevelMinimizer1Bot(), better than lmn=1*
                    "lm1d",
                    "lm1,s",
                    "lm1,d",
                    "lm1,ds", // bad
                    "lm1,dst", // very bad
                    "lm1d,s", // best
                    "lm1d,d",
                    "lm1d,ds", // bad
                    "lm1d,dst" // very bad
                ).associateWithTo(mutableMapOf()) { 0f }
                var timesTested = 0
                VeryPopulatedPrimitiveBoardGenerator(posRatio = 0.95, chipRatio = 0.9).assertAll(iterations = 100) { board: EvolvingBoard ->
                    val players = board.players()
                    val order = players.shuffled()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        println(board)
                        val playerId = order.first()
                        val lmn1 = LevelMaximizerBot(playerId, 1)
                        val lm1 = LevelMaximizer1Bot(playerId)
                        timesTested += 1
                        runBlocking {
                            with(MaximizingStrategy) {
                                with(lmn1) {
//                                    impls.addElapsedTime("lmn=1") {
//                                        makeTurnAsync(board, order).await()
//                                    }
//                                    impls.addElapsedTime("lmn=1d") {
//                                        makeTurnAsync_distinct(board, order).await()
//                                    }
//                                    impls.addElapsedTime("lmn=1,s") {
//                                        metaMakeTurnAsync(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e->allVariations_shifting(d,o,e)},t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lmn=1,ds") {
//                                        metaMakeTurnAsync(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e-> allVariations_distinct_shifting(d,o,e) },t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lmn=1,dst") {
//                                        metaMakeTurnAsync(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e-> allVariations_tailrec(d,o,e) },t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
                                }
                                with(lm1) {
                                    impls.addElapsedTime("lm1") {
                                        makeTurnAsync(board, order).await()
                                    }
                                    impls.addElapsedTime("lm1d") {
                                        makeTurnAsync_distinct(board, order).await()
                                    }
//                                    impls.addElapsedTime("lm1,s") {
//                                        metaMakeTurnAsync(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e->allVariations_shifting(d,o,e)},t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lm1,d") {
//                                        metaMakeTurnAsync_(
//                                            {t,d,e,p,o,b->metaEstimateTurn1_({d,p,o,e-> allVariations_distinct(d,p,o,e) },t,e,p,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lm1,ds") {
//                                        metaMakeTurnAsync(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e-> allVariations_distinct_shifting(d,o,e) },t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lm1,dst") {
//                                        metaMakeTurnAsync(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e-> allVariations_tailrec(d,o,e) },t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
                                    impls.addElapsedTime("lm1d,s") {
                                        metaMakeTurnAsync_distinct(
                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e->allVariations_shifting(d,o,e)},t,e,o,b)},
                                            board, order
                                        ).await()
                                    }
//                                    impls.addElapsedTime("lm1d,d") {
//                                        metaMakeTurnAsync_distinct_(
//                                            {t,d,e,p,o,b->metaEstimateTurn1_({d,p,o,e-> allVariations_distinct(d,p,o,e) },t,e,p,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lm1d,ds") {
//                                        metaMakeTurnAsync_distinct(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e-> allVariations_distinct_shifting(d,o,e) },t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
//                                    impls.addElapsedTime("lm1d,dst") {
//                                        metaMakeTurnAsync_distinct(
//                                            {t,d,e,o,b->metaEstimateTurn1({d,o,e-> allVariations_tailrec(d,o,e) },t,e,o,b)},
//                                            board, order
//                                        ).await()
//                                    }
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

    private fun <K> MutableMap<K, Milliseconds>.add(key: K, timeDelta: Milliseconds) {
        this[key] = this[key]!! + timeDelta
    }
}
