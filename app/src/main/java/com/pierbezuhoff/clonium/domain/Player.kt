package com.pierbezuhoff.clonium.domain

import java.io.Serializable

interface Player {
    val playerId: PlayerId
    val tactic: PlayerTactic
}

class HumanPlayer(override val playerId: PlayerId) : Player {
    override val tactic = PlayerTactic.Human
}

sealed class PlayerTactic : Serializable {
    object Human : PlayerTactic()
    sealed class Bot : PlayerTactic() {
        object RandomPicker : Bot()
        class LevelMaximizer(val depth: Int) : Bot()
        class ChipCountMaximizer(val depth: Int) : Bot()
    }

    fun toPlayer(playerId: PlayerId): Player =
        when (this) {
            Human -> HumanPlayer(playerId)
            Bot.RandomPicker -> RandomPickerBot(playerId)
            is Bot.LevelMaximizer -> LevelMaximizerBot(playerId, depth)
            is Bot.ChipCountMaximizer -> ChipCountMaximizerBot(playerId, depth)
        }
}

val PLAYER_TACTICS = listOf(
    PlayerTactic.Human,
    PlayerTactic.Bot.RandomPicker
)

