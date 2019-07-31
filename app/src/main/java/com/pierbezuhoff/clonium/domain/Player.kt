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
    object Human : PlayerTactic() {
        override fun toPlayer(playerId: PlayerId) =
            HumanPlayer(playerId)
    }
    sealed class Bot : PlayerTactic() {
        abstract override fun toPlayer(playerId: PlayerId): com.pierbezuhoff.clonium.domain.Bot
        object RandomPicker : Bot() {
            override fun toPlayer(playerId: PlayerId) =
                RandomPickerBot(playerId)
        }
        class LevelMaximizer(val depth: Int) : Bot() {
            override fun toPlayer(playerId: PlayerId) =
                LevelMaximizerBot(playerId, depth)
        }
        class ChipCountMaximizer(val depth: Int) : Bot() {
            override fun toPlayer(playerId: PlayerId) =
                ChipCountMaximizerBot(playerId, depth)
        }
    }

    abstract fun toPlayer(playerId: PlayerId): Player
}

val PLAYER_TACTICS = listOf(
    PlayerTactic.Human,
    PlayerTactic.Bot.RandomPicker
)

