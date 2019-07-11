package com.pierbezuhoff.clonium.domain

import androidx.annotation.IntRange

/** [Pos]ition on [Board]:
 * [x] = `0..(board.width - 1)` -- column
 * [y] = `0..(board.height - 1)` -- row */
data class Pos(val x: Int, val y: Int)
object Cell

interface EmptyBoard {
    val width: Int
    val height: Int
    fun hasCell(pos: Pos): Boolean =
        pos in asPosSet()
    fun cellAt(pos: Pos): Cell? =
        if (hasCell(pos)) Cell else null
    /** [Set] of [Pos]s with [Cell] */
    fun asPosSet(): Set<Pos>
}

/** Owner of [Chip], [id] MUST be unique and non-negative */
data class PlayerId(/** Unique, non-negative */ val id: Int)
data class Level(@IntRange(from = 1, to = 7) val ordinal: Int) : Comparable<Level> {
    override fun compareTo(other: Level): Int =
        ordinal.compareTo(other.ordinal)
    operator fun plus(delta: Int): Level =
        Level(ordinal + delta)
    operator fun minus(delta: Int): Level =
        Level(ordinal - delta)
    companion object {
        val MAX_STABLE_LEVEL = Level(3)
        val MIN_UNSTABLE_LEVEL = Level(4)
        val MAX_LEVEL = Level(7)
    }
}
data class Chip(val playerId: PlayerId, val level: Level)

interface Board : EmptyBoard {
    fun chipAt(pos: Pos): Chip? =
        if (!hasCell(pos)) null else asPosMap()[pos]
    fun hasChip(pos: Pos): Boolean =
        chipAt(pos) != null
    fun playerAt(pos: Pos): PlayerId? =
        chipAt(pos)?.playerId
    fun levelAt(pos: Pos): Level? =
        chipAt(pos)?.level
    /** For [Pos] with [Cell]: `Map.Entry<Pos, Chip?>` */
    fun asPosMap(): Map<Pos, Chip?>
    fun players(): Set<PlayerId> =
        asPosMap().values
            .filterNotNull()
            .map { it.playerId }
            .distinct()
            .toSet()
    fun possOf(playerId: PlayerId): Set<Pos> =
        asPosMap()
            .filterValues { chip -> chip?.playerId == playerId }
            .keys
}

class SimpleBoard(
    override val width: Int,
    override val height: Int,
    private val posMap: Map<Pos, Chip?>
) : Board {
    override fun asPosSet(): Set<Pos> = posMap.keys
    override fun asPosMap(): Map<Pos, Chip?> = posMap
}

data class Explosion(
    val playerId: PlayerId,
    val center: Pos,
    val up: EndState,
    val right: EndState,
    val down: EndState,
    val left: EndState
) {
    enum class EndState {
        /** After [Explosion] [Chip] lands on [Cell] */
        LAND,
        /** After [Explosion] [Chip] falls from the [Board] */
        FALLOUT
    }
}
/** Series of simultaneous [Explosion]s */
data class Transition(
    val interimState: Board,
    val endState: Board,
    val explosions: Set<Explosion>
)

interface EvolvingBoard : Board {
    class InvalidTurn(reason: String) : Exception(reason)

    @Throws(InvalidTurn::class)
    /** Increase [Level] at [pos] by 1, then explode all unstable chips while recording [Transition]s */
    fun inc(pos: Pos): Sequence<Transition>
}

