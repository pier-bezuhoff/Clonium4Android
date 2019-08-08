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

class BoardHighlighting {
    private val _highlightings: MutableMap<Pos, Highlighting> = mutableMapOf()
    val highlightings: Map<Pos, Highlighting> = _highlightings
    private var possibleTurns: Set<Pos> = emptySet()
    private var nextTurn: Pos? = null
    private var lastMainTurn: Pos? = null
    private var lastMinorTurns: List<Pos> = emptyList()
    private val lastTurns: List<Pos>
        get() = listOfNotNull(lastMainTurn) + lastMinorTurns

    fun showHumanPossibleTurns(turns: Set<Pos>) {
        _highlightings -= possibleTurns
        possibleTurns = turns
        turns.associateWithTo(_highlightings) { Highlighting.PossibleTurn.Human }
    }

    fun showBotPossibleTurns(turns: Set<Pos>) {
        _highlightings -= possibleTurns
        possibleTurns = turns
        turns.associateWithTo(_highlightings) { Highlighting.PossibleTurn.Bot }
    }

    fun hidePossibleTurns() {
        _highlightings -= possibleTurns
        possibleTurns = emptySet()
    }

    fun showLastTurn(turn: Pos, nPlayers: Int) {
        _highlightings -= listOfNotNull(lastMainTurn)
        _highlightings -= lastMinorTurns
        lastMinorTurns = (listOfNotNull(lastMainTurn) + (lastMinorTurns - turn))
            .take(maxOf(nPlayers - 2, 0)) // without main last and current
        lastMainTurn = turn
        lastMinorTurns.associateWithTo(_highlightings) { Highlighting.LastTurn.Minor }
        _highlightings[turn] = Highlighting.LastTurn.Main
    }

    fun hideInterceptedLastTurns(transitions: Sequence<Transition>) {
        val boards = transitions.flatMap { sequenceOf(it.interimBoard, it.endBoard) }
        val interceptedByExplosions = lastTurns.filter { pos -> boards.map { it.chipAt(pos) }.toSet().size > 1 }
        hideInterceptedLastTurns(interceptedByExplosions)
    }

    private fun hideInterceptedLastTurns(intercepted: Collection<Pos>) {
        require(lastTurns.containsAll(intercepted))
        _highlightings -= intercepted
        lastMainTurn = lastMainTurn.takeUnless { it in intercepted }
        lastMinorTurns = lastMinorTurns.filterNot { it in intercepted }
    }

    fun showNextTurn(turn: Pos) {
        _highlightings -= listOfNotNull(nextTurn)
        nextTurn = turn
        _highlightings[turn] = Highlighting.NextTurn
    }

    fun hideNextTurn() {
        _highlightings -= listOfNotNull(nextTurn)
        nextTurn = null
    }
}

