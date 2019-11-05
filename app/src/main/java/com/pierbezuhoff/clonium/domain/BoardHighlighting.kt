package com.pierbezuhoff.clonium.domain

import com.pierbezuhoff.clonium.utils.MultiMapDelegate
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

    fun toShortString(): String =
        when (this) {
            LastTurn.Main -> "L"
            LastTurn.Minor -> "l"
            PossibleTurn.Human -> "H"
            PossibleTurn.Bot -> "B"
            NextTurn -> "*"
        }
}

interface BoardHighlighting : Map<Pos, List<Highlighting>> {
    val generation: Int // inc per change
    fun showHumanPossibleTurns(turns: Set<Pos>)
    fun showBotPossibleTurns(turns: Set<Pos>)
    fun hidePossibleTurns()
    fun showLastTurn(turn: Pos, nPlayers: Int)
    fun hideInterceptedLastTurns(transitions: Sequence<Transition>)
    fun showNextTurn(turn: Pos)
    fun hideNextTurn()

    override fun toString(): String
    fun asString(): String {
        val poss = keys
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
                        val hs = get(Pos(x, y)).orEmpty()
                        var state = 0
                        hs.joinToString(separator = "") { h ->
                            when (h) {
                                is Highlighting.NextTurn -> {
                                    state = 1
                                    h.toShortString()
                                }
                                is Highlighting.PossibleTurn -> {
                                    val spaces = " ".repeat(1 - state)
                                    state = 2
                                    spaces + h.toShortString()
                                }
                                is Highlighting.LastTurn -> {
                                    val spaces = " ".repeat(2 - state)
                                    state = 3
                                    spaces + h.toShortString()
                                }
                            }
                        } + " ".repeat(3 - state)
                    }
                )
            }
            appendln(header)
        }
    }
}

class MapBoardHighlighting(
    private val nextTurnsMap: MutableMap<Pos, Highlighting.NextTurn> = mutableMapOf(),
    private val possibleTurnsMap: MutableMap<Pos, Highlighting.PossibleTurn> = mutableMapOf(),
    private val lastTurnsMap: MutableMap<Pos, Highlighting.LastTurn> = mutableMapOf()
) : Any()
    , Map<Pos, List<Highlighting>> by MultiMapDelegate(nextTurnsMap, possibleTurnsMap, lastTurnsMap)
    , BoardHighlighting
{
    override var generation: Int = 0
        private set
    private val firstLastTurn by Once(true)
    private var lastMainTurn: Pos? = null
    private var lastMinorTurns: List<Pos?> = emptyList() // `null` items are placeholders for intercepted minor turns
    private val lastTurns: List<Pos>
        get() = listOfNotNull(lastMainTurn) + lastMinorTurns.filterNotNull()

    override fun showHumanPossibleTurns(turns: Set<Pos>) {
        possibleTurnsMap.clear()
        turns.associateWithTo(possibleTurnsMap) { Highlighting.PossibleTurn.Human }
        generation++
    }

    override fun showBotPossibleTurns(turns: Set<Pos>) {
        possibleTurnsMap.clear()
        turns.associateWithTo(possibleTurnsMap) { Highlighting.PossibleTurn.Bot }
        generation++
    }

    override fun hidePossibleTurns() {
        possibleTurnsMap.clear()
        generation++
    }

    override fun showLastTurn(turn: Pos, nPlayers: Int) {
        lastTurnsMap -= listOfNotNull(lastMainTurn)
        lastTurnsMap -= lastMinorTurns.filterNotNull()
        if (!firstLastTurn)
            lastMinorTurns = (listOf(lastMainTurn) + (lastMinorTurns - turn))
                .take(maxOf(nPlayers - 2, 0)) // without last main and current
        lastMainTurn = turn
        lastMinorTurns.filterNotNull().associateWithTo(lastTurnsMap) { Highlighting.LastTurn.Minor }
        lastTurnsMap[turn] = Highlighting.LastTurn.Main
        generation++
    }

    override fun hideInterceptedLastTurns(transitions: Sequence<Transition>) {
        val boards = transitions.flatMap { sequenceOf(it.interimBoard, it.endBoard) }
        val interceptedByExplosions = lastTurns.filter { pos ->
            boards.mapTo(mutableSetOf()) { it.chipAt(pos) }.size > 1
        }
        hideInterceptedLastTurns(interceptedByExplosions)
        generation++
    }

    private fun hideInterceptedLastTurns(intercepted: Collection<Pos>) {
        require(lastTurns.containsAll(intercepted))
        lastTurnsMap -= intercepted
        lastMainTurn = lastMainTurn.takeUnless { it in intercepted }
        lastMinorTurns = lastMinorTurns.map { it?.takeUnless { it in intercepted } }
    }

    override fun showNextTurn(turn: Pos) {
        nextTurnsMap.clear()
        nextTurnsMap[turn] = Highlighting.NextTurn
        generation++
    }

    override fun hideNextTurn() {
        nextTurnsMap.clear()
        generation++
    }

    override fun toString(): String =
        asString()
}

