package com.pierbezuhoff.clonium.domain

import io.kotlintest.properties.Gen

fun Gen.Companion.softChoose(min: Int, max: Int): Gen<Int> =
    when {
        min > max -> Gen.from(emptyList())
        min == max -> Gen.constant(min)
        min < max -> Gen.choose(min, max)
        else -> error("impossible")
    }

fun PosGenerator(width: Int, height: Int): Gen<Pos> =
    Gen.bind(
        Gen.softChoose(0, width - 1),
        Gen.softChoose(0, height - 1)
    ) { x, y -> Pos(x, y) }

fun ChipGenerator(): Gen<Chip> =
    Gen.bind(Gen.choose(0, 3), Gen.choose(1, 3)) { id, ordinal -> Chip(PlayerId(id), Level(ordinal)) }

fun SimpleEmptyBoardGenerator(): Gen<SimpleEmptyBoard> =
    Gen.pair(Gen.choose(1, 10), Gen.choose(1, 10))
        .flatMap { (width, height) -> Gen.set(PosGenerator(width, height))
            .map { posSet -> SimpleEmptyBoard(width, height, posSet.toMutableSet()) } }

class EmptyBoardGenerator : Gen<EmptyBoard> {
    override fun constants(): Iterable<EmptyBoard> =
        with(EmptyBoardFactory) {
            listOf(
                SMALL_TOWER, TOWER,
                DEFAULT_1, DEFAULT_2, DEFAULT_3, DEFAULT_4, DEFAULT_5,
                rectangular(1, 1)
            )
        }

    override fun random(): Sequence<EmptyBoard> =
        Gen.pair(Gen.choose(1, 10), Gen.choose(1, 10))
            .flatMap { (width, height) -> Gen.set(PosGenerator(width, height))
                .map { posSet -> SimpleEmptyBoard(width, height, posSet.toMutableSet()) } }
            .random()
        generateSequence {
            val width = Gen.positiveIntegers().random().first()
            val height = Gen.positiveIntegers().random().first()
            val useCuts = Gen.bool().random().first()
            return@generateSequence if (useCuts) {
                val symmetricCuts =
                    Gen.set(Gen.pair(Gen.positiveIntegers(), Gen.positiveIntegers())).random().first()
                EmptyBoardFactory.rectangular(width, height).apply {
                    with(EmptyBoardFactory) {
                        for ((x, y) in symmetricCuts)
                            symmetricRemove(x, y)
                    }
                }
            } else {
                EmptyBoardFactory.rectangular(width, height)
            }
        }
}

class BoardGenerator : Gen<Board> {
    override fun constants(): Iterable<Board> =
        EmptyBoardGenerator().constants().flatMap {
            listOfNotNull(
                SimpleBoard(it.copy()),
                if (it.width >= 4 && it.hasCell(Pos(1, 1)))
                    BoardFactory.spawn4players(it.copy(), margin = 1)
                else null,
                if (it.width >= 6 && it.hasCell(Pos(2, 2)))
                    BoardFactory.spawn4players(it.copy(), margin = 2)
                else null
            )
        }

    override fun random(): Sequence<Board> =
        EmptyBoardGenerator().random().flatMap {
            val poss = it.asPosSet()
            val nPoss = poss.size
            val board = SimpleBoard(it.copy())
            val n1 = Gen.choose(1, nPoss / 2 + 1).random().first()
            val board1 = SimpleBoard(it.copy()).apply {
                for (pos in poss.shuffled().take(n1))
                    posMap[pos] = ChipGenerator().random().first()
            }
            val n2 = Gen.choose(nPoss / 2, nPoss).random().first()
            val board2 = SimpleBoard(it.copy()).apply {
                for (pos in poss.shuffled().take(n2))
                    posMap[pos] = ChipGenerator().random().first()
            }
            val board3 = SimpleBoard(it.copy()).apply {
                for (pos in poss.shuffled())
                    posMap[pos] = ChipGenerator().random().first()
            }
            sequenceOf(
                board,
                board1,
                board2,
                board3
            )
        }
}