package com.pierbezuhoff.clonium.domain

class LinkedBoard private constructor(
    override val width: Int,
    override val height: Int,
    private val roots: Set<LinkedCell>
) : EvolvingBoard {

    override fun asPosSet(): Set<Pos> =
        roots.flatMap { it.component() }
            .map { it.pos }
            .toSet()

    override fun copy(): LinkedBoard =
        LinkedBoard(width, height, roots.map { TODO("copy component") }.toSet())
}

private data class LinkedCell(
    val pos: Pos,
    var chip: Chip,
    /** establish tree-like structure, [children] isSubsetOf [neighbors] */
    val children: List<LinkedCell>,
    val right: LinkedCell? = null,
    val up: LinkedCell? = null,
    val left: LinkedCell? = null,
    val down: LinkedCell? = null
) {
    override fun hashCode(): Int =
        pos.hashCode()

    override fun equals(other: Any?): Boolean =
        (other as? LinkedCell)?.let { it.pos == pos } ?: false

    fun neighbors(): List<LinkedCell> =
        listOfNotNull(right, up, left, down)

    fun component(): Set<LinkedCell> =
        _component(emptySet(), listOf(this))

    private tailrec fun _component(part: Set<LinkedCell>, border: List<LinkedCell>): Set<LinkedCell> =
        if (border.isEmpty())
            part
        else
            _component(part + border, border.flatMap { it.neighbors() } - part)
}

