package com.pierbezuhoff.clonium.models

import com.pierbezuhoff.clonium.domain.PlayerId
import com.pierbezuhoff.clonium.domain.PlayerTactic


// TODO: add chip set (with symmetry), cell image

/** Bundle of game parameters */
data class GameConfig(
    val botMinTime: Long = 300L,
    val gameSpeed: Float = 1f
)

data class PlayerItem(
    val playerId: PlayerId,
    var tactic: PlayerTactic,
    var participate: Boolean
)

data class PlayersConfig(val playerItems: List<PlayerItem>) {
    fun toStringSet(): Set<String> =
        playerItems.withIndex()
            .map { (i, item) ->
                "$i ${item.playerId.id} ${item.tactic.name} ${item.participate}"
            }.toSet()

    object Builder {
        fun fromStringSet(stringSet: Set<String>): PlayersConfig =
            runCatching {
                stringSet
                    .map {
                        val (indexPart, playerIdPart, tacticPart, participatePart) = it.split(' ')
                        val ix = indexPart.toInt()
                        val playerId = PlayerId(playerIdPart.toInt())
                        val tactic = PlayerTactic.Builder.fromName(tacticPart)
                        val participate = participatePart.toBoolean()
                        IndexedValue(ix, PlayerItem(playerId, tactic, participate))
                    }.sortedBy { it.index }
                    .map { it.value }
                    .let { PlayersConfig(it) }
            }.getOrElse {
                it.printStackTrace()
                IllegalArgumentException("Failed to parse Set<String> $stringSet").printStackTrace()
                return@getOrElse PlayersConfig(emptyList())
            }
    }
}
