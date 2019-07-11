package com.pierbezuhoff.clonium.domain

import androidx.annotation.IntRange

data class Pos(val x: Int, val y: Int)
object Cell

interface EmptyBoard {
    val width: Int
    val height: Int
    fun cellAt(pos: Pos): Cell?
    fun asPosSet(): Set<Pos>
}

data class Player(val id: Int)
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
    fun playerAt(pos: Pos): Player? = chipAt(pos)?.player
    fun levelAt(pos: Pos): Level? = chipAt(pos)?.level
    fun asPosMap(): Map<Pos, Chip?>
}

enum class ExplosionEndState {
    LAND, FALLOUT
}
data class Explosion(
    val player: Player,
    val center: Pos,
    val up: ExplosionEndState,
    val right: ExplosionEndState,
    val down: ExplosionEndState,
    val left: ExplosionEndState
)
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