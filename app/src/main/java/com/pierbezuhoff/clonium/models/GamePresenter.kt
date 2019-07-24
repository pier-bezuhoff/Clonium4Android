package com.pierbezuhoff.clonium.models

import android.graphics.*
import android.util.Log
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.times
import androidx.core.graphics.translationMatrix
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.animation.Advanceable
import com.pierbezuhoff.clonium.models.animation.Milliseconds
import com.pierbezuhoff.clonium.models.animation.TransitionsAnimatedAdvancer
import kotlin.math.*

/** Draw [Game] state and animations on [Canvas] */
interface GamePresenter : SpatialBoard {
    val game: Game
    val animatedAdvancer: TransitionsAnimatedAdvancer?
    val bitmapPaint: Paint
    val blocking: Boolean
    // BUG: does not work!
    fun freezeBoard()
    fun unfreezeBoard()
    fun startTransitions(transitions: Sequence<Transition>)
    fun advance(timeDelta: Milliseconds)
    fun Canvas.draw()
    fun Canvas.drawBoard(board: Board)
    fun highlight(poss: Set<Pos>, weak: Boolean = false)
    fun unhighlight() =
        highlight(emptySet())
}

interface SpatialBoard {
    /** Cell width and height */
    val cellSize: Int
    /** How much smaller get a chip with when it's higher */
    val zZoom: Double
    /** How much a chip smaller than a cell */
    val chipCellRatio: Float
    val jumpHeight: Float
    val falloutVerticalSpeed: Float
    val falloutAngleSpeed: Float

    /** Set target `View` size */
    fun setSize(width: Int, height: Int)
    fun rescaleMatrix(bitmap: Bitmap, targetWidth: Int = cellSize, targetHeight: Int = cellSize): Matrix =
        scaleMatrix(
            targetWidth.toFloat() / bitmap.width,
            targetHeight.toFloat() / bitmap.height
        )
    fun centeredScaleMatrix(bitmap: Bitmap, scaleX: Float, scaleY: Float = scaleX): Matrix =
        Matrix().apply {
            postScale(
                scaleX, scaleY,
                bitmap.width / 2f, bitmap.height / 2f
            )
        }
    fun centeredRotateMatrix(bitmap: Bitmap, degrees: Float): Matrix =
        Matrix().apply {
            postRotate(degrees, bitmap.width/2f, bitmap.height/2f)
        }
    fun pos2point(pos: Pos): Point =
        Point(pos.x * cellSize, pos.y * cellSize)
    fun pointf2pos(point: PointF): Pos =
        Pos((point.x / cellSize).toInt(), (point.y / cellSize).toInt())
    fun pos2translationMatrix(pos: Pos): Matrix =
        pos2point(pos).let { translationMatrix(it.x.toFloat(), it.y.toFloat()) }
}

private class SimpleSpatialBoard(private val game: Game) : SpatialBoard {
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    override val cellSize: Int
        get() = with(game.board) {
            min(viewWidth / width, viewHeight / height)
        }

    override val chipCellRatio: Float = 0.9f
    override val zZoom: Double = 0.2 // 20% per [cellSize]
    override val jumpHeight: Float = 1f // in [cellSize]s
    override val falloutVerticalSpeed: Float = 2f
    override val falloutAngleSpeed: Float = 2f * 360

    override fun setSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }
}

// MAYBE: rotate rectangular board along with view
class SimpleGamePresenter(
    override val game: Game,
    private val bitmapLoader: GameBitmapLoader
) : Any()
    , SpatialBoard by SimpleSpatialBoard(game)
    , GamePresenter
{
    // TODO: advancer pool because of lasting fallout animations
    override var animatedAdvancer: TransitionsAnimatedAdvancer? = null
    override val blocking: Boolean
        get() = animatedAdvancer?.blocking ?: false

    override val bitmapPaint: Paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private var board: Board = game.board
    private var weakHighlight: Boolean = false
    private var highlighted: Set<Pos> = emptySet()

    override fun advance(timeDelta: Milliseconds) {
        animatedAdvancer?.let {
            it.advance(timeDelta)
            if (it.ended || !it.blocking)
//                unfreezeBoard()
            if (it.ended)
                animatedAdvancer = null
        }
    }

    override fun Canvas.draw() {
        require(cellSize > 0) { "setSize must be called before draw" }
        animatedAdvancer?.apply {
            if (!blocking)
                drawGameBoard()
            draw()
        } ?: drawGameBoard()
    }

    private fun Canvas.drawGameBoard() =
        drawBoard(board)

    override fun Canvas.drawBoard(board: Board) {
        drawColor(BACKGROUND_COLOR)
        for (pos in board.asPosSet())
            drawCell(pos)
        for (pos in highlighted)
            drawHighlight(pos)
        for ((pos, maybeChip) in board.asPosMap())
            maybeChip?.let {
                drawChip(pos, it)
            }
    }

    private fun Canvas.drawCell(pos: Pos) {
        val bitmap = bitmapLoader.loadCell()
        val rescaleMatrix = rescaleMatrix(bitmap)
        val translateMatrix = pos2translationMatrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * rescaleMatrix,
            bitmapPaint
        )
    }

    private fun Canvas.drawHighlight(pos: Pos) {
        val bitmap = bitmapLoader.loadHighlight(weak = weakHighlight)
        val rescaleMatrix = rescaleMatrix(bitmap)
        val translateMatrix = pos2translationMatrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * rescaleMatrix,
            bitmapPaint
        )
    }

    private fun Canvas.drawChip(pos: Pos, chip: Chip) {
        val bitmap = bitmapLoader.loadChip(chip)
        val rescaleMatrix = rescaleMatrix(bitmap)
        val centeredScaleMatrix = centeredScaleMatrix(bitmap, chipCellRatio)
        val translateMatrix = pos2translationMatrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * rescaleMatrix * centeredScaleMatrix,
            bitmapPaint
        )
    }

    override fun freezeBoard() {
        board = game.board.copy()
    }

    override fun unfreezeBoard() {
        board = game.board
    }

    override fun startTransitions(transitions: Sequence<Transition>) {
        // NOTE: leaky leak of SimpleGamePresenter (circular reference)
        if (transitions.iterator().hasNext()) {
            animatedAdvancer = TransitionsAnimatedAdvancer(transitions, this, bitmapLoader)
        }
    }

    override fun highlight(poss: Set<Pos>, weak: Boolean) {
        highlighted = poss
        weakHighlight = weak
    }

    companion object {
        private const val TAG = "GamePresenter"
        private const val BACKGROUND_COLOR: Int = Color.BLACK
    }
}