package com.pierbezuhoff.clonium.models

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pierbezuhoff.clonium.domain.PlayerId
import com.pierbezuhoff.clonium.domain.PlayerTactic
import com.pierbezuhoff.clonium.models.animation.ChipAnimation

/** Bundle of game parameters */
data class GameConfig(
    val botMinTime: Long = 500L,
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
                "$i; ${item.playerId.id}; ${item.tactic.name}; ${item.participate}"
            }.toSet()

    object Builder {
        fun fromStringSet(stringSet: Set<String>): PlayersConfig =
            runCatching {
                stringSet
                    .map {
                        val (indexPart, playerIdPart, tacticPart, participatePart) = it.split(';')
                        val ix = indexPart.trim().toInt()
                        val playerId = PlayerId(playerIdPart.trim().toInt())
                        val tactic = PlayerTactic.Builder.fromName(tacticPart.trim())
                        val participate = participatePart.trim().toBoolean()
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

var SharedPreferences.playersConfig: PlayersConfig
    get() =
        PlayersConfig.Builder.fromStringSet(
            getStringSet(PlayersConfig::class.simpleName, null) ?: mutableSetOf()
        )
    set(value) {
        edit {
            putStringSet(PlayersConfig::class.simpleName, value.toStringSet())
        }
    }

data class ChipsConfig(
    val chipAnimation: ChipAnimation,
    val chipSet: ChipSet,
    val colorPrism: ColorPrism = chipSet.getDefaultColorPrism()
)

var SharedPreferences.chipAnimation: ChipAnimation
    get() =
        getString(ChipAnimation::class.simpleName, null)
            ?.let(ChipAnimation::valueOf)
            ?: ChipAnimation.ROTATION
    set(value) {
        edit {
            putString(ChipAnimation::class.simpleName, value.name)
        }
    }

var SharedPreferences.chipSet: ChipSet
    get() =
        getString(ChipSet::class.simpleName, null)
            ?.let(ChipSet.Builder::of)
            ?: GreenChipSet
    set(value) {
        edit {
            putString(ChipSet::class.simpleName, value.name)
        }
    }

var SharedPreferences.colorPrism: ColorPrism?
    get() =
        getString(ColorPrism::class.simpleName, null)
            ?.let(MutableMapColorPrism.Factory::fromString)
    set(value) {
        edit {
            putString(ColorPrism::class.simpleName, value?.asString())
        }
    }

var SharedPreferences.chipsConfig: ChipsConfig
    get() = ChipsConfig(chipAnimation, chipSet, colorPrism ?: chipSet.getDefaultColorPrism())
    set(value) {
        chipAnimation = value.chipAnimation
        chipSet = value.chipSet
        colorPrism = value.colorPrism
    }
