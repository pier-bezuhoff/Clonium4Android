package com.pierbezuhoff.clonium.domain

import kotlin.IllegalArgumentException

@Suppress("NOTHING_TO_INLINE")
class PrimitiveBoard private constructor(
    override val width: Int,
    override val height: Int,
    private val chips: IntArray,
    private val ownedIxs: Map<PlayerId, MutableSet<Int>>
) : EvolvingBoard {

    @Suppress("RemoveRedundantQualifierName")
    constructor(board: Board) : this(
        board.width, board.height,
        IntArray(board.width * board.height).apply {
            val proto = PrimitiveBoard(board.width, board.height, intArrayOf(), mutableMapOf())
            for (ix in indices)
                this[ix] = NO_CELL
            for ((pos, maybeChip) in board.asPosMap())
                this[proto.pos2ix(pos)] = proto.chip2int(maybeChip)
        },
        kotlin.run {
            val proto = PrimitiveBoard(board.width, board.height, intArrayOf(), mutableMapOf())
            return@run board.players().associateWith {
                board.possOf(it).map { proto.pos2ix(it) }.toMutableSet() }
        }
    )

    override fun copy(): PrimitiveBoard =
        PrimitiveBoard(width, height, chips.clone(), ownedIxs.mapValues { it.value.toMutableSet() })

    private inline fun validPos(pos: Pos): Boolean =
        (pos.x in 0 until width) && (pos.y in 0 until height)

    private inline fun pos2ix(pos: Pos): Int {
        require(validPos(pos))
        return pos.x + pos.y * width
    }

    private inline fun ix2pos(ix: Int): Pos =
        Pos(ix % width, ix / width)

    private inline fun hasCell(ix: Int): Boolean =
        ix in chips.indices && chips[ix] != NO_CELL

    override fun hasCell(pos: Pos): Boolean {
        return validPos(pos) && hasCell(pos2ix(pos))
    }

    override fun asPosSet(): Set<Pos> {
        val poss = mutableSetOf<Pos>()
        for (ix in chips.indices)
            if (chips[ix] != NO_CELL)
                poss.add(ix2pos(ix))
        return poss
    }
    private inline fun chip2int(maybeChip: Chip?): Int =
        maybeChip?.let { (player, level) ->
            level.ordinal + player.id * MAX_LEVEL_ORDINAL
        } ?: NO_CHIP

    private inline fun int2playerId(value: Int): PlayerId =
        PlayerId(value / MAX_LEVEL_ORDINAL)

    private inline fun int2level(value: Int): Level =
        Level(value % MAX_LEVEL_ORDINAL)

    private inline fun int2chip(value: Int): Chip? =
        when {
            value == NO_CELL || value == NO_CHIP ->
                null
            value < -2 ->
                throw IllegalArgumentException("Impossible to decode $value < -2 to Chip? at board $this")
            value % MAX_LEVEL_ORDINAL == 0 ->
                throw IllegalArgumentException("Impossible to decode $value with level = 0 to Chip? at board $this")
            else ->
                Chip(int2playerId(value), int2level(value))
        }

    private inline fun chipAt(ix: Int): Chip? =
        int2chip(chips[ix])

    private inline fun playerAt(ix: Int): PlayerId? =
        int2chip(chips[ix])?.playerId

    private inline fun levelAt(ix: Int): Level? =
        int2chip(chips[ix])?.level

    override fun chipAt(pos: Pos): Chip? =
        chipAt(pos2ix(pos))

    override fun asPosMap(): Map<Pos, Chip?> {
        val map = mutableMapOf<Pos, Chip?>()
        for (ix in chips.indices)
            chips[ix].let { value ->
                if (value >= -1)
                    map[ix2pos(ix)] = int2chip(value)
            }
        return map
    }

    override fun asInhabitedPosMap(): Map<Pos, Chip> {
        val map = mutableMapOf<Pos, Chip>()
        for (ix in chips.indices)
            chips[ix].let { value ->
                if (value >= 0)
                    map[ix2pos(ix)] = int2chip(value)!!
            }
        return map
    }

    override fun possOf(playerId: PlayerId): Set<Pos> =
        ownedIxs[playerId]?.map { ix2pos(it) }?.toSet() ?: emptySet()

    override fun chipCountOf(playerId: PlayerId): Int =
        ownedIxs[playerId]?.size ?: 0

    override fun isAlive(playerId: PlayerId): Boolean =
        ownedIxs[playerId]?.isNotEmpty() ?: false

    override fun levelOf(playerId: PlayerId): Int {
        return ownedIxs[playerId]?.sumBy { chips[it] % MAX_LEVEL_ORDINAL } ?: 0
    }

    override fun players(): Set<PlayerId> =
        ownedIxs.keys

    private fun hasChip(ix: Int): Boolean =
        hasCell(ix) && chips[ix] != NO_CHIP

    /** Neighbor indices of [ix] with cell */
    private inline fun neighbors(ix: Int): Set<Int> {
        val neighbors = mutableSetOf<Int>()
        if (hasCell(ix - width))
            neighbors.add(ix - width)
        if (hasCell(ix + width))
            neighbors.add(ix + width)
        if (ix % width > 0 && hasCell(ix - 1))
            neighbors.add(ix - 1)
        if (ix % width < width - 1 && hasCell(ix + 1))
            neighbors.add(ix + 1)
        return neighbors
    }

    private inline fun hasUnstableLevel(ix: Int): Boolean =
        int2level(chips[ix]) >= Level.MIN_UNSTABLE_LEVEL

    private inline fun dec4(ix: Int) {
        require(hasUnstableLevel(ix))
        val level = levelAt(ix)!!
        if (level == Level.MIN_UNSTABLE_LEVEL) {
            ownedIxs.getValue(playerAt(ix)!!).remove(ix)
            chips[ix] = NO_CHIP
        } else {
            chips[ix] -= 4
        }
    }

    /** Increase neighbors' levels by 1 */
    private inline fun explodeToNeighbors(ix: Int, playerId: PlayerId) {
        for (i in neighbors(ix)) {
            if (chips[i] == NO_CHIP) {
                chips[i] = chip2int(Chip(playerId, Level1))
                ownedIxs.getValue(playerId).add(i)
            } else {
                val previousOwner = playerAt(i)!!
                if (previousOwner != playerId) {
                    ownedIxs.getValue(previousOwner).remove(i)
                    ownedIxs.getValue(playerId).add(i)
                }
                chips[i] = chip2int(Chip(playerId, chipAt(i)!!.level + 1))
            }
        }
    }

    /** For indices in [ixs]: [dec4] it and [explodeToNeighbors].
     * Return [Set] of changed indices */
    private inline fun explode(ixs: Set<Int>): Set<Int> {
        require(ixs.all { hasUnstableLevel(it) })
        val changed: MutableSet<Int> = mutableSetOf()
        changed.addAll(ixs)
        for (ix in ixs) {
            val player = playerAt(ix)!!
            dec4(ix)
            explodeToNeighbors(ix, player)
            changed.addAll(neighbors(ix))
        }
        return changed
    }

    private inline fun unstableIxs(
        suspiciousIxs: Set<Int> = chips.indices.toSet()
    ): Set<Int> =
        suspiciousIxs.filter { hasUnstableLevel(it) }.toSet()

    // TODO: detect chains and explode whole chains
    private tailrec fun evolve(unstable: Set<Int>) {
        if (unstable.isNotEmpty()) {
            val changed = explode(unstable)
            evolve(unstableIxs(changed))
        }
    }

    private inline fun inc(ix: Int) {
        val level = levelAt(ix)!!
        chips[ix] += 1
        if (level >= Level.MAX_STABLE_LEVEL)
            evolve(setOf(ix))
    }

    override fun inc(pos: Pos) {
        val ix = pos2ix(pos)
        if (!hasCell(ix))
            throw EvolvingBoard.InvalidTurn("There is no cell on $pos in $this")
        if (!hasChip(ix))
            throw EvolvingBoard.InvalidTurn("There is no chip on $pos in $this")
        inc(ix)
    }

    private fun explosionAt(ix: Int): Explosion {
        require(hasChip(ix))
        val player = playerAt(ix)!!
        val up = ix2pos(ix - width)
        val down = ix2pos(ix + width)
        val left = if (ix % width > 0) ix2pos(ix - 1) else Pos(-1, ix2pos(ix).y)
        val right = if (ix % width < width - 1) ix2pos(ix + 1) else Pos(width, ix2pos(ix).y)
        fun endState(pos: Pos): Explosion.EndState =
            if (hasCell(pos)) Explosion.EndState.LAND else Explosion.EndState.FALLOUT
        return Explosion(
            player, center = ix2pos(ix),
            up = endState(up), down = endState(down),
            left = endState(left), right = endState(right)
        )
    }

    /** Pure, return [Transition] of explosions at [ixs] */
    private fun explodeTransition(ixs: Set<Int>): Transition {
        require(ixs.all { hasUnstableLevel(it) })
        val interimState = copy()
        for (ix in ixs)
            interimState.dec4(ix)
        val endState = interimState.copy()
        val explosions = mutableSetOf<Explosion>()
        for (ix in ixs) {
            val player = playerAt(ix)!!
            endState.explodeToNeighbors(ix, player)
            explosions.add(explosionAt(ix))
        }
        return Transition(interimState, endState, explosions)
    }

    private tailrec fun _evolveTransitions(
        unstable: Set<Int>,
        transitions: Sequence<Transition>
    ): Sequence<Transition> {
        return if (unstable.isEmpty())
            transitions
        else {
            val transition = explodeTransition(unstable)
            val changed = explode(unstable)
            _evolveTransitions(unstableIxs(changed), transitions + transition)
        }
    }

    /** Impure, [explode] all unstable chips while recording [Sequence] of [Transition]s*/
    private fun evolveTransitions(unstable: Set<Int>): Sequence<Transition> =
        _evolveTransitions(unstable, emptySequence())

    override fun incAnimated(pos: Pos): Sequence<Transition> {
        val ix = pos2ix(pos)
        if (!hasCell(ix))
            throw EvolvingBoard.InvalidTurn("There is no cell on $pos")
        if (!hasChip(ix))
            throw EvolvingBoard.InvalidTurn("There is no chip on $pos")
        val level = levelAt(ix)!!
        chips[ix] += 1
        return if (level < Level.MAX_STABLE_LEVEL)
            emptySequence()
        else
            evolveTransitions(setOf(ix))
    }

    override fun toString(): String =
        asString()

    override fun equals(other: Any?): Boolean =
        other is Board && other.asString() == asString()

    override fun hashCode(): Int =
        asString().hashCode()

    companion object {
        private const val NO_CELL = -2
        private const val NO_CHIP = -1
        private const val MAX_STABLE_LEVEL_ORDINAL = Level.MAX_STABLE_LEVEL_ORDINAL
        private const val MIN_UNSTABLE_LEVEL_ORDINAL = Level.MIN_UNSTABLE_LEVEL_ORDINAL
        private const val MAX_LEVEL_ORDINAL = Level.MAX_LEVEL_ORDINAL
    }

    object Builder : Board.Builder, EvolvingBoard.Builder {
        override fun of(emptyBoard: EmptyBoard): PrimitiveBoard =
            PrimitiveBoard(SimpleBoard(emptyBoard))

        override fun of(board: Board): PrimitiveBoard =
            PrimitiveBoard(board)

        override fun fromString(s: String): PrimitiveBoard =
            of(SimpleBoard.Builder.fromString(s))
    }
}