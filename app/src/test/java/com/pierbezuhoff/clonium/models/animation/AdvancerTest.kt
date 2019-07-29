package com.pierbezuhoff.clonium.models.animation

import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.withClue
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.specs.FreeSpec
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow

class AdvancerTest : FreeSpec() {
    init {
        "EmptyAdvancer" - {
            "Can (Not) Advance" {
                shouldThrow<IllegalStateException> { EmptyAdvancer.advance(0L) }
            }
        }
        "AdvancerPack" - {
            "pack with EmptyAdvancer cannot advance" {
                Gen.list(BooleanAdvancerGenerator()).assertAll { advancers ->
                    val list = (advancers + EmptyAdvancer).shuffled()
                    val pack = AdvancerPack(list)
                    shouldThrow<IllegalStateException> { pack.advance(0L) }
                }
            }
            "example" {
                val a1 = ConstAdvancer(1, 0L, false)
                val a2 = ConstAdvancer(2, 1_501L, true)
                val a3 = ConstAdvancer(3, 1_600L, false)
                val pack = AdvancerPack(listOf(a1, a2, a3))
                pack.shouldAdvanceTo(0L, listOf(1, 2, 3), blocking = true, ended = false)
                pack.shouldAdvanceTo(0L, listOf(2, 3), blocking = true, ended = false)
                pack.shouldAdvanceTo(1_501L, listOf(2, 3), blocking = false, ended = false)
                pack.shouldAdvanceTo(0L, listOf(3), blocking = false, ended = false)
                pack.shouldAdvanceTo(99L, listOf(3), blocking = false, ended = true)
                pack.shouldAdvanceTo(0L, listOf(), blocking = false, ended = true)
            }
            "all in pack advance" {
                Gen.list(BooleanAdvancerGenerator()).assertAll { advancers ->
                    val pack = AdvancerPack(advancers)
                    val dt = 239L
                    pack.advance(dt)
                    advancers.forEach {
                        it.progress shouldBeGreaterThan 0.0
                    }
                }
            }
            "pack is parallel" {
                Gen.list(ConstAdvancerGenerator((1..1000).toList())).assertAll { advancers ->
                    val results = advancers.associateWith { it.advance(0L) }
                    val activeAdvancers = advancers.toMutableList()
                    fun activeResults(): List<Int> =
                        activeAdvancers.map { results.getValue(it) }
                    val pack = AdvancerPack(advancers)
                    pack.advance(0L) shouldContainExactly activeResults()
                    var elapsed: Milliseconds = 0L
                    val durations = advancers.groupBy { it.duration }
                    val groups = durations.entries
                        .sortedBy { it.value.first().duration }
                        .map { it.value }
                    for (group in groups) {
                        pack.advance(group.first().duration - elapsed) shouldContainExactly activeResults()
                        elapsed = group.first().duration
                        activeAdvancers.removeAll(group)
                        withClue("pack = $pack\n") {
                            pack.advance(0L) shouldContainExactly activeResults()
                        }
                    }
                    pack.blocking shouldBe false
                    pack.ended shouldBe true
                }
            }
        }
        "AdvancerSequence" - {
            "example" {
                val am1 = ConstAdvancer(-1, 400L, blocking = false)
                val a0 = ConstAdvancer(0, 500L, blocking = false)
                val a1 = ConstAdvancer(1, 500L, blocking = true)
                val a2 = ConstAdvancer(2, 400L, blocking = false)
                val a3 = ConstAdvancer(3, 300L, blocking = true)
                val a4 = ConstAdvancer(4, 50L, blocking = false)
                val a5 = ConstAdvancer(5, 80L, blocking = false)
                val a6 = ConstAdvancer(6, 90L, blocking = true)
                val a7 = ConstAdvancer(7, 210L, blocking = false)
                val a8 = ConstAdvancer(8, 110L, blocking = true)
                // diagram ('|' means "end", '.' means "continue"):
                //  1 . . | 3 . | 6 . . . | 8 . . |    // <- blocking branch
                // -1 . |   2 . . . . . . . . . |      // <- non-blocking branch
                //  0 . . |       4 . |     7 . . . |  // <- non-blocking branch
                //                5 . . |              // <- non-blocking branch
                val sequence = AdvancerSequence(
                    listOf(am1, a0, a1, a2, a3, a4, a5, a6, a7, a8).map { AdvancerPack(it) }
                )
                sequence.shouldAdvanceTo(0L, listOf(-1, 0, 1), blocking = true, ended = false)
                sequence.shouldAdvanceTo(400L, listOf(-1, 0, 1), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(0, 1), blocking = true, ended = false)
                sequence.shouldAdvanceTo(100L, listOf(0, 1), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(2, 3), blocking = true, ended = false)
                sequence.shouldAdvanceTo(300L, listOf(2, 3), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(2, 4, 5, 6), blocking = true, ended = false)
                sequence.shouldAdvanceTo(50L, listOf(2, 4, 5, 6), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(2, 5, 6), blocking = true, ended = false)
                sequence.shouldAdvanceTo(30L, listOf(2, 5, 6), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(2, 6), blocking = true, ended = false)
                sequence.shouldAdvanceTo(10L, listOf(2, 6), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(2, 7, 8), blocking = true, ended = false)
                sequence.shouldAdvanceTo(10L, listOf(2, 7, 8), blocking = true, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(7, 8), blocking = true, ended = false)
                sequence.shouldAdvanceTo(100L, listOf(7, 8), blocking = false, ended = false)
                sequence.shouldAdvanceTo(0L, listOf(7), blocking = false, ended = false)
                sequence.shouldAdvanceTo(100L, listOf(7), blocking = false, ended = true)
                sequence.shouldAdvanceTo(0L, listOf(), blocking = false, ended = true)
            }
        }
        "Advancers" - {
            "example" - {
                val a1 = ConstAdvancer(1, 1_000L, true)
                val a2 = ConstAdvancer(2, 3_000L, false)
                val b3 = ConstAdvancer(3, 1_500L, true)
                val c4 = ConstAdvancer(4, 200L, true)
                val c5 = ConstAdvancer(5, 3_000L, false)
                "DSL type check" {
                    with(Advancers) {
                        // 2 ^ 2 = 4
                        val paa: AdvancerPack<Int> = a1 and a2
                        val ppa: AdvancerPack<Int> = paa and c4
                        val pap: AdvancerPack<Int> = a1 and paa
                        val ppp: AdvancerPack<Int> = ppa and pap
                        // 3 ^ 2 = 9
                        val saa: AdvancerSequence<Int> = a1 then a2
                        val sap: AdvancerSequence<Int> = a1 then paa
                        val spa: AdvancerSequence<Int> = paa then b3
                        val spp: AdvancerSequence<Int> = paa then ppa
                        val ssa: AdvancerSequence<Int> = saa then a1
                        val sas: AdvancerSequence<Int> = a1 then saa
                        val ssp: AdvancerSequence<Int> = saa then paa
                        val sps: AdvancerSequence<Int> = paa then saa
                        val sss: AdvancerSequence<Int> = sap then spa
                    }
                }
                val sequence: AdvancerSequence<Int> = with(Advancers) {
                    (a1 and a2) then b3 sThenP (c4 and c5)
                }
                "ultimate traverse" {
                    sequence.blocking shouldBe true
                    sequence.ended shouldBe false
                    sequence.shouldAdvanceTo(1L, listOf(1, 2), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(1_000L, listOf(1, 2), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(2, 3), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(1_000L, listOf(2, 3), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(500L, listOf(2, 3), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(2, 4, 5), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(198L, listOf(2, 4, 5), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(2, 4, 5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(2, 5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(300L, listOf(2, 5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(2_500L, listOf(5), blocking = false, ended = true)
                }
            }
        }
    }
}