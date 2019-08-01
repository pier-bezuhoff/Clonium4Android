package com.pierbezuhoff.clonium.domain

import java.io.Serializable

interface Player {
    val playerId: PlayerId
    val tactic: PlayerTactic
}

class HumanPlayer(override val playerId: PlayerId) : Player {
    override val tactic = PlayerTactic.Human
    override fun toString(): String =
        "HumanPlayer($playerId)"
}

sealed class PlayerTactic : Serializable {
    object Human : PlayerTactic() {
        override fun toPlayer(playerId: PlayerId) =
            HumanPlayer(playerId)
    }
    sealed class Bot : PlayerTactic() {
        abstract override fun toPlayer(playerId: PlayerId): com.pierbezuhoff.clonium.domain.BotPlayer
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
        class LevelMinimizer(val depth: Int) : Bot() {
            override fun toPlayer(playerId: PlayerId) =
                LevelMinimizerBot(playerId, depth)
        }
        class LevelBalancer(val depth: Int, val ratio: Float) : Bot() {
            override fun toPlayer(playerId: PlayerId) =
                LevelBalancerBot(playerId, depth, ratio)
        }
    }

    abstract fun toPlayer(playerId: PlayerId): Player
}

val PLAYER_TACTICS = listOf(
    PlayerTactic.Human,
    PlayerTactic.Bot.RandomPicker,
    PlayerTactic.Bot.LevelMaximizer(1), // NOTE: slow: up to 5s on tower board
    PlayerTactic.Bot.ChipCountMaximizer(1),
    PlayerTactic.Bot.LevelMinimizer(1),
    PlayerTactic.Bot.LevelBalancer(1, 2f)
)

