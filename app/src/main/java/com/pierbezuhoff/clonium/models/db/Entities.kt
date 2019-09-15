package com.pierbezuhoff.clonium.models.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.models.ChipSet
import com.pierbezuhoff.clonium.models.ChipSymmetry
import com.pierbezuhoff.clonium.models.ColorPrism
import com.pierbezuhoff.clonium.models.animation.ChipAnimation

@Entity
data class BoardEntity(
    @PrimaryKey val name: String,
    val board: Board
) : Board by board

// also: cell type
@Entity
data class ChipSetEntity(
    @PrimaryKey override val name: String,
    override val symmetry: ChipSymmetry,
    override val nColors: Int,
    override val levelRange: IntRange,
    override val hasBottoms: Boolean,
    override val customColorPrism: ColorPrism?,
    val chipAnimation: ChipAnimation
) : ChipSet

// TODO: add entity "save"
