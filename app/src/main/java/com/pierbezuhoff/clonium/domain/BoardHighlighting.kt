package com.pierbezuhoff.clonium.domain

sealed class Highlighting {
    sealed class PossibleTurn : Highlighting() {
        object Human : PossibleTurn()
        object Bot : PossibleTurn()
    }
    sealed class LastTurn : Highlighting() {
        object Main : LastTurn()
        object Minor : LastTurn()
    }
    object NextTurn : Highlighting()
}

interface BoardHighlighting {
    val highlightings: Map<Pos, Highlighting>
    fun showHumanPossibleTurns(turns: Set<Pos>)
    fun showBotPossibleTurns(turns: Set<Pos>)
    fun hidePossibleTurns()
    fun showLastTurn(turn: Pos, nPlayers: Int)
    fun hideInterceptedLastTurns(transitions: Sequence<Transition>)
    fun showNextTurn(turn: Pos)
    fun hideNextTurn()
}

class MapBoardHighlighting : BoardHighlighting {
    private val _highlightings: MutableMap<Pos, Highlighting> = mutableMapOf()
    override val highlightings: Map<Pos, Highlighting> = _highlightings
    private var possibleTurns: Set<Pos> = emptySet()
    private var nextTurn: Pos? = null
    private var lastMainTurn: Pos? = null
    private var lastMinorTurns: List<Pos?> = emptyList() // null items are placeholders for intercepted minor turns
    private val lastTurns: List<Pos>
        get() = listOfNotNull(lastMainTurn) + lastMinorTurns.filterNotNull()

    override fun showHumanPossibleTurns(turns: Set<Pos>) {
        _highlightings -= possibleTurns
        possibleTurns = turns
        turns.associateWithTo(_highlightings) { Highlighting.PossibleTurn.Human }
    }

    override fun showBotPossibleTurns(turns: Set<Pos>) {
        _highlightings -= possibleTurns
        possibleTurns = turns
        turns.associateWithTo(_highlightings) { Highlighting.PossibleTurn.Bot }
    }

    override fun hidePossibleTurns() {
        _highlightings -= possibleTurns
        possibleTurns = emptySet()
    }

    override fun showLastTurn(turn: Pos, nPlayers: Int) {
        _highlightings -= listOfNotNull(lastMainTurn)
        _highlightings -= lastMinorTurns.filterNotNull()
        lastMinorTurns = (listOf(lastMainTurn) + (lastMinorTurns - turn))
            .take(maxOf(nPlayers - 1, 0)) // without last main
        lastMainTurn = turn
        lastMinorTurns.filterNotNull().associateWithTo(_highlightings) { Highlighting.LastTurn.Minor }
        _highlightings[turn] = Highlighting.LastTurn.Main
    }

    override fun hideInterceptedLastTurns(transitions: Sequence<Transition>) {
        val boards = transitions.flatMap { sequenceOf(it.interimBoard, it.endBoard) }
        val interceptedByExplosions = lastTurns.filter { pos -> boards.map { it.chipAt(pos) }.toSet().size > 1 }
        hideInterceptedLastTurns(interceptedByExplosions)
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
    }

    override fun hideNextTurn() {
        _highlightings -= listOfNotNull(nextTurn)
        nextTurn = null
    }
}

