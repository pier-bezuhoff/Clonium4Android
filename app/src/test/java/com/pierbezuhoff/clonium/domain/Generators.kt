package com.pierbezuhoff.clonium.domain

import io.kotlintest.properties.Gen

fun Gen.Companion.softChoose(min: Int, max: Int): Gen<Int> =
    when {
        min > max -> Gen.from(emptyList()) // NOTE: it throw an exception, I suppose
        min == max -> Gen.constant(min)
        min < max -> Gen.choose(min, max)
        else -> error("impossible")
    }

fun PosGenerator(width: Int, height: Int): Gen<Pos> =
    Gen.bind(
        Gen.softChoose(0, width),
        Gen.softChoose(0, height)
    ) { x, y -> Pos(x, y) }

fun PosGenerator(emptyBoard: EmptyBoard): Gen<Pos> =
    PosGenerator(emptyBoard.width, emptyBoard.height)

fun ChipGenerator(): Gen<Chip> =
    Gen.bind(Gen.choose(0, 4), Gen.choose(1, 4)) { id, ordinal -> Chip(PlayerId(id), Level(ordinal)) }

class SimpleEmptyBoardGenerator(private val softMinSize: Int = 1, private val posRatio: Double? = null) : Gen<SimpleEmptyBoard> {
    override fun constants(): Iterable<SimpleEmptyBoard> =
        with(SimpleEmptyBoard.Examples) {
            listOf(
                *(1..5).map { SimpleEmptyBoard.Factory.square(it) }.toTypedArray(),
                DEFAULT_1, DEFAULT_2, DEFAULT_3, DEFAULT_4, DEFAULT_5,
                SMALL_TOWER, TOWER
            )
        }

    override fun random(): Sequence<SimpleEmptyBoard> =
        if (posRatio == null)
            Gen.pair(Gen.choose(softMinSize, 10), Gen.choose(softMinSize, 10))
                .flatMap { (width, height) -> Gen.set(PosGenerator(width, height))
                    .map { posSet -> SimpleEmptyBoard(width, height, posSet.toMutableSet()) } }
                .random()
        else // TODO: check if it works
            Gen.pair(Gen.choose(softMinSize, 10), Gen.choose(softMinSize, 10))
                .map { (width, height) ->
                    val nPoss = (width * height * posRatio).toInt()
                    val posSet = PosGenerator(width, height).random().distinct().take(nPoss).toSet()
                    SimpleEmptyBoard(width, height, posSet.toMutableSet())
                }.random()
}

class SimpleBoardGenerator : Gen<SimpleBoard> {
    override fun constants(): Iterable<SimpleBoard> =
        SimpleEmptyBoardGenerator().constants().flatMap {
            listOfNotNull(
                SimpleBoard(it.copy()),
                populate(it, 1),
                if (it.width >= 2) populate(it, 2) else null,
                if (it.width >= 3) populate(it, 3) else null,
                if (it.width >= 4 && it.hasCell(Pos(1, 1)))
                    SimpleBoard.Factory.spawn4players(it.copy(), margin = 1)
                else null,
                if (it.width >= 6 && it.hasCell(Pos(2, 2)))
                    SimpleBoard.Factory.spawn4players(it.copy(), margin = 2)
                else null
            )
        }

    override fun random(): Sequence<SimpleBoard> =
        SimpleEmptyBoardGenerator().random().flatMap {
            val poss = it.asPosSet()
            val nPoss = poss.size
            val boardEmpty = SimpleBoard(it.copy())
            val n1 = Gen.softChoose(1, nPoss / 2 + 1).random().first()
            val board1 = populate(it, n1)
            val n2 = Gen.softChoose(nPoss / 2, nPoss).random().first()
            val board2 = populate(it, n2)
            val boardFull = populate(it, nPoss)
            sequenceOf(
                boardEmpty, // no chips
                board1, // <= 1/2 poss with chips
                board2, // >= 1/2 poss with chips
                boardFull // all poss with chips
            )
        }

    private fun populate(emptyBoard: EmptyBoard, nChips: Int): SimpleBoard =
        SimpleBoard(emptyBoard.copy()).apply {
            for (pos in emptyBoard.asPosSet().shuffled().take(nChips))
                posMap[pos] = ChipGenerator().random().first()
        }
}

fun PrimitiveBoardGenerator(): Gen<PrimitiveBoard> =
    SimpleBoardGenerator().map { PrimitiveBoard.Factory.of(it) }

fun PopulatedPrimitiveBoardGenerator(): Gen<PrimitiveBoard> =
    PrimitiveBoardGenerator().filter { it.players().isNotEmpty() }

fun VeryPopulatedPrimitiveBoardGenerator(posRatio: Double? = 0.9, chipRatio: Double = 0.5): Gen<PrimitiveBoard> =
    SimpleEmptyBoardGenerator(softMinSize = 6, posRatio = posRatio).map {
        PrimitiveBoard.Factory.of(SimpleBoard(it).apply {
            val nPoss = it.posSet.size
            for (pos in it.posSet.shuffled().take((chipRatio * nPoss).toInt()))
                posMap[pos] = ChipGenerator().random().first()
        })
    }
