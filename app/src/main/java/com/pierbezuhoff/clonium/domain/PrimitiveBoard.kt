package com.pierbezuhoff.clonium.domain

import java.lang.Exception

// MAYBE: somehow discard useless [cells]
@Suppress("NOTHING_TO_INLINE")
class PrimitiveBoard(
    width: Int,
    height: Int,
    /** (pos to [null]) means there is [Cell] at pos but no chip */
    chipsMap: Map<Pos, Chip?>
) : PrimitiveEmptyBoard(width, height, chipsMap.mapValues { Cell })
    , Board
    , EvolvingBoard
{
    private val chips: IntArray = IntArray(width * height).apply {
        for (ix in indices)
            this[ix] = NO_CELL
        for ((pos, maybeChip) in chipsMap)
            this[pos2ix(pos)] = chip2int(maybeChip)
    }

    private inline fun chip2int(maybeChip: Chip?): Int =
        maybeChip?.let { (player, level) ->
            level.ordinal + player.id * Level.MAX_LEVEL.ordinal
        } ?: NO_CHIP

    private inline fun int2chip(value: Int): Chip? =
        when {
            value == NO_CELL || value == NO_CHIP ->
                null
            value < -2 ->
                throw Exception("Impossible to decode $value < -2 to Chip?")
            value % Level.MAX_LEVEL.ordinal == 0 ->
                throw Exception("Impossible to decode $value with level = 0 to Chip?")
            else ->
                Chip(
                    Player(value / Level.MAX_LEVEL.ordinal),
                    Level(value % Level.MAX_LEVEL.ordinal)
                )
        }

    override fun chipAt(pos: Pos): Chip? =
        int2chip(chips[pos2ix(pos)])

    override fun asPosMap(): Map<Pos, Chip?> {
        val map = mutableMapOf<Pos, Chip?>()
        for (ix in chips.indices)
            chips[ix].let { value ->
                if (value >= -1)
                    map[ix2pos(ix)] = int2chip(value)
            }
        return map
    }

    /** 4 neighbor indices of [ix] */
    private fun ix4(ix: Int): Set<Int> =
        setOf(ix - width, ix - 1, ix + 1, ix + width)

    override fun inc(pos: Pos): Sequence<Transition> {
        val ix = pos2ix(pos)
        if (chips[ix] == NO_CELL)
            throw EvolvingBoard.InvalidTurn("There is no cell on $pos")
        if (chips[ix] == NO_CHIP)
            throw EvolvingBoard.InvalidTurn("There is no chip on $pos")
        val (player, level) = chipAt(pos)!!
        chips[ix] += 1
        if (level + 1 < Level.MIN_UNSTABLE_LEVEL) {
            return emptySequence()
        } else {
            TODO()
        }
    }

    companion object {
        private const val NO_CELL = -2
        private const val NO_CHIP = -1
    }
}