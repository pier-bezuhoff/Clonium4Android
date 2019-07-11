package com.pierbezuhoff.clonium.domain

import java.lang.Exception

@Suppress("NOTHING_TO_INLINE")
class PrimitiveBoard(
    override val width: Int,
    override val height: Int,
    private val chips: IntArray
) : Any()
    , EmptyBoard
    , Board
    , EvolvingBoard
{
    fun copy(): PrimitiveBoard =
        PrimitiveBoard(width, height, chips.clone())

    private inline fun pos2ix(pos: Pos): Int =
        pos.x + pos.y * width

    private inline fun ix2pos(ix: Int): Pos =
        Pos(ix % width, ix / width)

    private fun hasCell(ix: Int): Boolean =
        ix in chips.indices && chips[ix] != NO_CELL

    override fun hasCell(pos: Pos): Boolean =
        hasCell(pos2ix(pos))

    override fun asPosSet(): Set<Pos> {
        val poss = mutableSetOf<Pos>()
        for (ix in chips.indices)
            if (chips[ix] != NO_CELL)
                poss.add(ix2pos(ix))
        return poss
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

    private fun chipAt(ix: Int): Chip? =
        int2chip(chips[ix])

    private fun playerAt(ix: Int): Player? =
        int2chip(chips[ix])?.player

    private fun levelAt(ix: Int): Level? =
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

    /** 4 neighbor indices of [ix] */
    private fun ix4(ix: Int): Set<Int> =
        setOf(ix - width, ix - 1, ix + 1, ix + width)

    private fun hasChip(ix: Int): Boolean =
        hasCell(ix) && chips[ix] != NO_CHIP

    /** Neighbor indices of [ix] with [Cell] */
    private fun neighbors(ix: Int): Set<Int> =
        ix4(ix).filter(this::hasCell).toSet()

    private fun hasUnstableLevel(ix: Int): Boolean =
        levelAt(ix)?.let { it >= Level.MIN_UNSTABLE_LEVEL } == true

    private fun dec4(ix: Int) {
        require(hasUnstableLevel(ix))
        val level = levelAt(ix)!!
        if (level == Level.MIN_UNSTABLE_LEVEL)
            chips[ix] = NO_CHIP
        else
            chips[ix] -= 4
    }

    /** Increase neighbors' levels by 1 */
    private fun explodeToNeighbors(ix: Int, player: Player) {
        for (i in neighbors(ix))
            if (chips[i] == NO_CHIP)
                chips[i] = chip2int(Chip(player, Level(1)))
            else
                chips[i] = chip2int(Chip(player, chipAt(i)!!.level + 1))
    }

    /** For indices in [ixs]: [dec4] it and [explodeToNeighbors].
     * Return [Set] of changed indices */
    private fun explode(ixs: Set<Int>): Set<Int> {
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

    private fun explosionAt(ix: Int): Explosion {
        require(hasChip(ix))
        val player = playerAt(ix)!!
        val upIx = ix - width
        val downIx = ix + width
        val leftIx = ix - 1
        val rightIx = ix + 1
        fun endState(ix: Int): ExplosionEndState =
            if (hasCell(ix)) ExplosionEndState.LAND else ExplosionEndState.FALLOUT
        return Explosion(
            player, center = ix2pos(ix),
            up = endState(upIx), down = endState(downIx),
            left = endState(leftIx), right = endState(rightIx)
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

    private fun unstableIxs(suspiciousIxs: Set<Int> = chips.indices.toSet()): Set<Int> =
        suspiciousIxs.filter { hasUnstableLevel(it) }.toSet()

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

    override fun inc(pos: Pos): Sequence<Transition> {
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

    companion object {
        private const val NO_CELL = -2
        private const val NO_CHIP = -1
    }
}