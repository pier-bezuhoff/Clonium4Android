package com.pierbezuhoff.clonium.models.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BoardEntity(
    @PrimaryKey val name: String,
    val width: Int,
    val height: Int,
    val chips: IntArray
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
                other is BoardEntity &&
                name == other.name &&
                width == other.width && height == other.height &&
                chips.contentEquals(other.chips)

    override fun hashCode(): Int { // autogen
        var result = name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + chips.contentHashCode()
        return result
    }
}

// TODO: add chip set entity
// TODO: add entity "save"
