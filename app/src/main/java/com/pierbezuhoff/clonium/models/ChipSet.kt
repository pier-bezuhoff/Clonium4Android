package com.pierbezuhoff.clonium.models

import com.pierbezuhoff.clonium.domain.Chip
import com.pierbezuhoff.clonium.domain.Level1
import com.pierbezuhoff.clonium.domain.PlayerId
import com.pierbezuhoff.clonium.utils.impossibleCaseOf

private typealias ColorId = Int
private typealias LevelOrdinal = Int
private typealias BitmapPath = String

interface ChipSet {
    val name: String
    /** symmetry of [Chip] with [Level1] */
    val symmetry: ChipSymmetry
    val nColors: Int
    val levelRange: IntRange
    val defaultColorPrism: ColorPrism

    fun pathOfChip(colorPrism: ColorPrism = defaultColorPrism, chip: Chip): BitmapPath
    fun pathOfChipBottom(colorPrism: ColorPrism = defaultColorPrism, chip: Chip): BitmapPath
    fun mkRandomColorPrism(): ColorPrism
}

sealed class ChipSymmetry {
    object None : ChipSymmetry()
    /** invariant to 180-rotation */
    object Two : ChipSymmetry()
    /** invariant to 90-rotation */
    object Four : ChipSymmetry()
}

/** Remapping from player ids to color ids */
interface ColorPrism {
    val colors: Map<PlayerId, ColorId>

    fun player2color(playerId: PlayerId): ColorId? =
        colors[playerId]

    fun asString(): String =
        colors.entries
            .joinToString(separator = ", ") { (playerId, colorId) -> "${playerId.id}: $colorId" }
}

open class MapColorPrism(override val colors: Map<PlayerId, ColorId>) : ColorPrism

class MutableMapColorPrism(override val colors: MutableMap<PlayerId, ColorId>) : MapColorPrism(colors) {
    object Builder {
        fun of(prism: ColorPrism): MutableMapColorPrism =
            MutableMapColorPrism(prism.colors.toMutableMap())

        fun fromString(s: String): MutableMapColorPrism =
            MutableMapColorPrism(s.split(", ")
                .associate {
                    val (playerPart, colorPart) = it.split(": ")
                    PlayerId(playerPart.trim().toInt()) to colorPart.trim().toInt()
                }.toMutableMap()
            )
    }
}

fun colorPrismOf(colorIds: Iterable<ColorId>): MapColorPrism =
    MapColorPrism(
        colorIds
            .withIndex()
            .associate { (i, colorId) -> PlayerId(i) to colorId }
    )

