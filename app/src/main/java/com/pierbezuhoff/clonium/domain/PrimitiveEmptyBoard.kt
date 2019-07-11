package com.pierbezuhoff.clonium.domain

@Suppress("CanBePrimaryConstructorProperty", "NOTHING_TO_INLINE")
open class PrimitiveEmptyBoard(
    final override val width: Int,
    final override val height: Int,
    cellMap: Map<Pos, Cell?>
) : EmptyBoard {
    /** Indices:
     * 012345
     * 678910... */
    protected val cells: BooleanArray = BooleanArray(width * height).apply {
        for (ix in indices)
            this[ix] = false
        for ((pos, maybeCell) in cellMap)
            this[pos2ix(pos)] = maybeCell != null
    }

    protected inline fun pos2ix(pos: Pos): Int =
        pos.x + pos.y * width

    protected inline fun ix2pos(ix: Int): Pos =
        Pos(ix % width, ix / width)

    final override fun cellAt(pos: Pos): Cell? =
        if (cells[pos2ix(pos)]) Cell else null

    final override fun asPosSet(): Set<Pos> {
        val poss = mutableSetOf<Pos>()
        for (ix in cells.indices)
            if (cells[ix])
                poss.add(ix2pos(ix))
        return poss
    }
}

