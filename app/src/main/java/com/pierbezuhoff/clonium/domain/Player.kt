package com.pierbezuhoff.clonium.domain

interface Player {
    val playerId: PlayerId
}

class HumanPlayer(override val playerId: PlayerId) : Player