// MAYBE: change into final class + lambdas as params
abstract class CommonChipSet(
    final override val name: String,
    final override val symmetry: ChipSymmetry,
    final override val nColors: Int,
    final override val levelRange: IntRange
) : ChipSet {
    override val defaultColorPrism =
        colorPrismOf(0 until nColors)

    abstract fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath

    protected fun mkPathIn(dirName: String, colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        "$dirName/$colorId-$levelOrdinal.png"

    open fun mkBottomPath(colorId: ColorId): BitmapPath =
        mkPath(colorId, 1)

    protected fun mkBottomPathIn(dirName: String, colorId: ColorId): BitmapPath =
        "$dirName/$colorId-bottom.png"

    final override fun pathOfChip(colorPrism: ColorPrism, chip: Chip): BitmapPath {
        val (playerId, level) = chip
        val maybeColorId = colorPrism.player2color(playerId)
        when {
            maybeColorId == null -> throw IllegalArgumentException(
                "ColorPrism $colorPrism cannot find color id for $playerId (in chip $chip), ChipSet $this has only $nColors colors"
            )
            level.ordinal !in levelRange -> throw IllegalArgumentException(
                "ChipSet $this does not have $level (in chip $chip), Level should be in ${levelRange.first}..${levelRange.last}"
            )
            else -> {
                val colorId: ColorId = maybeColorId
                return mkPath(colorId, level.ordinal)
            }
        }
    }

    final override fun pathOfChipBottom(colorPrism: ColorPrism, chip: Chip): BitmapPath {
        require(chip.level == Level1)
        val (playerId, _) = chip
        when (val maybeColorId = colorPrism.player2color(playerId)) {
            null -> throw IllegalArgumentException(
                "ColorPrism $colorPrism cannot find color id for $playerId (in chip $chip), ChipSet $this has only $nColors colors"
            )
            else -> {
                return mkBottomPath(maybeColorId)
            }
        }
    }

    final override fun mkRandomColorPrism(): ColorPrism =
        MapColorPrism(
            (0 until nColors)
                .shuffled()
                .withIndex()
                .associate { (i, colorId) ->
                    PlayerId(i) to colorId
                }
        )

    object Builder {
        val variants: Map<String, CommonChipSet> =
            setOf(
                StandardChipSet,
                GreenChipSet,
                StarChipSet,
                WhiteStarChipSet,
                MinecraftChipSet,
                CircuitChipSet
            ).associateBy { it.name }

        fun of(name: String) =
            variants[name] ?: throw IllegalArgumentException("Cannot find ChipSet with name = \"$name\"")
    }
}

object StandardChipSet : CommonChipSet(
    name = "standard",
    symmetry = ChipSymmetry.Four,
    nColors = 8,
    levelRange = 1..5
) {
    override fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        mkPathIn("standard_chip_set", colorId, levelOrdinal)
}

object GreenChipSet : CommonChipSet(
    name = "green",
    symmetry = ChipSymmetry.Two,
    nColors = 8,
    levelRange = 0..7
) {
    override fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        mkPathIn("green_chip_set", colorId, levelOrdinal)
    override fun mkBottomPath(colorId: ColorId): BitmapPath =
        mkBottomPathIn("green_chip_set", colorId)
}

object StarChipSet : CommonChipSet(
    name = "star",
    symmetry = ChipSymmetry.Four,
    nColors = 8,
    levelRange = 0..7
) {
    override val defaultColorPrism =
        colorPrismOf(setOf(7, 2, 5, 6, 1, 3, 0, 4))

    override fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        mkPathIn("star_chip_set", colorId, levelOrdinal)
    override fun mkBottomPath(colorId: ColorId): BitmapPath =
        mkBottomPathIn("star_chip_set", colorId)
}

object WhiteStarChipSet : CommonChipSet(
    name = "white_star",
    symmetry = ChipSymmetry.Four,
    nColors = 8,
    levelRange = 0..7
) {
    override val defaultColorPrism =
        colorPrismOf(setOf(7, 2, 5, 6, 1, 3, 0, 4))

    override fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        mkPathIn("star_white_chip_set", colorId, levelOrdinal)
    override fun mkBottomPath(colorId: ColorId): BitmapPath =
        mkBottomPathIn("star_white_chip_set", colorId)
}

object MinecraftChipSet : CommonChipSet(
    name = "minecraft",
    symmetry = ChipSymmetry.None,
    nColors = 9,
    levelRange = 1..7
) {
    override fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        "minecraft_chip_set/${color2dir(colorId)}/$levelOrdinal.png"

    private fun color2dir(colorId: ColorId): String =
        when (colorId) {
            0 -> "common"
            1 -> "skelet"
            2 -> "city"
            3 -> "end"
            4 -> "steve"
            5 -> "animals"
            6 -> "water"
            7 -> "fire"
            8 -> "zombie"
            else -> impossibleCaseOf(colorId)
        }
}

object CircuitChipSet : CommonChipSet(
    name = "circuit",
    symmetry = ChipSymmetry.Four,
    nColors = 8,
    levelRange = 0..7
) {
    override fun mkPath(colorId: ColorId, levelOrdinal: LevelOrdinal): BitmapPath =
        mkPathIn("circuit_chip_set", colorId, levelOrdinal)
}