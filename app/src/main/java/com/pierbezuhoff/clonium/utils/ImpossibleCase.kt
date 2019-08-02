package com.pierbezuhoff.clonium.utils

class ImpossibleCase(message: String) : IllegalStateException(message)

fun impossibleCaseOf(whenArg: Any?): Nothing =
    throw ImpossibleCase("Impossible case of $whenArg")