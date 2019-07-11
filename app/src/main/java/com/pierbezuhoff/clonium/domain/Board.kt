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
    fun hasCell(pos: Pos): Boolean
    fun cellAt(pos: Pos): Cell? =
        if (hasCell(pos)) Cell else null
    /** [Set] of [Pos]s with [Cell] */
    fun asPosSet(): Set<Pos>
}

/** Owner of [Chip], [id] MUST be unique and non-negative */
data class Player(/** Unique, non-negative */ val id: Int)
data class Level(@IntRange(from = 1, to = 7) val ordinal: Int) : Any()
    , Comparable<Level>
{
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
data class Chip(val player: Player, val level: Level)

interface Board {
    fun chipAt(pos: Pos): Chip?
    fun hasChip(pos: Pos): Boolean =
        chipAt(pos) != null
    fun playerAt(pos: Pos): Player? =
        chipAt(pos)?.player
    fun levelAt(pos: Pos): Level? =
        chipAt(pos)?.level
    /** For [Pos] with [Cell]: [Map.Entry<Pos, Chip?>] */
    fun asPosMap(): Map<Pos, Chip?>
}

enum class ExplosionEndState {
    /** After [Explosion] [Chip] lands on [Cell] */
    LAND,
    /** After [Explosion] [Chip] falls from the [Board] */
    FALLOUT
}
data class Explosion(
    val player: Player,
    val center: Pos,
    val up: ExplosionEndState,
    val right: ExplosionEndState,
    val down: ExplosionEndState,
    val left: ExplosionEndState
)
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