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

    /** [Set] of [Pos]s with [Cell] */
    fun asPosSet(): Set<Pos>

    fun hasCell(pos: Pos): Boolean =
        pos in asPosSet()

    fun cellAt(pos: Pos): Cell? =
        if (hasCell(pos)) Cell else null

    fun pos2str(pos: Pos): String =
        when {
            !hasCell(pos) -> "  "
            else -> "□ "
        }

    fun copy(): EmptyBoard =
        SimpleEmptyBoard(width, height, asPosSet().toMutableSet())

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
    /** For [Pos] with [Cell]: `Map.Entry<Pos, Chip?>` */
    fun asPosMap(): Map<Pos, Chip?>

    fun chipAt(pos: Pos): Chip? =
        if (!hasCell(pos)) null else asPosMap()[pos]

    fun hasChip(pos: Pos): Boolean =
        chipAt(pos) != null

    fun playerAt(pos: Pos): PlayerId? =
        chipAt(pos)?.playerId

    fun levelAt(pos: Pos): Level? =
        chipAt(pos)?.level

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
    override fun asPosSet(): Set<Pos> = posMap.keys
    override fun asPosMap(): Map<Pos, Chip?> = posMap
    override fun toString(): String =
        asString()
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
    val interimBoard: Board,
    val endBoard: Board,
    val explosions: Set<Explosion>
)

interface EvolvingBoard : Board {
    class InvalidTurn(reason: String) : Exception(reason)

    @Throws(InvalidTurn::class)
    /** Increase [Level] at [pos] by 1, then explode all unstable chips while recording [Transition]s */
    fun inc(pos: Pos): Sequence<Transition>
}
fun EvolvingBoard(board: Board): EvolvingBoard =
    PrimitiveBoard(board)

object EmptyBoardFactory {
    private fun SimpleEmptyBoard.symmetricRemove(x: Int, y: Int) {
        require(hasCell(Pos(x, y)))
        posSet.removeAll(setOf(
            Pos(x, y), Pos(x, height - 1 - y),
            Pos(width - 1 - x, y), Pos(width - 1 - x, height - 1 - y)
        ))
    }

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

    // Default empty boards from BGC Clonium
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

    private fun SimpleBoard.spawn4players(margin: Int = 1) {
        require(2 * margin <= width && 2 * margin <= height)
        spawn4symmetricPlayers(margin, margin)
    }

    fun rectangular(width: Int, height: Int, playerMargin: Int = 1): SimpleBoard =
        SimpleBoard(EmptyBoardFactory.rectangular(width, height)).apply {
            spawn4players(playerMargin)
        }

    fun roundedRectangular(width: Int, height: Int, playerMargin: Int = 1): SimpleBoard =
        SimpleBoard(EmptyBoardFactory.roundedRectangular(width, height)).apply {
            spawn4players(playerMargin)
        }

    // Default boards from BGC Clonium
    val DEFAULT_1 = SimpleBoard(EmptyBoardFactory.DEFAULT_1).apply { spawn4players() }
    val DEFAULT_2 = SimpleBoard(EmptyBoardFactory.DEFAULT_2).apply { spawn4players() }
    val DEFAULT_3 = SimpleBoard(EmptyBoardFactory.DEFAULT_3).apply { spawn4players() }
    val DEFAULT_4 = SimpleBoard(EmptyBoardFactory.DEFAULT_4).apply { spawn4players() }
    val DEFAULT_5 = SimpleBoard(EmptyBoardFactory.DEFAULT_5).apply { spawn4players() }
}