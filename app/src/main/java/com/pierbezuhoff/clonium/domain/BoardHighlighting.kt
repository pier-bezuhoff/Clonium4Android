package com.pierbezuhoff.clonium.domain

import kotlin.math.max

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
    val lastTurns: List<Pos>
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
        lastMinorTurns = (listOfNotNull(lastMainTurn) + lastMinorTurns).take(max(nPlayers - 2, 0)) // without main last and current
        lastMainTurn = turn
        lastMinorTurns.associateWithTo(_highlightings) { Highlighting.LastTurn.Minor }
        _highlightings[turn] = Highlighting.LastTurn.Main
    }

    fun hideInterceptedLastTurns(transition: Transition) {
        hideInterceptedLastTurns(sequenceOf(transition))
    }

    fun hideInterceptedLastTurns(transitions: Sequence<Transition>) {
        val boards = transitions.flatMap { sequenceOf(it.interimBoard, it.endBoard) }
        val interceptedByExplosions = lastTurns.filter { pos -> boards.map { it.chipAt(pos) }.toSet().size > 1 }
        hideInterceptedLastTurns(interceptedByExplosions)
    }

    fun hideInterceptedLastTurns(turns: List<Pos>) {
        require(lastTurns.containsAll(turns))
        _highlightings -= turns
        lastMainTurn = lastMainTurn.takeUnless { it in turns }
        lastMinorTurns = lastMinorTurns.filterNot { it in turns }
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

