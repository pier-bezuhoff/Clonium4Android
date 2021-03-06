package com.pierbezuhoff.clonium.domain

import androidx.annotation.IntRange
import com.pierbezuhoff.clonium.utils.filterMap
import com.pierbezuhoff.clonium.utils.mapFilter
import java.io.Serializable

// TODO: SimpleEvolvingBoard

/** [Pos]ition on [Board]:
 * [x] = `0..(board.width - 1)` -- column
 * [y] = `0..(board.height - 1)` -- row */
data class Pos(val x: Int, val y: Int) : Serializable {
    val right: Pos get() = Pos(x + 1, y)
    val left: Pos get() = Pos(x - 1, y)
    val up: Pos get() = Pos(x, y - 1)
    val down: Pos get() = Pos(x, y + 1)
    val neighbors: Set<Pos> get() = setOf(right, up, left, down)
    val directedNeighbors: Map<Direction, Pos> get() =
        DIRECTIONS.associateWith { neighborAt(it) }

    fun neighborAt(direction: Direction): Pos =
        when (direction) {
            is Direction.Up -> up
            is Direction.Right -> right
            is Direction.Down -> down
            is Direction.Left -> left
        }
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
        pos.neighbors
            .filter { hasCell(it) }
            .toSet()

    fun directedNeighbors(pos: Pos): Map<Direction, Pos> =
        pos.directedNeighbors
            .filterValues { hasCell(it) }

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
                    .joinToString(prefix = "$y|", separator = "", postfix = "|$y") { x ->
                        runCatching {
                            pos2str(Pos(x, y))
                        }.getOrElse {
                            it.printStackTrace()
                            return@getOrElse "! "
                        }
                    })
            }
            appendln()
            append((0 until width).joinToString(prefix = "x>", separator = "", postfix = "<x") { x -> "$x " })
            appendln()
        }
    }

    interface Factory {
        /** ```
         * "1234\n5678" ->
         * Triple(width = 2, height = 2, xMap = mapOf(
         *     Pos(0, 0) to readCell('1', '2'), Pos(0, 1) to readCell('3', '4'),
         *     Pos(1, 0) to readCell('5', '6'), Pos(1, 1) to readCell('7', '8')
         * )
         * ```
         */
        fun <X> unmap(s: String, readCell: (Char, Char) -> X): Triple<Int, Int, Map<Pos, X>> {
            val lines = s.trimIndent().lines()
            require(lines.isNotEmpty())
            require(lines.first().length % 2 == 0)
            require(lines.all { it.length == lines.first().length })
            val height = lines.size
            val width = lines.first().length / 2
            val m: MutableMap<Pos, X> = mutableMapOf()
            for ((y, line) in lines.withIndex()) {
                line.chunked(2)
                    .withIndex()
                    .associateTo(m) { (x, c2) ->
                        Pos(x, y) to readCell(c2[0], c2[1])
                    }
            }
            return Triple(width, height, m)
        }

        /** -> has cell */
        fun readCell(c1: Char, c2: Char): Boolean =
            when {
                c1 == ' ' && c2 == ' ' -> false
                c1 == '□' && c2 == ' ' -> true
                else -> throw IllegalArgumentException("Invalid cell format: \"$c1$c2\"")
            }

        fun fromString(s: String): EmptyBoard
    }
}

class SimpleEmptyBoard(
    override val width: Int,
    override val height: Int,
    val posSet: MutableSet<Pos>
) : Any()
    , EmptyBoard
    , Serializable
{
    override fun asPosSet(): Set<Pos> =
        posSet

    fun symmetricRemove(x: Int, y: Int) {
        require(hasCell(Pos(x, y)))
        posSet.removeAll(setOf(
            Pos(x, y), Pos(x, height - 1 - y),
            Pos(width - 1 - x, y), Pos(width - 1 - x, height - 1 - y)
        ))
    }

    override fun toString(): String =
        asString()

    override fun equals(other: Any?): Boolean =
        other is EmptyBoard && other.asPosSet() == asPosSet()

    override fun hashCode(): Int =
        asString().hashCode()

    object Factory : EmptyBoard.Factory {
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

        override fun fromString(s: String): SimpleEmptyBoard {
            val (width, height, cellMap) = unmap(s) { ch, _ -> ch != ' ' }
            return SimpleEmptyBoard(
                width, height,
                cellMap.filterValues { it }.keys.toMutableSet()
            )
        }
    }

    object Examples {
        val SMALL_TOWER = Factory.rectangular(6, 6).apply {
            symmetricRemove(0, 1)
            symmetricRemove(1, 0)
            symmetricRemove(0, 2)
            symmetricRemove(2, 0)
        }
        val TOWER = Factory.rectangular(8, 8).apply {
            for (i in 1..3) {
                symmetricRemove(0, i)
                symmetricRemove(i, 0)
            }
        }
        // Default empty boards from BGC/Clonium
        val DEFAULT_1 = Factory.rectangular(8, 8)
        val DEFAULT_2 = Factory.rectangular(6, 6)
        val DEFAULT_3 = Factory.roundedRectangular(8, 8).apply {
            symmetricRemove(3, 3)
        }
        val DEFAULT_4 = Factory.roundedRectangular(8, 8)
        val DEFAULT_5 = Factory.rectangular(8, 8).apply {
            symmetricRemove(0, 3)
            symmetricRemove(3, 0)
        }
    }
}


