package com.pierbezuhoff.clonium.models.db

import androidx.room.TypeConverter
import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.domain.PrimitiveBoard
import com.pierbezuhoff.clonium.models.ChipSymmetry
import com.pierbezuhoff.clonium.models.ColorPrism
import com.pierbezuhoff.clonium.models.MutableMapColorPrism
import com.pierbezuhoff.clonium.models.animation.ChipAnimation
import com.pierbezuhoff.clonium.utils.impossibleCaseOf

class Converters {
    @TypeConverter
    fun saveBoard(board: Board?): Triple<Int, Int, IntArray>? {
        if (board == null) return null
        val primitiveBoard = PrimitiveBoard.Factory.of(board)
        return Triple(board.width, board.height, primitiveBoard.chips)
    }

    @TypeConverter
    fun loadBoard(triple: Triple<Int, Int, IntArray>?): Board? {
        if (triple == null) return null
        val (width, height, chips) = triple
        return PrimitiveBoard(width, height, chips)
    }

    @TypeConverter
    fun saveChipSymmetry(chipSymmetry: ChipSymmetry?): Int? =
        when (chipSymmetry) {
            ChipSymmetry.None -> 0
            ChipSymmetry.Two -> 2
            ChipSymmetry.Four -> 4
            null -> null
        }

    @TypeConverter
    fun loadChipSymmetry(i: Int?): ChipSymmetry? =
        when (i) {
            0 -> ChipSymmetry.None
            2 -> ChipSymmetry.Two
            4 -> ChipSymmetry.Four
            null -> null
            else -> impossibleCaseOf(i)
        }

    @TypeConverter
    fun saveIntRange(intRange: IntRange?): Pair<Int, Int>? =
        intRange?.let { it.first to it.last }

    @TypeConverter
    fun loadIntRange(startEnd: Pair<Int, Int>?): IntRange? =
        startEnd?.let { it.first .. it.second }

    @TypeConverter
    fun saveColorPrism(colorPrism: ColorPrism?): String? =
        colorPrism?.asString()

    @TypeConverter
    fun loadColorPrism(s: String?): ColorPrism? =
        s?.let(MutableMapColorPrism.Factory::fromString)

    @TypeConverter
    fun saveChipAnimation(chipAnimation: ChipAnimation?): Int? =
        when (chipAnimation) {
            ChipAnimation.ROTATION -> 0
            ChipAnimation.SLIDE -> 1
            null -> null
        }

    @TypeConverter
    fun loadChipAnimation(i: Int?): ChipAnimation? =
        when (i) {
            0 -> ChipAnimation.ROTATION
            1 -> ChipAnimation.SLIDE
            null -> null
            else -> impossibleCaseOf(i)
        }
}
