package com.pierbezuhoff.clonium.models

import android.graphics.*
import android.util.Log
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.times
import androidx.core.graphics.translationMatrix
import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.domain.Chip
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.domain.Pos
import kotlin.math.min

// MAYBE: rotate rectangular board along with view
/** draw [Game] state and animation on [Canvas] */
class GameModel(
    val game: Game,
    private val bitmapLoader: BitmapLoader
) : Game by game {
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    private val cellSize: Int
        get() = min(viewWidth / board.width, viewHeight / board.height)

    private val paint: Paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    fun setSize(width: Int, height: Int) {
        Log.i(TAG, "width = $width, height = $height")
        viewWidth = width
        viewHeight = height
    }

    fun advance(timeDelta: Long) {
        //
    }

    fun drawCurrentBoard(canvas: Canvas) {
        canvas.drawBoard(board)
    }

    private fun Canvas.drawBoard(board: Board) {
        require(viewWidth > 0 && viewHeight > 0)
        drawColor(backgroundColor)
        for (pos in board.asPosSet())
            drawCell(pos)
        for ((pos, maybeChip) in board.asPosMap())
            maybeChip?.let {
                drawChip(pos, it)
            }
    }

    private fun Canvas.drawCell(pos: Pos) {
        val bitmap = bitmapLoader.loadCell()
        val rescaleMatrix = rescaleMatrix(bitmap.width, bitmap.height)
        val translateMatrix = pos2matrix(pos)
        drawBitmap(
            bitmap,
            rescaleMatrix * translateMatrix, // NOTE: order MAY be wrong
            paint
        )
    }

    private fun Canvas.drawChip(pos: Pos, chip: Chip) {
        val bitmap = bitmapLoader.loadChip(chip)
        val rescaleMatrix = rescaleMatrix(bitmap.width, bitmap.height)
        val translateMatrix = pos2matrix(pos)
        drawBitmap(
            bitmap,
            rescaleMatrix * translateMatrix,
            paint
        )
    }

    private fun rescaleMatrix(
        width: Int, height: Int,
        targetWidth: Int = cellSize, targetHeight: Int = cellSize
    ): Matrix =
        scaleMatrix(targetWidth.toFloat() / width, targetHeight.toFloat() / height)

    private fun pos2matrix(pos: Pos): Matrix =
        pos2point(pos).let { translationMatrix(it.x.toFloat(), it.y.toFloat()) }

    private fun pos2point(pos: Pos): Point =
        Point(pos.x * cellSize, pos.y * cellSize)

    companion object {
        private const val TAG = "GameModel"
        private const val backgroundColor: Int = Color.BLACK
    }
}