/** Owner of [Chip] */
open class PlayerId(/** non-negative */ val id: Int) : Serializable {
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
open class Level(@IntRange(from = 1, to = 7) val ordinal: Int) : Any()
    , Comparable<Level>
    , Serializable
{
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
        const val MAX_STABLE_LEVEL_ORDINAL = 3
        const val MIN_UNSTABLE_LEVEL_ORDINAL = 4
        const val MAX_LEVEL_ORDINAL = 7
    }
}
object Level1 : Level(1)
object Level2 : Level(2)
object Level3 : Level(3)
object Level4 : Level(4)
object Level7 : Level(7)

/** Element placed on cell, owned by [playerId] with [level] (= # of holes) */
data class Chip(val playerId: PlayerId, val level: Level) : Serializable

/** Board with some [Chip]s on cells */
interface Board : EmptyBoard {
    override fun asPosSet(): Set<Pos> = asPosMap().keys

    /** For [Pos] with cell: `Map.Entry<Pos, Chip?>` */
    fun asPosMap(): Map<Pos, Chip?>

    fun asInhabitedPosMap(): Map<Pos, Chip> =
        asPosMap()
            .mapNotNull { (key, value) ->
                if (value == null) null
                else key to value
            }
            .toMap()

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

    fun chipCountOf(playerId: PlayerId): Int =
        possOf(playerId).size

    fun isAlive(playerId: PlayerId): Boolean =
        possOf(playerId).isNotEmpty()

    fun levelOf(playerId: PlayerId): Int =
        possOf(playerId).sumBy { levelAt(it)?.ordinal ?: 0 }

    fun diff(board: Board): Map<Pos, Chip?> {
        require(width == board.width && height == board.height)
        val allPoss = asPosSet() + board.asPosSet()
        return allPoss
            .filter { board.chipAt(it) != chipAt(it) }
            .associateWith { board.chipAt(it) }
            .toMutableMap()
    }

    /** [Set] of transitive [Level3] neighbors */
    fun chains(): Set<Set<Pos>> {
        val level3poss = asPosSet().filter { levelAt(it)?.let { it >= Level3 } ?: false  }
        val chains = mutableSetOf<MutableSet<Pos>>()
        val chained = mutableSetOf<Pos>()
        for (pos in level3poss) {
            val chainedNeighbors = neighbors(pos).intersect(chained)
            val neighborChains = chains.filter { it.intersect(chainedNeighbors).isNotEmpty() }
            if (neighborChains.isEmpty()) {
                chains.add(mutableSetOf(pos))
            } else {
                val mainChain = neighborChains.first()
                mainChain.add(pos)
                if (neighborChains.size > 1) {
                    for (chain in neighborChains.drop(1)) {
                        chains.remove(chain)
                        mainChain.addAll(chain)
                    }
                }
            }
            chained.add(pos)
        }
        return chains
    }

    /** Transitive [Level3] neighbors of [pos] if [pos] has at least [Level3] else `null` */
    fun chainOf(pos: Pos): Set<Pos>? =
        if (levelAt(pos)?.let { it < Level3 } ?: true)
            null
        else
            chains().first { pos in it }

    /** All possible turns of [playerId] with distinct results (1 turn form each [Level3]-chain) */
    fun distinctTurnsOf(playerId: PlayerId): Set<Pos> {
        val chains = chains()
        fun chainIdOf(pos: Pos): Int =
            chains.indexOfFirst { pos in it }
        val poss = possOf(playerId)
        val (stable, unstable) = poss.partition { levelAt(it)?.let { it < Level3 } ?: true }
        return (stable + unstable.distinctBy { chainIdOf(it) }).toSet()
    }

    fun groupedDistinctTurns(playerId: PlayerId): Set<Set<Pos>> {
        val chains = chains()
        fun chainIdOf(pos: Pos): Int =
            chains.indexOfFirst { pos in it }
        val poss = possOf(playerId)
        val (stable, unstable) = poss.partition { levelAt(it)!! < Level3 }
        return stable.map { setOf(it) }.toSet() + unstable.groupBy { chainIdOf(it) }.values.map { it.toSet() }
    }

    /** Cyclically shift [order] removing dead players */
    fun shiftOrder(order: List<PlayerId>): List<PlayerId> {
        if (order.isEmpty())
            return order
        val filteredOrder = order.filter { isAlive(it) }
        return if (filteredOrder.isEmpty()) emptyList() else filteredOrder.drop(1) + filteredOrder.first()
    }

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

    interface Factory : EmptyBoard.Factory {
        fun of(emptyBoard: EmptyBoard): Board

