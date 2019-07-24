package com.pierbezuhoff.clonium.models.animation

import io.kotlintest.shouldBe

fun <A> Advancer<A>.shouldAdvanceTo(
    timeDelta: Milliseconds,
    expected: A? = null,
    blocking: Boolean? = null, ended: Boolean? = null
) {
    advance(timeDelta).let { actual ->
        expected?.let { actual shouldBe it }
        blocking?.let { this.blocking shouldBe it }
        ended?.let { this.ended shouldBe it }
    }
}
