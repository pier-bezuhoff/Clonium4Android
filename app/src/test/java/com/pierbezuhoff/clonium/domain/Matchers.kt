package com.pierbezuhoff.clonium.domain

import io.kotlintest.Matcher
import io.kotlintest.MatcherResult
import io.kotlintest.matchers.Diff
import io.kotlintest.should

infix fun Board.shouldMatchBoard(expected: Board) =
    this should matchBoard(expected)

fun matchBoard(expected: Board): Matcher<Board> =
    BoardMatcher(expected)

private class BoardMatcher(private val expected: Board) : Matcher<Board> {
    override fun test(value: Board): MatcherResult {
        val diff = Diff.create(value.asPosMap(), expected.asPosMap())
        val failureMessage = """
            |
            |Expected:
            |${expected.asString()}
            |should be equal to
            |${value.asString()}
            |but it differs by:
            |${diff.toString(1)}
            |
        """.trimMargin()
        val negatedFailureMessage = """
            |Expected:
            |${expected.asString()}
            |should not be equal to
            |${value.asString()}
            |but equals
            |
        """.trimMargin()
        return MatcherResult(
            value.asString() == expected.asString(),
            failureMessage, negatedFailureMessage
        )
    }

}