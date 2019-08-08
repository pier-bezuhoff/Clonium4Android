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
    abstract val name: String

    object Human : PlayerTactic() {
        override val name = "Human"
        override fun toPlayer(playerId: PlayerId) =
            HumanPlayer(playerId)
    }
    sealed class Bot : PlayerTactic() {
        abstract override fun toPlayer(playerId: PlayerId): BotPlayer

        object RandomPicker : Bot() {
            override val name = "RandomPicker"
            override fun toPlayer(playerId: PlayerId) =
                RandomPickerBot(playerId)
        }
        class LevelMaximizer(val depth: Int) : Bot() {
            override val name = "LevelMaximizer($depth)"
            override fun toPlayer(playerId: PlayerId) =
                LevelMaximizerBot(playerId, depth)
        }
        class ChipCountMaximizer(val depth: Int) : Bot() {
            override val name = "ChipCountMaximizer($depth)"
            override fun toPlayer(playerId: PlayerId) =
                ChipCountMaximizerBot(playerId, depth)
        }
        class LevelMinimizer(val depth: Int) : Bot() {
            override val name = "LevelMinimizer($depth)"
            override fun toPlayer(playerId: PlayerId) =
                LevelMinimizerBot(playerId, depth)
        }
        class LevelBalancer(val depth: Int, val ratio: Float) : Bot() {
            override val name = "LevelBalancer($depth,$ratio)"
            override fun toPlayer(playerId: PlayerId) =
                LevelBalancerBot(playerId, depth, ratio)
        }
    }

    abstract fun toPlayer(playerId: PlayerId): Player

    override fun toString(): String =
        name

    object Builder {
        fun fromName(name: String): PlayerTactic =
            PLAYER_TACTICS.first { it.name == name }
    }
}

val PLAYER_TACTICS = listOf(
    PlayerTactic.Human,
    PlayerTactic.Bot.RandomPicker,
    PlayerTactic.Bot.LevelMaximizer(1), // NOTE: slow: up to 5s on tower board
    PlayerTactic.Bot.ChipCountMaximizer(1),
    PlayerTactic.Bot.LevelMinimizer(1),
    PlayerTactic.Bot.LevelBalancer(1, 2f)
)

