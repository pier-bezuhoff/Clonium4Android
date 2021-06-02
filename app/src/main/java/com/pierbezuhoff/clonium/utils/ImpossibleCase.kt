package com.pierbezuhoff.clonium.utils

/*
* Usage: in impossible 'else' branch of 'case-of' expression
* */
class ImpossibleCase(message: String) : IllegalStateException(message)

@Suppress("FunctionName")
fun ImpossibleCaseOf(whenArg: Any?): ImpossibleCase =
    ImpossibleCase("Impossible case of $whenArg")

fun <A> impossibleCaseOf(whenArg: A, beforeThrowing: (A) -> Unit = {}): Nothing {
    beforeThrowing(whenArg)
    throw ImpossibleCaseOf(whenArg)
}