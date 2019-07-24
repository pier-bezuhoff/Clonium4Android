package com.pierbezuhoff.clonium.domain

import androidx.annotation.IntRange

/** [Pos]ition on [Board]:
 * [x] = `0..(board.width - 1)` -- column
 * [y] = `0..(board.height - 1)` -- row */
data class Pos(val x: Int, val y: Int) {
    val right: Pos get() = Pos(x + 1, y)
    val left: Pos get() = Pos(x - 1, y)
    val up: Pos get() = Pos(x, y - 1)
    val down: Pos get() = Pos(x, y + 1)
    val neighbors: Set<Pos> get() = setOf(right, up, left, down)
}

/** Empty (= without [Chip]s on cells) board */
interface EmptyBoard {
    val width: Int
    val height: Int

    /** [Set] of [Pos]s with cell */
    fun asPosSet(): Set<Pos>

    fun hasCell(pos: Pos): Boolean =
        pos in asPosSet()

    /** 4 >= neighbor [Pos]s (with cell) of [pos] */
    fun neighbors(pos: Pos): Set<Pos> =
        with(pos) {
            setOf(Pos(x, y + 1), Pos(x, y - 1), Pos(x + 1, y), Pos(x - 1, y))
                .filter { hasCell(it) }
                .toSet()
        }

    fun pos2str(pos: Pos): String =
        when {
            !hasCell(pos) -> "  "
            else -> "□ "
        }

    fun copy(): EmptyBoard =
        SimpleEmptyBoard(width, height, asPosSet().toMutableSet())

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    fun asString(): String {
        return buildString {
            append((0 until width)
                .joinToString(prefix = "x>", separator = "", postfix = "<x") { x -> "$x " })
            for (y in 0 until height) {
                appendln()
                append((0 until width)
                    .joinToString(prefix = "$y|", separator = "", postfix = "|$y") { x -> pos2str(Pos(x, y)) })
            }
            appendln()
            append((0 until width).joinToString(prefix = "x>", separator = "", postfix = "<x") { x -> "$x " })
            appendln()
        }
    }
}

class SimpleEmptyBoard(
    override val width: Int,
    override val height: Int,
    val posSet: MutableSet<Pos>
) : EmptyBoard {
    override fun asPosSet(): Set<Pos> =
        posSet

    override fun toString(): String =
        asString()

    override fun equals(other: Any?): Boolean =
        other is EmptyBoard && other.asString() == asString()

    override fun hashCode(): Int =
        asString().hashCode()
}


/** Owner of [Chip] */
open class PlayerId(/** non-negative */ val id: Int) {
    override fun toString(): String =
        "PlayerId($id)"
    override fun equals(other: Any?): Boolean =
        other is PlayerId && id == other.id
    override fun hashCode(): Int =
        id
}
object PlayerId0 : PlayerId(0)
object PlayerId1 : PlayerId(1)
object PlayerId2 : PlayerId(2)
object PlayerId3 : PlayerId(3)

/** Level (# of holes) of [Chip] */
open class Level(@IntRange(from = 1, to = 7) val ordinal: Int) : Comparable<Level> {
    fun valid(): Boolean =
        Level1 <= this && this <= Level7
    override fun toString(): String =
        "Level($ordinal)"
    override fun compareTo(other: Level): Int =
        ordinal.compareTo(other.ordinal)
    override fun equals(other: Any?): Boolean =
        other is Level && ordinal == other.ordinal
    override fun hashCode(): Int =
        ordinal
    operator fun plus(delta: Int): Level =
        Level(ordinal + delta)
    operator fun minus(delta: Int): Level =
        Level(ordinal - delta)
    companion object {
        val MAX_STABLE_LEVEL = Level3
        val MIN_UNSTABLE_LEVEL = Level4
        val MAX_LEVEL = Level7
    }
}
object Level1 : Level(1)
object Level2 : Level(2)
object Level3 : Level(3)
object Level4 : Level(4)
object Level7 : Level(7)

