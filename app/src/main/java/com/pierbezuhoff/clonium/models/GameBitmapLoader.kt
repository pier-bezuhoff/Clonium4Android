package com.pierbezuhoff.clonium.models

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pierbezuhoff.clonium.domain.*

interface AssetBitmapLoader {
    fun loadAssetBitmap(path: String): Bitmap
}

interface GameBitmapLoader : AssetBitmapLoader {
    fun loadCell(): Bitmap
    fun loadChip(chipSet: ChipSet, colorPrism: ColorPrism = chipSet.customColorPrism, chip: Chip): Bitmap
    fun loadRawChip(chipSet: ChipSet, colorId: Int, level: Level): Bitmap
    fun loadBottomOfChip(chipSet: ChipSet, colorPrism: ColorPrism = chipSet.customColorPrism, chip: Chip): Bitmap
    fun loadHighlighting(highlighting: Highlighting): Bitmap
    fun loadMadeTurn(): Bitmap
}

open class CachingAssetBitmapLoader(private val assetManager: AssetManager) : AssetBitmapLoader {
    private val cache: MutableMap<String, Bitmap> = hashMapOf()

    override fun loadAssetBitmap(path: String): Bitmap =
        cache.getOrPut(path) {
            return@getOrPut BitmapFactory.decodeStream(assetManager.open(path))
        }
}

class CommonGameBitmapLoader(assetManager: AssetManager) : CachingAssetBitmapLoader(assetManager)
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

    override fun loadMadeTurn(): Bitmap =
        loadAssetBitmap("highlights/made-turn.png")

    override fun loadChip(chipSet: ChipSet, colorPrism: ColorPrism, chip: Chip): Bitmap =
        loadAssetBitmap(chipSet.pathOfChip(colorPrism, chip))

    override fun loadRawChip(chipSet: ChipSet, colorId: Int, level: Level): Bitmap =
        loadAssetBitmap(chipSet.pathOfChip(chip = Chip(PlayerId(colorId), level)))

    override fun loadBottomOfChip(chipSet: ChipSet, colorPrism: ColorPrism, chip: Chip): Bitmap =
        loadAssetBitmap(chipSet.pathOfChipBottom(colorPrism, chip))
}

