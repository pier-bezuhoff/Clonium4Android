package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.Milliseconds
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.withClue
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import java.time.temporal.ValueRange
import kotlin.reflect.jvm.internal.impl.descriptors.VariableAccessorDescriptor
import kotlin.system.measureTimeMillis

class MaximizingStrategyTest : FreeSpec() {
    init {
        "allVariations" - {
            "measure different implementations" {
                val impls: MutableMap<String, Milliseconds> = setOf(
                    "a", // all turns, current
                    "d", // distinct
                    "ds", // distinct + shifting
                    "dst" // distinct + shifting + tailrec
                ).associateWithTo(mutableMapOf()) { 0L }
                PopulatedPrimitiveBoardGenerator().assertAll(iterations = 100_000) { board: EvolvingBoard ->
                    val players = board.players()
                    val order = players.shuffled()
                    val playerId = order.first()
                    if (minOf(board.width, board.height) >= 4 && order.isNotEmpty()) {
                        val nTurns = Gen.choose(1, 4).random().first()
                        withClue("nTurns = $nTurns, order = $order") {}
                        with(MaximizingStrategy) {
                            var variations: Sequence<EvolvingBoard> = emptySequence()
                            var otherVariations: Sequence<EvolvingBoard> = emptySequence()
                            impls.addElapsedTime("a") {
                                variations = allVariations(nTurns, playerId, order, board)
                            }
                            impls.addElapsedTime("d") {
                                otherVariations = allVariations_distinct(nTurns, playerId, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                            impls.addElapsedTime("ds") {
                                otherVariations = allVariations_shifting(nTurns, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                            impls.addElapsedTime("dst") {
                                otherVariations = allVariations_tailrec(nTurns, order, board)
                            }
//                            variations.map { it.toString() }.toSet() shouldContainExactlyInAnyOrder otherVariations.map { it.toString() }.toSet()
                        }
                    }
                }
                for ((name, time) in impls)
                    println("$name: ${time}ms")
            }
        }
    }

    private inline fun <K> MutableMap<K, Milliseconds>.addElapsedTime(key: K, block: () -> Unit) {
        this[key] = this[key]!! + measureTimeMillis(block)
    }

    private fun <K> MutableMap<K, Milliseconds>.add(key: K, timeDelta: Milliseconds) {
        this[key] = this[key]!! + timeDelta
    }
}