/** Element placed on cell, owned by [playerId] with [level] (= # of holes) */
data class Chip(val playerId: PlayerId, val level: Level)

/** Board with some [Chip]s on cells */
interface Board : EmptyBoard {
    override fun asPosSet(): Set<Pos> = asPosMap().keys

    /** For [Pos] with cell: `Map.Entry<Pos, Chip?>` */
    fun asPosMap(): Map<Pos, Chip?>

    fun chipAt(pos: Pos): Chip? =
        asPosMap()[pos]

    fun hasChip(pos: Pos): Boolean =
        chipAt(pos) != null

    fun playerAt(pos: Pos): PlayerId? =
        chipAt(pos)?.playerId

    fun levelAt(pos: Pos): Level? =
        chipAt(pos)?.level

    /** alive (with [Chip]s) players on the [Board] */
    fun players(): Set<PlayerId> =
        asPosMap().values
            .filterNotNull()
            .map { it.playerId }
            .distinct()
            .toSet()

    /** [Set] of all [Pos]s owned by player with [playerId] */
    fun possOf(playerId: PlayerId): Set<Pos> =
        asPosMap()
            .filterValues { chip -> chip?.playerId == playerId }
            .keys

    fun isAlive(playerId: PlayerId): Boolean =
        possOf(playerId).isNotEmpty()

    override fun pos2str(pos: Pos): String =
        when {
            !hasCell(pos) -> "  "
            !hasChip(pos) -> "□ "
            else -> {
                val (player, level) = chipAt(pos)!!
                val playerChars = "⁰¹²³⁴⁵⁶⁷⁸⁹ⁿ"
                val playerChar =
                    if (player.id <= 9)
                        playerChars[player.id]
                    else
                        playerChars.last()
                "${level.ordinal}$playerChar"
            }
        }

    override fun copy(): Board =
        SimpleBoard(width, height, asPosMap().toMutableMap())
}

class SimpleBoard(
    override val width: Int,
    override val height: Int,
    val posMap: MutableMap<Pos, Chip?>
) : Board {
    constructor(emptyBoard: EmptyBoard) : this(
        emptyBoard.width, emptyBoard.height,
        mutableMapOf<Pos, Chip?>().apply {
            emptyBoard.asPosSet().associateWithTo(this) { null }
        }
    )
    constructor(board: Board) : this(
        board.width, board.height, board.asPosMap().toMutableMap()
    )
    override fun asPosMap(): Map<Pos, Chip?> =
        posMap

    override fun toString(): String =
        asString()

    override fun copy(): SimpleBoard =
        SimpleBoard(width, height, posMap.toMutableMap())

    override fun equals(other: Any?): Boolean =
        other is Board && other.asString() == asString()

    override fun hashCode(): Int =
        asString().hashCode()

    fun addChipAt(chip: Chip?, pos: Pos) {
        posMap[pos] = chip
    }

    /** Return [copy] of this [SimpleBoard] with applied changes of [pairs] */
    fun with(vararg pairs: Pair<Pos, Chip?>): SimpleBoard {
        val board = copy()
        for ((pos, maybeChip) in pairs)
            board.posMap[pos] = maybeChip
        return board
    }
}


/** Single-chip effect of [EvolvingBoard.inc]: [Chip] at [center] decrease [Level] by 4 pAndP
 * 4 transient [Chip]s with `Level(1)` explode to [up], [right], [down] pAndP [left] */
data class Explosion(
    val playerId: PlayerId,
    val center: Pos,
    val up: EndState,
    val right: EndState,
    val down: EndState,
    val left: EndState
) {
    enum class EndState {
        /** After [Explosion] [Chip] lands on cell */
        LAND,
        /** After [Explosion] [Chip] falls from the [Board] */
        FALLOUT
    }
}
/** Single-step effect of [EvolvingBoard.inc]: series of simultaneous [explosions] */
data class Transition(
    /** [Board] after decreasing exploded [Chip]s while transient ones are flying in 4 directions */
    val interimBoard: Board,
    /** [Board] after [Transition] */
    val endBoard: Board,
    val explosions: Set<Explosion>
)

