package com.pierbezuhoff.clonium.models

// TODO: add chip set (with symmetry), cell image
/** Bundle of game parameters */
data class GameConfig(
    val botMinTime: Long = 300L,
    val gameSpeed: Float = 1f
)