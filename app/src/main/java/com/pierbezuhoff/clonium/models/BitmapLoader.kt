package com.pierbezuhoff.clonium.models

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pierbezuhoff.clonium.domain.Chip
import java.io.IOException

class BitmapLoader(private val assetManager: AssetManager) {
    private val cache: MutableMap<String, Bitmap> = hashMapOf()

    @Throws(IOException::class)
    fun loadAssetBitmap(path: String): Bitmap =
        cache.getOrPut(path) {
            BitmapFactory.decodeStream(assetManager.open(path))
        }

    fun loadCell(): Bitmap =
        loadAssetBitmap("cell.png")

    fun loadChip(chip: Chip): Bitmap {
        require(chip.level.ordinal in 1..5) { "Temporary limitation, will be extended to 1..7" }
        require(chip.playerId.id in 0..8) { "Temporary limitation" }
        return loadAssetBitmap("chip_set/item${chip.level.ordinal}-${chip.playerId.id}.png")
    }
}