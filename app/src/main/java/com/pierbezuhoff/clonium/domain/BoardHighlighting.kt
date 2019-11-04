package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.Once

sealed class Highlighting {
    sealed class LastTurn : Highlighting() {
        object Main : LastTurn()
        object Minor : LastTurn()
    }
    sealed class PossibleTurn : Highlighting() {
        object Human : PossibleTurn()
        object Bot : PossibleTurn()
    }
    object NextTurn : Highlighting()
}

data class PosHighlighting(
    val last: Highlighting.LastTurn?,
    val possible: Highlighting.PossibleTurn?,
    val next: Highlighting.NextTurn?
) {
    val all: List<Highlighting> = listOfNotNull(last, possible, next)
    val isEmpty: Boolean = last == null && possible == null && next == null

    override fun toString(): String {
        val l = when (last) {
            Highlighting.LastTurn.Main -> "L"
            Highlighting.LastTurn.Minor -> "l"
            null -> " "
        }
        val p = when (possible) {
            Highlighting.PossibleTurn.Human -> "H"
            Highlighting.PossibleTurn.Bot -> "B"
            null -> " "
        }
        val n = when (next) {
            Highlighting.NextTurn -> "*"
            else -> " "
        }
        return n + p + l
    }
}

interface BoardHighlighting {
    val generation: Int
    val highlightings: Map<Pos, PosHighlighting>
    fun showHumanPossibleTurns(turns: Set<Pos>)
    fun showBotPossibleTurns(turns: Set<Pos>)
    fun hidePossibleTurns()
    fun showLastTurn(turn: Pos, nPlayers: Int)
    fun hideInterceptedLastTurns(transitions: Sequence<Transition>)
    fun showNextTurn(turn: Pos)
    fun hideNextTurn()

    fun asString(): String {
        val poss = highlightings.keys
        val minX = poss.map { it.x }.min()!!
        val maxX = poss.map { it.x }.max()!!
        val xs = minX..maxX
        val minY = poss.map { it.y }.min()!!
        val maxY = poss.map { it.y }.max()!!
        val ys = minY..maxY
        return buildString {
            val header = xs.joinToString(prefix = "x>", postfix = "<x") { x -> " $x " }
            appendln(header)
            for (y in ys) {
                appendln(
                    xs.joinToString(prefix = "$y|", postfix = "|$y") { x ->
                        highlightings[Pos(x, y)].toString()
                    }
                )
            }
            appendln(header)
        }
    }
}

class MapBoardHighlighting : BoardHighlighting {
    override var generation: Int = 0
        private set
    private val _highlightings: MutableMap<Pos, PosHighlighting> = mutableMapOf()
    override val highlightings: Map<Pos, PosHighlighting> = _highlightings
    private var possibleTurns: Set<Pos> = emptySet()
    private var nextTurn: Pos? = null
    private val firstLastTurn by Once(true)
    private var lastMainTurn: Pos? = null
    private var lastMinorTurns: List<Pos?> = emptyList() // null items are placeholders for intercepted minor turns
    private val lastTurns: List<Pos>
        get() = listOfNotNull(lastMainTurn) + lastMinorTurns.filterNotNull()

    override fun showHumanPossibleTurns(turns: Set<Pos>) {
        _highlightings -= possibleTurns
        possibleTurns = turns
        turns.associateWithTo(_highlightings) { Highlighting.PossibleTurn.Human }
        generation++
    }

    override fun showBotPossibleTurns(turns: Set<Pos>) {
        _highlightings -= possibleTurns
        possibleTurns = turns
        turns.associateWithTo(_highlightings) { Highlighting.PossibleTurn.Bot }
        generation++
    }

    override fun hidePossibleTurns() {
        _highlightings -= possibleTurns
        possibleTurns = emptySet()
        generation++
    }

    override fun showLastTurn(turn: Pos, nPlayers: Int) {
        _highlightings -= listOfNotNull(lastMainTurn)
        _highlightings -= lastMinorTurns.filterNotNull()
        if (!firstLastTurn)
            lastMinorTurns = (listOf(lastMainTurn) + (lastMinorTurns - turn))
                .take(maxOf(nPlayers - 2, 0)) // without last main and current
        lastMainTurn = turn
        lastMinorTurns.filterNotNull().associateWithTo(_highlightings) { Highlighting.LastTurn.Minor }
        _highlightings[turn] = Highlighting.LastTurn.Main
        generation++
    }

    override fun hideInterceptedLastTurns(transitions: Sequence<Transition>) {
        val boards = transitions.flatMap { sequenceOf(it.interimBoard, it.endBoard) }
        val interceptedByExplosions = lastTurns.filter { pos -> boards.map { it.chipAt(pos) }.toSet().size > 1 }
        hideInterceptedLastTurns(interceptedByExplosions)
        generation++
    }

    private fun hideInterceptedLastTurns(intercepted: Collection<Pos>) {
        require(lastTurns.containsAll(intercepted))
        _highlightings -= intercepted
        lastMainTurn = lastMainTurn.takeUnless { it in intercepted }
        lastMinorTurns = lastMinorTurns.map { it?.takeUnless { it in intercepted } }
    }

    override fun showNextTurn(turn: Pos) {
        _highlightings -= listOfNotNull(nextTurn)
        nextTurn = turn
        _highlightings[turn] = Highlighting.NextTurn
        generation++
    }

    override fun hideNextTurn() {
        _highlightings -= listOfNotNull(nextTurn)
        nextTurn = null
        generation++
    }
}

