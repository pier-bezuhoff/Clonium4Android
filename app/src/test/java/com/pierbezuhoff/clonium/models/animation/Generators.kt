@file:Suppress("TestFunctionName")

package com.pierbezuhoff.clonium.models.animation

import io.kotlintest.properties.Gen

fun <A> ConstAdvancer(result: A, duration: Milliseconds, blocking: Boolean): Advancer<A> =
    object : Advancer<A>(duration) {
        override val blocking: Boolean = blocking
        override fun advance(timeDelta: Milliseconds): A =
            result
    }

fun <A> LambdaAdvancer(duration: Milliseconds, blocking: Boolean, getResult: (Progress) -> A): Advancer<A> =
    object : Advancer<A>(duration) {
        override val blocking: Boolean = blocking
        override fun advance(timeDelta: Milliseconds): A = getResult(progress)
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