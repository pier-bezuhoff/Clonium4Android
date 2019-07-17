package com.pierbezuhoff.clonium.domain

import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.maps.shouldContainExactly
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.plus

class BoardTest : FreeSpec() {
    init {
        "interface EmptyBoard" - {
            "copy" - {
                "EmptyBoard" {
                    SimpleEmptyBoardGenerator().assertAll(iterations = 1_000) { board: SimpleEmptyBoard ->
                        val initialPoss = board.posSet.toSet()
                        val copied = board.copy()
                        board.posSet.add(Pos(board.width - 1, board.height - 1))
                        board.posSet.remove(board.posSet.first())
                        copied.width shouldBe board.width
                        copied.height shouldBe board.height
                        copied.asPosSet() shouldContainExactly initialPoss
                    }
                }
                "Board" {
                    SimpleBoardGenerator().assertAll(iterations = 1_000) { board: SimpleBoard ->
                        val initialPosMap = board.posMap.toMap()
                        val copied = board.copy()
                        board.posMap[Pos(board.width - 1, board.height - 1)] = Chip(PlayerId(0), Level(2))
                        board.posMap.remove(board.posMap.keys.first())
                        board.posMap[Pos(0, 0)] = Chip(PlayerId(1), Level(2))
                        copied.width shouldBe board.width
                        copied.height shouldBe board.height
                        copied.asPosMap() shouldContainExactly initialPosMap
                    }
                }
                "PrimitiveBoard" {
                    PopulatedPrimitiveBoardGenerator().assertAll(iterations = 1_000) { board: PrimitiveBoard ->
                        val initialPosMap = board.asPosMap()
                        val copied = board.copy()
                        board.inc(board.possOf(board.players().first()).last())
                        copied.width shouldBe board.width
                        copied.height shouldBe board.height
                        copied.asPosMap() shouldContainExactly initialPosMap
                    }
                }
            }
            "asPosSet & hasCell" - {
                "PrimitiveBoard" {
                    PrimitiveBoardGenerator().assertAll(iterations = 10_000) { board: PrimitiveBoard ->
                        PosGenerator(board).assertAll(iterations = 10) { pos: Pos ->
                            board.hasCell(pos) shouldBe (pos in board.asPosSet())
                        }
                    }
                }
            }
        }

        "interface Board" - {
            "PrimitiveBoard" - {
                "asPosSet & asPosMap & chipAt & hasCell & hasChip" {
                    PrimitiveBoardGenerator().assertAll(iterations = 10_000) { board: PrimitiveBoard ->
                        board.asPosSet() shouldContainExactlyInAnyOrder board.asPosMap().keys
                        PosGenerator(board).assertAll(iterations = 10) { pos: Pos ->
                            (board.chipAt(pos) != null) shouldBe board.hasChip(pos)
                            board.chipAt(pos) shouldBe board.asPosMap()[pos]
                        }
                    }
                }
                "chipAt & playerAt & levelAt" {
                    PrimitiveBoardGenerator().assertAll(iterations = 10_000) { board: PrimitiveBoard ->
                        PosGenerator(board).assertAll(iterations = 10) { pos: Pos ->
                            board.chipAt(pos)?.level shouldBe board.levelAt(pos)
                            board.chipAt(pos)?.playerId shouldBe board.playerAt(pos)
                        }
                    }
                }
                "asPosMap & players & isAlive" {
                    PrimitiveBoardGenerator().assertAll(iterations = 10_000) { board: PrimitiveBoard ->
                        board.players() shouldContainExactlyInAnyOrder
                                board.asPosMap()
                                    .values.mapNotNull { it?.playerId }
                                    .toSet()
                        board.players().forAll { board.isAlive(it) shouldBe true }
                    }
                }
                "asPosMap & possOf & players & isAlive" {
                    PrimitiveBoardGenerator().assertAll(iterations = 10_000) { board: PrimitiveBoard ->
                        board.players().forAll { playerId ->
                            board.possOf(playerId) shouldContainExactlyInAnyOrder
                                    board.asPosMap()
                                        .filterValues { it?.playerId == playerId }
                                        .keys
                            board.isAlive(playerId) shouldBe board.possOf(playerId).isNotEmpty()
                        }
                        board.asPosMap().filterValues { it != null }.keys shouldContainExactlyInAnyOrder
                                board.players().flatMap { board.possOf(it) }
                    }
                }
            }
        }
    }
}
