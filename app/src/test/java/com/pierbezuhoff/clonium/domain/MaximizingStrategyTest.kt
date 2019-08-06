package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.Milliseconds
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
                    "s", // shifting
                    "ds", // distinct + shifting
                    "dst" // distinct + shifting + tailrec
                ).associateWithTo(mutableMapOf()) { 0f }
                var timesTested = 0
                VeryPopulatedPrimitiveBoardGenerator().assertAll(iterations = 10_000) { board: EvolvingBoard ->
                    val players = board.players()
                    val order = players.shuffled()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        val playerId = order.first()
                        timesTested += 1
                        val nTurns = Gen.choose(1, 4).random().first()
                        withClue("nTurns = $nTurns, order = $order") {}
                        with(MaximizingStrategy) {
                            var variations: Sequence<EvolvingBoard> = emptySequence()
                            var otherVariations: Sequence<EvolvingBoard> = emptySequence()
                            impls.addElapsedTime("a") {
                                variations = allVariations(nTurns, playerId, order, board)
                            }
                            impls.addElapsedTime("s") {
                                otherVariations = allVariations_shifting(nTurns, order, board)
                            }
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
                    println("$name: ${time/1_000/timesTested}mcs per board")
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
