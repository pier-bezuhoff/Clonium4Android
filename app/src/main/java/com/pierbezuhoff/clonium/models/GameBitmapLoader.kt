package com.pierbezuhoff.clonium.models

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.pierbezuhoff.clonium.domain.Chip
import com.pierbezuhoff.clonium.domain.Highlighting
import java.io.IOException

interface AssetBitmapLoader {
    fun loadAssetBitmap(path: String): Bitmap
}

interface GameBitmapLoader : AssetBitmapLoader {
    fun loadCell(): Bitmap
    fun loadChip(chip: Chip): Bitmap
    fun loadBottomOfChip(chip: Chip): Bitmap
    fun loadHighlighting(highlighting: Highlighting): Bitmap
}

open class CachingAssetBitmapLoader(private val assetManager: AssetManager) : AssetBitmapLoader {
    private val cache: MutableMap<String, Bitmap> = hashMapOf()

    @Throws(IOException::class)
    override fun loadAssetBitmap(path: String): Bitmap =
        cache.getOrPut(path) {
            Log.i("GameBitmapLoader", "loadAssetBitmap(\"$path\")")
            return@getOrPut BitmapFactory.decodeStream(assetManager.open(path))
        }
}

abstract class CommonGameBitmapLoader(assetManager: AssetManager) : CachingAssetBitmapLoader(assetManager)
    , GameBitmapLoader
{
    override fun loadCell(): Bitmap =
        loadAssetBitmap("cells/dark-blue.png")

    override fun loadHighlighting(highlighting: Highlighting): Bitmap =
        loadAssetBitmap(
            when (highlighting) {
                Highlighting.PossibleTurn.Human -> "highlights/highlight-strong.png"
                Highlighting.PossibleTurn.Bot -> "highlights/highlight-weak.png"
                Highlighting.LastTurn.Main -> "highlights/last-turn-main.png"
                Highlighting.LastTurn.Minor -> "highlights/last-turn-minor.png"
                Highlighting.NextTurn -> "highlights/next-turn.png"
            }
        )
}

class StandardGameBitmapLoader(assetManager: AssetManager) : CommonGameBitmapLoader(assetManager)
    , GameBitmapLoader
{
    override fun loadChip(chip: Chip): Bitmap {
        require(chip.level.ordinal in 1..5) { "Temporary limitation, will be extended to 1..7" }
        require(chip.playerId.id in 0..7) { "Temporary limitation" }
        return loadAssetBitmap("chip_set/item${i1(chip)}-${i2(chip)}.png")
    }

    override fun loadBottomOfChip(chip: Chip): Bitmap {
        require(chip.level.ordinal == 1)
        return loadChip(chip)
    }

    private fun i1(chip: Chip): String =
        "${chip.level.ordinal}"

    private fun i2(chip: Chip): String =
        "${chip.playerId.id + 1}"
}

class GreenGameBitmapLoader(assetManager: AssetManager) : CommonGameBitmapLoader(assetManager)
    , GameBitmapLoader
{
    override fun loadChip(chip: Chip): Bitmap {
        require(chip.level.ordinal in 1..7)
        require(chip.playerId.id in 0..5) { "Temporary limitation, will be extended to 0..7" }
        return loadAssetBitmap("green_chip_set/g${i1(chip)}-${i2(chip)}.png")
    }

    override fun loadBottomOfChip(chip: Chip): Bitmap {
        require(chip.level.ordinal == 1)
        return loadAssetBitmap("green_chip_set/g${i1(chip)}-0.png")
    }

    private fun i1(chip: Chip): String =
        "${chip.playerId.id + 1}"

    private fun i2(chip: Chip): String =
        "${chip.level.ordinal}"
}

class StarGameBitmapLoader(assetManager: AssetManager) : CommonGameBitmapLoader(assetManager)
    , GameBitmapLoader
{
    override fun loadChip(chip: Chip): Bitmap {
        require(chip.level.ordinal in 1..7)
        require(chip.playerId.id in 0..7)
        return loadAssetBitmap("star_chip_set/chip-${i1(chip)}-${i2(chip)}.png")
    }

    override fun loadBottomOfChip(chip: Chip): Bitmap {
        require(chip.level.ordinal == 1)
        require(chip.playerId.id in 0..7)
        return loadAssetBitmap("star_chip_set/chip-${i1(chip)}-0.png")
    }

    private fun i1(chip: Chip): String =
        "${chip.playerId.id + 1}"

    private fun i2(chip: Chip): String =
        "${chip.level.ordinal}"
}

