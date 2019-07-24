package com.pierbezuhoff.clonium.models.animation

import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
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
            "pack.duration is max" {
                val a1 = ConstAdvancer(1, 0L, false)
                val a2 = ConstAdvancer(2, 1_501L, true)
                val a3 = ConstAdvancer(4, 1_500L, false)
                val pack = AdvancerPack(listOf(a1, a2, a3))
                pack.duration shouldBe 1_501L
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
        }
        "Advancers" - {
            "example" - {
                val a1 = ConstAdvancer(1, 1_000L, true)
                val a2 = ConstAdvancer(2, 3_000L, false)
                val b3 = ConstAdvancer(3, 1_500L, true)
                val c4 = ConstAdvancer(4, 200L, true)
                val c5 = ConstAdvancer(5, 3_000L, false)
                "DSL" {
                    with(Advancers) {
                        // 2 * 2 = 4
                        val paa: AdvancerPack<Int> = a1 and a2
                        val ppa: AdvancerPack<Int> = paa and c4
                        val pap: AdvancerPack<Int> = a1 and paa
                        val ppp: AdvancerPack<Int> = ppa and pap
                        // 3 * 3 = 9
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
                    sequence.shouldAdvanceTo(1L, listOf(2, 4, 5), blocking = true, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(2, 5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(300L, listOf(2, 5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(1L, listOf(5), blocking = false, ended = false)
                    sequence.shouldAdvanceTo(2_500L, listOf(5), blocking = false, ended = true)
                }
            }
        }
    }
}