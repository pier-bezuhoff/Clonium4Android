package com.pierbezuhoff.clonium.models

import android.graphics.*
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.times
import androidx.core.graphics.translationMatrix
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.animation.*
import com.pierbezuhoff.clonium.utils.Milliseconds
import kotlin.math.*

interface SpatialBoard {
    /** Margin of [SpatialBoard] in [cellSize]s */
    val margin: Float
    /** Cell width and height */
    val cellSize: Int
    /** How much larger get a chip with when it's higher (+ [zZoom]*100% of [cellSize]) */
    val zZoom: Double
    /** How much a chip smaller than a cell */
    val chipCellRatio: Float
    /** Height of explosion jump in [cellSize]s */
    val jumpHeight: Float
    /** Fallout max depth in [cellSize]s */
    val falloutVerticalSpeed: Float
    /** 360 * (# of full turnaround) */
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
        Point(((pos.x + margin) * cellSize).roundToInt(), ((pos.y + margin) * cellSize).roundToInt())
    fun pointf2pos(point: PointF): Pos =
        Pos((point.x / cellSize - margin).toInt(), (point.y / cellSize - margin).toInt())
    fun pos2translationMatrix(pos: Pos): Matrix =
        pos2point(pos).let { translationMatrix(it.x.toFloat(), it.y.toFloat()) }
}

private class SimpleSpatialBoard(
    private val board: Board,
    override val margin: Float
) : SpatialBoard {
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    override val cellSize: Int
        get() = with(board) {
            min(
                viewWidth / (margin + width + margin),
                viewHeight / (margin + height + margin)
            ).roundToInt()
        }

    override val chipCellRatio: Float = 0.9f
    override val zZoom: Double = 0.2 // 20% per [cellSize]
    override val jumpHeight: Float = 1f // in [cellSize]s
    override val falloutVerticalSpeed: Float = 3f
    override val falloutAngleSpeed: Float = 4f * 360

    override fun setSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }
}


interface BoardPresenter : SpatialBoard {
    var board: Board
    val boardHighlighting: BoardHighlighting
    val bitmapPaint: Paint
    fun draw(canvas: Canvas) {
        canvas.drawBoard(board)
    }
    fun Canvas.drawBoard(board: Board)

    interface Builder {
        fun of(board: Board, margin: Float = 0f): BoardPresenter
    }
}

// MAYBE: rotate rectangular board along with view
class SimpleBoardPresenter(
    override var board: Board,
    private val bitmapLoader: GameBitmapLoader,
    margin: Float = 0f
) : Any()
    , SpatialBoard by SimpleSpatialBoard(board, margin)
    , BoardPresenter
{
    override val boardHighlighting: BoardHighlighting = BoardHighlighting()
    override val bitmapPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun Canvas.drawBoard(board: Board) {
        drawColor(BACKGROUND_COLOR)
        for (pos in board.asPosSet())
            drawCell(pos)
        for ((pos, highlighting) in boardHighlighting.highlightings)
            drawBitmapAt(bitmapLoader.loadHighlighting(highlighting), pos)
        for ((pos, maybeChip) in board.asPosMap())
            maybeChip?.let { drawChip(pos, it) }
    }

    private fun Canvas.drawCell(pos: Pos) {
        drawBitmapAt(bitmapLoader.loadCell(), pos)
    }

    private inline fun Canvas.drawBitmapAt(bitmap: Bitmap, pos: Pos) {
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

    companion object {
        private const val BACKGROUND_COLOR: Int = Color.BLACK
    }

    class Builder(
        private val bitmapLoader: GameBitmapLoader
    ) : BoardPresenter.Builder {
        override fun of(board: Board, margin: Float): BoardPresenter =
            SimpleBoardPresenter(board, bitmapLoader, margin)
    }
}


/** Draw [Game] state and animations on [Canvas] */
interface GamePresenter : BoardPresenter, TransitionAnimationsHost {
    val game: Game
    fun advance(timeDelta: Milliseconds)
    /** Copy [game]'s [Board]: make [board] unaffected by [game] changes */
    fun freezeBoard()
    /** Show [game]'s [Board]: immediately reflect any [game] changes */
    fun unfreezeBoard()
    /** Start [Transition]s animations */
    fun startTransitions(transitions: Sequence<Transition>)
    /** Draw current [game] state with animations */
    override fun draw(canvas: Canvas)

    interface Builder {
        fun of(game: Game, margin: Float = 0f): GamePresenter
    }
}

class SimpleGamePresenter(
    override val game: Game,
    private val bitmapLoader: GameBitmapLoader,
    private val symmetry: ChipSymmetry,
    transitionsHost: TransitionAnimationsHost,
    margin: Float = 1f
) : Any()
    , BoardPresenter by SimpleBoardPresenter(game.board, bitmapLoader, margin)
    , TransitionAnimationsHost by transitionsHost
    , GamePresenter
{
    override var board: Board = game.board

    override fun advance(timeDelta: Milliseconds) {
        advanceAnimations(timeDelta)
    }

    object Draw
    override fun draw(canvas: Canvas) {
        require(cellSize > 0) { "setSize must be called before draw" }
        synchronized(Draw) {
            if (!blocking)
                canvas.drawBoard(board)
            drawAnimations(canvas)
        }
    }

    // BUG: SOMETIMES we can see future of explosion (transition.endBoard) when first turn
    // BUG: blinking after turn
    override fun freezeBoard() {
        board = game.board.copy()
    }

    override fun unfreezeBoard() {
        board = game.board
    }

    override fun startTransitions(transitions: Sequence<Transition>) {
        // NOTE: leaky leak of SimpleGamePresenter (circular reference)
        // MAYBE: use WeakRef
        boardHighlighting.hideInterceptedLastTurns(transitions)
        startAdvancer(
            TransitionsAnimatedAdvancer(
                transitions = transitions,
                symmetry = symmetry,
                gamePresenter = this,
                bitmapLoader = bitmapLoader
            )
        )
    }

    class Builder(
        private val bitmapLoader: GameBitmapLoader,
        private val symmetry: ChipSymmetry,
        private val transitionsHost: TransitionAnimationsHost
    ) : GamePresenter.Builder {
        override fun of(game: Game, margin: Float): GamePresenter =
            SimpleGamePresenter(game, bitmapLoader, symmetry, transitionsHost, margin)
    }
}