        override fun fromString(s: String): Board

        /** Scan 2 chars of asString() repr and return Pair(has cell, chip) */
        fun readChip(c1: Char, c2: Char): Pair<Boolean, Chip?> =
            if (c2 == ' ')
                Pair(readCell(c1, c2), null)
            else
                Pair(true, Chip(PlayerId("⁰¹²³⁴⁵⁶⁷⁸⁹ⁿ".indexOf(c2)), Level("$c1".toInt())))
    }
}

class SimpleBoard(
    override val width: Int,
    override val height: Int,
    val posMap: MutableMap<Pos, Chip?>
) : Any()
    , Board
    , Serializable
{
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

    fun spawn4symmetricPlayers(x: Int, y: Int) {
        require(hasCell(Pos(x, y)))
        val poss = setOf(
            Pos(x, y), Pos(x, height - 1 - y),
            Pos(width - 1 - x, y), Pos(width - 1 - x, height - 1 - y)
        )
        for ((i, pos) in poss.withIndex())
            posMap[pos] = Chip(PlayerId(i), Level.MAX_STABLE_LEVEL)
    }

    fun spawn4players(margin: Int = 1) {
        require(2 * margin <= width && 2 * margin <= height)
        spawn4symmetricPlayers(margin, margin)
    }

    object Factory : Board.Factory {
        override fun of(emptyBoard: EmptyBoard): SimpleBoard =
            SimpleBoard(emptyBoard)

        fun spawn4players(emptyBoard: EmptyBoard, margin: Int = 1): SimpleBoard =
            SimpleBoard(emptyBoard).apply { spawn4players(margin) }

        override fun fromString(s: String): SimpleBoard {
            val (width, height, map) = unmap(s) { c1, c2 -> readChip(c1, c2) }
            return SimpleBoard(
                width, height,
                map
                    .filterValues { (hasCell, _) -> hasCell }
                    .mapValuesTo(mutableMapOf()) { (_, pair) -> pair.second }
            )
        }
    }

    object Examples {
        val TOWER = Factory.spawn4players(SimpleEmptyBoard.Examples.TOWER)
        val SMALL_TOWER = Factory.spawn4players(SimpleEmptyBoard.Examples.SMALL_TOWER)
        // Default boards from BGC/Clonium
        val DEFAULT_1 = Factory.spawn4players(SimpleEmptyBoard.Examples.DEFAULT_1)
        val DEFAULT_2 = Factory.spawn4players(SimpleEmptyBoard.Examples.DEFAULT_2)
        val DEFAULT_3 = Factory.spawn4players(SimpleEmptyBoard.Examples.DEFAULT_3)
        val DEFAULT_4 = Factory.spawn4players(SimpleEmptyBoard.Examples.DEFAULT_4)
        val DEFAULT_5 = Factory.spawn4players(SimpleEmptyBoard.Examples.DEFAULT_5)
        val ALL = listOf(
            TOWER, SMALL_TOWER,
            DEFAULT_1, DEFAULT_2, DEFAULT_3, DEFAULT_4, DEFAULT_5
        )
    }
}


sealed class Direction {
    object Up : Direction()
    object Right : Direction()
    object Down : Direction()
    object Left : Direction()
}

// NOTE: referencing subclass in superclass static method/field on JVM
//  may lead to deadlock in multi-thread environment
//  (a thread tries loading superclass, while the other one tries loading subclass)
val DIRECTIONS: List<Direction> = listOf(
    Direction.Up,
    Direction.Right,
    Direction.Down,
    Direction.Left
)

/** Single-chip effect of [EvolvingBoard.inc]: [Chip] at [center] decrease [Level] by 4 and
 * 4 transient [Chip]s with [Level1] explode to [up], [right], [down] and [left] */
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

    fun endStateAt(direction: Direction) =
        when (direction) {
            is Direction.Up -> up
            is Direction.Right -> right
            is Direction.Down -> down
            is Direction.Left -> left
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

/** Board with [Chip]s on which [Player]s can make turns ([inc] and [incAnimated]) */
interface EvolvingBoard : Board {
    class InvalidTurn(reason: String) : IllegalArgumentException(reason)

    override fun copy(): EvolvingBoard

    @Throws(InvalidTurn::class)
    /** Increase [Level] at [pos] by 1, then explode all unstable [Chip]s */
    fun inc(pos: Pos)

    @Throws(InvalidTurn::class)
    /** Increase [Level] at [pos] by 1, then explode all unstable [Chip]s while recording [Transition]s */
    fun incAnimated(pos: Pos): Sequence<Transition>

    /** Return new [EvolvingBoard] after [EvolvingBoard.inc] */
    @Throws(InvalidTurn::class)
    fun afterInc(pos: Pos): EvolvingBoard =
        copy().apply { inc(pos) }

    interface Factory : Board.Factory {
        fun of(board: Board): EvolvingBoard
        override fun fromString(s: String): EvolvingBoard
    }
}
