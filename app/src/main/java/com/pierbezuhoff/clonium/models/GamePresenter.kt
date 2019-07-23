package com.pierbezuhoff.clonium.models

import android.graphics.*
import androidx.core.graphics.rotationMatrix
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.times
import androidx.core.graphics.translationMatrix
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.animation.TransitionsAnimatedAdvancer
import kotlin.math.*

/** Draw [Game] state and animations on [Canvas] */
interface GamePresenter : SpatialBoard {
    val game: Game
    val animatedAdvancer: TransitionsAnimatedAdvancer?
    fun startTransitions(transitions: Sequence<Transition>)
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
    val jumpHeight: Float // 1f
    val falloutSpeed: Float
    val falloutAngleSpeed: Float // 720f

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
    fun pos2point(pos: Pos): Point
    fun pointf2pos(point: PointF): Pos
}

// MAYBE: rotate rectangular board along with view
// MAYBE: cut out SpatialBoard interface
class SimpleGamePresenter(
    override val game: Game,
    private val bitmapLoader: GameBitmapLoader
) : Any()
    , GamePresenter
{
    override var animatedAdvancer: TransitionsAnimatedAdvancer? = null

    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    override val cellSize: Int
        get() = with(game.board) {
            min(viewWidth / width, viewHeight / height)
        }

    private val paint: Paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private var weakHighlight: Boolean = false
    private var highlighted: Set<Pos> = emptySet()

    override fun setSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    override fun Canvas.draw() {
        require(viewWidth > 0 && viewHeight > 0)
        animatedAdvancer?.apply {
            if (!blocking)
                drawGameBoard()
            draw()
        } ?: drawGameBoard()
    }

    private fun Canvas.drawGameBoard() =
        drawBoard(game.board)

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
        val rescaleMatrix = rescaleMatrix(bitmap.width, bitmap.height)
        val translateMatrix = pos2matrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * rescaleMatrix,
            paint
        )
    }

    private fun Canvas.drawHighlight(pos: Pos) {
        val bitmap = bitmapLoader.loadHighlight(weak = weakHighlight)
        val rescaleMatrix = rescaleMatrix(bitmap.width, bitmap.height)
        val translateMatrix = pos2matrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * rescaleMatrix,
            paint
        )
    }

    private fun Canvas.drawChip(pos: Pos, chip: Chip) {
        val bitmap = bitmapLoader.loadChip(chip)
        val rescaleMatrix = rescaleMatrix(bitmap.width, bitmap.height)
        val centeredScaleMatrix = centeredScaleMatrix(bitmap.width, bitmap.height, CHIP_CELL_RATIO)
        val translateMatrix = pos2matrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * rescaleMatrix * centeredScaleMatrix,
            paint
        )
    }

    override fun startTransitions(transitions: Sequence<Transition>) {
        // NOTE: leaky leak of SimpleGamePresenter
        animatedAdvancer = TransitionsAnimatedAdvancer(transitions, this, bitmapLoader)
    }

    private fun rescaleMatrix(
        width: Int, height: Int,
        targetWidth: Int = cellSize, targetHeight: Int = cellSize
    ): Matrix =
        scaleMatrix(targetWidth.toFloat() / width, targetHeight.toFloat() / height)

    private fun centeredScaleMatrix(
        width: Int, height: Int,
        scaleX: Float, scaleY: Float = scaleX
    ): Matrix =
        Matrix().apply {
            postScale(
                scaleX, scaleY,
                width / 2f, height / 2f
            )
        }

    private fun pos2matrix(pos: Pos): Matrix =
        pos2point(pos).let { translationMatrix(it.x.toFloat(), it.y.toFloat()) }

    override fun highlight(poss: Set<Pos>, weak: Boolean) {
        highlighted = poss
        weakHighlight = weak
    }

    override fun pos2point(pos: Pos): Point =
        Point(pos.x * cellSize, pos.y * cellSize)

    override fun pointf2pos(point: PointF): Pos =
        Pos((point.x / cellSize).toInt(), (point.y / cellSize).toInt())

    companion object {
        private const val TAG = "GamePresenter"
        private const val BACKGROUND_COLOR: Int = Color.BLACK
        private const val CHIP_CELL_RATIO: Float = 0.9f
        private const val Z_ZOOM: Double = 2e-1
        private const val EXPLOSION_ANIMATION_DURATION = 1_000L
        private const val EXPLOSION_PAUSE_DURATION = 500L
        private const val FALLOUT_ANIMATION_DURATION = 4_000L
        private const val FALLOUT_N_CYCLES = 2
        private const val FALLOUT_FALLING_VELOCITY = 2f
    }
}
}