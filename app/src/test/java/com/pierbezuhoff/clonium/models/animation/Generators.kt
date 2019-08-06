@file:Suppress("TestFunctionName")

package com.pierbezuhoff.clonium.models.animation

import com.pierbezuhoff.clonium.utils.Milliseconds
import io.kotlintest.properties.Gen

fun <A> ConstAdvancer(result: A, duration: Milliseconds, blocking: Boolean): Advancer<A> =
    object : Advancer<A>(duration, if (blocking) duration else 0L) {
        override val blocking: Boolean = blocking
        override fun advance(timeDelta: Milliseconds): A {
            elapse(timeDelta)
            return result
        }
        override fun toString(): String =
            "ConstAdvancer($result) [${super.toString()}]"
    }

fun <A> LambdaAdvancer(duration: Milliseconds, blocking: Boolean, getResult: (Progress) -> A): Advancer<A> =
    object : Advancer<A>(duration, if (blocking) duration else 0L) {
        override val blocking: Boolean = blocking
        override fun advance(timeDelta: Milliseconds): A {
            elapse(timeDelta)
            return getResult(progress)
        }
    }

fun <A> ConstAdvancerGenerator(
    results: List<A>,
    minDuration: Milliseconds = 1L,
    maxDuration: Milliseconds = 10_000L
): Gen<Advancer<A>> =
    Gen.choose(minDuration, maxDuration)
        .map { duration ->
            val result = Gen.from(results).random().first()
            val blocking = Gen.bool().random().first()
            ConstAdvancer(result, duration, blocking)
        }

fun BooleanAdvancerGenerator(
    minDuration: Milliseconds = 1L,
    maxDuration: Milliseconds = 10_000L
): Gen<Advancer<Boolean>> = ConstAdvancerGenerator(
    listOf(true, false),
    minDuration, maxDuration
)

fun BooleanPackGenerator(): Gen<AdvancerPack<Boolean>> =
    Gen.list(BooleanAdvancerGenerator())
        .map { AdvancerPack(it) }