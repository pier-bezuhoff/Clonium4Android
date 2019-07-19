package com.pierbezuhoff.clonium.models

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.pierbezuhoff.clonium.domain.Chip
import java.io.IOException

interface AssetBitmapLoader {
    fun loadAssetBitmap(path: String): Bitmap
}

interface GameBitmapLoader : AssetBitmapLoader {
    fun loadCell(): Bitmap
    fun loadHighlight(weak: Boolean = false): Bitmap
    fun loadChip(chip: Chip): Bitmap
    fun loadBottomOfChip(chip: Chip): Bitmap
}

abstract class CachingAssetBitmapLoader(private val assetManager: AssetManager) : AssetBitmapLoader {
    private val cache: MutableMap<String, Bitmap> = hashMapOf()

    @Throws(IOException::class)
    override fun loadAssetBitmap(path: String): Bitmap =
        cache.getOrPut(path) {
            Log.i("GameBitmapLoader", "loadAssetBitmap(\"$path\")")
            return@getOrPut BitmapFactory.decodeStream(assetManager.open(path))
        }
}

class GreenGameBitmapLoader(assetManager: AssetManager) : CachingAssetBitmapLoader(assetManager)
    , GameBitmapLoader
{
    override fun loadCell(): Bitmap =
        loadAssetBitmap("cell.png")

    override fun loadHighlight(weak: Boolean): Bitmap {
        val opacity = if (weak) 15 else 25
        return loadAssetBitmap("highlight-$opacity.png")
    }

    override fun loadChip(chip: Chip): Bitmap {
        require(chip.level.ordinal in 1..7)
        return loadAssetBitmap("green_chip_set/g1-${chip.level.ordinal}.png")
    }

    override fun loadBottomOfChip(chip: Chip): Bitmap {
        require(chip.level.ordinal == 1)
        return loadAssetBitmap("green_chip_set/g1-reverse.png")
    }
}

class StandardGameBitmapLoader(assetManager: AssetManager) : CachingAssetBitmapLoader(assetManager)
    , GameBitmapLoader
{
    override fun loadCell(): Bitmap =
        loadAssetBitmap("cell.png")

    override fun loadHighlight(weak: Boolean): Bitmap {
        val opacity = if (weak) 15 else 25
        return loadAssetBitmap("highlight-$opacity.png")
    }

    override fun loadChip(chip: Chip): Bitmap {
        require(chip.level.ordinal in 1..5) { "Temporary limitation, will be extended to 1..7" }
        require(chip.playerId.id in 0..7) { "Temporary limitation" }
        return loadAssetBitmap("chip_set/item${chip.level.ordinal}-${chip.playerId.id + 1}.png")
    }

    override fun loadBottomOfChip(chip: Chip): Bitmap {
        require(chip.level.ordinal == 1)
        return loadChip(chip)
    }
}
