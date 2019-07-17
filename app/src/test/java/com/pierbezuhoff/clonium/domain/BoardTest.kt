package com.pierbezuhoff.clonium.domain

import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec

class BoardTest : FreeSpec() {

    init {
        "copy" - {
            "SimpleEmptyBoard" - {
                "property" {
                    assertAll(SimpleEmptyBoardGenerator()) { board: SimpleEmptyBoard ->
                        val initialPoss = board.posSet.toSet()
                        val copied = board.copy()
                        board.posSet shouldContainExactly initialPoss
                        board.posSet.add(Pos(board.width - 1, board.height - 1))
                        board.posSet.remove(board.posSet.first())
                        copied.width shouldBe board.width
                        copied.height shouldBe board.height
                        copied.asPosSet() shouldContainExactly initialPoss
                    }
                }
            }
            "SimpleBoard" - {
                "property" {

                }
            }
        }
    }

}
