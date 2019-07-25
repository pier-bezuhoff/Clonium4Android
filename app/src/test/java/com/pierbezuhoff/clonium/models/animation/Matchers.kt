package com.pierbezuhoff.clonium.models.animation

import io.kotlintest.Matcher
import io.kotlintest.Result
import io.kotlintest.should

fun <A> Advancer<A>.shouldAdvanceTo(
    timeDelta: Milliseconds,
    expected: A? = null,
    blocking: Boolean? = null, ended: Boolean? = null
) = this should AdvanceToMatcher(timeDelta, expected, blocking, ended)

private class AdvanceToMatcher<A>(
    private val timeDelta: Milliseconds,
    private val expected: A? = null,
    private val blocking: Boolean? = null, private val ended: Boolean? = null
) : Matcher<Advancer<A>> {
    override fun test(value: Advancer<A>): Result {
        val actual = value.advance(timeDelta)
        val resultMatches = expected?.let { actual == it } ?: true
        val resultFailureMessage = if (!resultMatches)
            "advance result should be equal to $expected but it is $actual\n" else ""
        val blockingMatches = blocking?.let { value.blocking == it } ?: true
        val blockingFailureMessage = if (!blockingMatches)
            "blocking should be $blocking but it is ${value.blocking}\n" else ""
        val endedMatches = ended?.let { value.ended == it } ?: true
        val endedFailureMessage = if (!endedMatches)
            "ended should be $ended but it is ${value.ended}\n" else ""
        val failureMessage = """
            |
            |$resultFailureMessage$blockingFailureMessage$endedFailureMessage
            |present state: $value
            |
        """.trimMargin()
        val negatedFailureMessage = """
            |
            |advance result should not be $actual
            |or blocking should not be ${value.blocking}
            |or ended should not be ${value.ended}
            |
        """.trimMargin()
        return Result(
            resultMatches && blockingMatches && endedMatches,
            failureMessage, negatedFailureMessage
        )
    }
}