/** Board with [Chip]s on which [Player]s can make turns ([inc] pAndP [incAnimated]) */
interface EvolvingBoard : Board {
    class InvalidTurn(reason: String) : Exception(reason)

    override fun copy(): EvolvingBoard

    @Throws(InvalidTurn::class)
    /** Increase [Level] at [pos] by 1, sThenS explode all unstable [Chip]s */
    fun inc(pos: Pos)

    @Throws(InvalidTurn::class)
    /** Increase [Level] at [pos] by 1, sThenS explode all unstable [Chip]s while recording [Transition]s */
    fun incAnimated(pos: Pos): Sequence<Transition>
}
// TODO: SimpleEvolvingBoard


object EmptyBoardFactory {
    fun SimpleEmptyBoard.symmetricRemove(x: Int, y: Int) {
        require(hasCell(Pos(x, y)))
        posSet.removeAll(setOf(
            Pos(x, y), Pos(x, height - 1 - y),
            Pos(width - 1 - x, y), Pos(width - 1 - x, height - 1 - y)
        ))
    }

    fun square(size: Int): SimpleEmptyBoard =
        rectangular(size, size)

    fun rectangular(width: Int, height: Int): SimpleEmptyBoard =
        SimpleEmptyBoard(
            width, height,
            (0 until height).flatMap { y ->
                (0 until width).map { x ->
                    Pos(x, y)
                }
            }.toMutableSet()
        )

    fun roundedRectangular(width: Int, height: Int): SimpleEmptyBoard {
        require(width >= 2 && height >= 2)
        val emptyBoard = rectangular(width, height)
        emptyBoard.symmetricRemove(0, 0)
        return emptyBoard
    }

    val SMALL_TOWER = rectangular(6, 6).apply {
        symmetricRemove(0, 1)
        symmetricRemove(1, 0)
        symmetricRemove(0, 2)
        symmetricRemove(2, 0)
    }
    val TOWER = rectangular(8, 8).apply {
        for (i in 1..3) {
            symmetricRemove(0, i)
            symmetricRemove(i, 0)
        }
    }
    // Default empty boards from BGC/Clonium
    val DEFAULT_1 = rectangular(8, 8)
    val DEFAULT_2 = rectangular(6, 6)
    val DEFAULT_3 = roundedRectangular(8, 8).apply {
        symmetricRemove(3, 3)
    }
    val DEFAULT_4 = roundedRectangular(8, 8)
    val DEFAULT_5 = rectangular(8, 8).apply {
        symmetricRemove(0, 3)
        symmetricRemove(3, 0)
    }
}

object BoardFactory {
    private fun SimpleBoard.spawn4symmetricPlayers(x: Int, y: Int) {
        require(hasCell(Pos(x, y)))
        val poss = setOf(
            Pos(x, y), Pos(x, height - 1 - y),
            Pos(width - 1 - x, y), Pos(width - 1 - x, height - 1 - y)
        )
        for ((i, pos) in poss.withIndex())
            posMap[pos] = Chip(PlayerId(i), Level.MAX_STABLE_LEVEL)
    }

    fun SimpleBoard.spawn4players(margin: Int = 1) {
        require(2 * margin <= width && 2 * margin <= height)
        spawn4symmetricPlayers(margin, margin)
    }

    fun spawn4players(emptyBoard: EmptyBoard, margin: Int = 1): SimpleBoard =
        SimpleBoard(emptyBoard).apply { spawn4players(margin) }

    // Default boards from BGC/Clonium
    val DEFAULT_1 = spawn4players(EmptyBoardFactory.DEFAULT_1)
    val DEFAULT_2 = spawn4players(EmptyBoardFactory.DEFAULT_2)
    val DEFAULT_3 = spawn4players(EmptyBoardFactory.DEFAULT_3)
    val DEFAULT_4 = spawn4players(EmptyBoardFactory.DEFAULT_4)
    val DEFAULT_5 = spawn4players(EmptyBoardFactory.DEFAULT_5)
}