package com.pierbezuhoff.clonium.models

import android.graphics.*
import androidx.annotation.ColorInt
import androidx.core.graphics.scale
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.times
import androidx.core.graphics.translationMatrix
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.animation.*
import com.pierbezuhoff.clonium.utils.*
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
    val falloutAngularSpeed: Float
    /** 360 * (# of full turnarounds) */
    val madeTurnMeanAngularSpeed: Float
    /** +(it * 100%) per lifetime */
    val madeTurnGrowth: Float

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

private open class SimpleSpatialBoard(
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
    override val falloutAngularSpeed: Float = 4f * 360
    override val madeTurnGrowth: Float = 0.2f
    override val madeTurnMeanAngularSpeed: Float = 1 * 360f

    override fun setSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }
}


interface BoardPresenter : SpatialBoard, BoardHighlighting {
    var board: Board
    val bitmapPaint: Paint
    fun draw(canvas: Canvas) {
        canvas.drawBoard(board)
    }
    fun Canvas.drawBoard(board: Board)
    fun invalidateBoard()

    interface Factory {
        fun of(
            board: Board,
            chipSet: ChipSet,
            colorPrism: ColorPrism = chipSet.getDefaultColorPrism(),
            margin: Float = 0f
        ): BoardPresenter
    }
}

private typealias CellSize = Int
private typealias Size = Pair<Int, Int>
private typealias Highlightings = Map<Pos, Highlighting>
// MAYBE: rotate rectangular board along with view
open class SimpleBoardPresenter(
    final override var board: Board,
    private val boardHighlighting: BoardHighlighting,
    protected val bitmapLoader: GameBitmapLoader,
    private val chipSet: ChipSet,
    protected val colorPrism: ColorPrism,
    margin: Float = 0f
) : Any()
    , SpatialBoard by SimpleSpatialBoard(board, margin)
    , BoardPresenter
    , BoardHighlighting by boardHighlighting
    , WithLog by AndroidLogOf<SimpleBoardPresenter>()
{

    override val bitmapPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

//    private var cellsBitmapSnapshot: Bitmap? = null
//    private var boardSnapshot: PrimitiveBoard? = null
//    private var highlightingsSnapshot: Map<Pos, Highlighting>? = null
//    private var chipsAndHighlightinsBitmapSnapshot: Bitmap? = null

    private val cachedHighlightings = CachedMap<CellSize, Highlighting, Bitmap>(
        create = { cellSize, highlighting -> createHighlightingBitmap(cellSize, highlighting) },
        differ = { cs0, cs -> cs0 != cs }
    )
    private val cachedChips = CachedMap<CellSize, Chip, Bitmap>(
        create = { cellSize, chip -> createChipBitmap(cellSize, chip) },
        differ = { cs0, cs -> cs0 != cs }
    )
    private val cachedCellsBitmap =
        Cached<Pair<EmptyBoard, Size>, Bitmap>(
            create = { (board, wh) -> createCellsBitmap(board, wh.first, wh.second) },
            differ = { (_, wh0), (_, wh) -> wh0 != wh }
        )
    private val cachedChipsAndHighlightingsBitmap =
        Cached<Triple<PrimitiveBoard, Highlightings, Size>, Bitmap>(
            create = { (board, highlightings, wh) -> createChipsAndHighlightingsBitmap(board, highlightings, wh.first, wh.second) },
            differ = { (b0, h0, _), (b, h, _) -> h0 != h || b0 != b }
        )

    private val printOnce by Once(true) //tmp

    override fun Canvas.drawBoard(board: Board) {
        if (printOnce)
            log i "first drawBoard"
        drawCells(board)
        drawHighlightingsAndChips(board)
    }

    private fun Canvas.drawCells(board: Board) {
        // NOTE: assuming board as EmptyBoard does not change
        val bitmapSnapshot = cachedCellsBitmap[Pair(board, Pair(width, height))]
//        val bitmapSnapshot =
//            cellsBitmapSnapshot ?:
//            createCellsBitmap(board, width, height)
        drawBitmap(bitmapSnapshot, 0f, 0f, bitmapPaint) // ~3.5ms
    }

    private fun createCellsBitmap(board: EmptyBoard, width: Int, height: Int): Bitmap {
        val snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(snapshot)
        canvas.drawColor(BACKGROUND_COLOR)
        for (pos in board.asPosSet())
            canvas.drawCell(pos)
//        cellsBitmapSnapshot = snapshot
        return snapshot
    }

    private fun Canvas.drawHighlightingsAndChips(board: Board) {
//        val shouldBeInvalidated =
//            highlightingsSnapshot != highlightings || boardSnapshot != board // MAYBE: find better way, than cmp str-repr
//        if (shouldBeInvalidated) {
//            createChipsAndHighlightingsBitmap(board, width, height)
//        }
//        val bitmapSnapshot = chipsAndHighlightinsBitmapSnapshot!! // previous invalidate made it non-null
        val bitmapSnapshot = cachedChipsAndHighlightingsBitmap[Triple(PrimitiveBoard.Factory.of(board), highlightings, Pair(width, height))]
        drawBitmap(bitmapSnapshot, 0f, 0f, bitmapPaint) // ~3.5ms
    }

    private fun createChipsAndHighlightingsBitmap(board: PrimitiveBoard, highlightings: Highlightings, width: Int, height: Int): Bitmap {
        return log i withMilestoneScope("invalidate C&H") {
//            highlightingsSnapshot = highlightings.toMap()
            - "copy board&highlightings"
            val snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) // ~1.5-3ms
            - "createBitmap"
            val canvas = Canvas(snapshot)
            - "mk Canvas"
            // ~10ms
            for ((pos, highlighting) in highlightings) {
                log i elapsedTime("drawHighlighting", startMarker = null) {
//                    canvas.drawHighlighting(highlighting, pos)
                    canvas.drawBitmapAt(bitmapLoader.loadHighlighting(highlighting), pos)
                }
            }
            - "draw highlightings"
            // ~50ms
            for ((pos, maybeChip) in board.asPosMap())
                maybeChip?.let { canvas.drawChip(pos, it) }
            - "draw chips"
//            chipsAndHighlightinsBitmapSnapshot = snapshot
            return@withMilestoneScope snapshot
        }
    }

    private fun Canvas.drawCell(pos: Pos) {
        drawBitmapAt(bitmapLoader.loadCell(), pos)
    }

    private fun Canvas.drawBitmapAt(bitmap: Bitmap, pos: Pos) {
        val rescaleMatrix = rescaleMatrix(bitmap)
        val translateMatrix = pos2translationMatrix(pos)
        val matrix = translateMatrix * rescaleMatrix
        drawBitmap( // ~1.9ms
            bitmap,
            matrix,
            bitmapPaint
        )
    }

    private fun createHighlightingBitmap(cellSize: CellSize, highlighting: Highlighting): Bitmap {
        val bitmap = bitmapLoader.loadHighlighting(highlighting)
        return bitmap.scale(cellSize, cellSize)
    }

    private fun Canvas.drawHighlighting(highlighting: Highlighting, pos: Pos) {
        val bitmap = cachedHighlightings[cellSize, highlighting]
        val translateMatrix = pos2translationMatrix(pos)
        drawBitmap( // ~1.5ms
            bitmap,
            translateMatrix,
            bitmapPaint
        )
    }

    private fun createChipBitmap(cellSize: CellSize, chip: Chip): Bitmap {
        val bitmap = bitmapLoader.loadChip(chipSet, colorPrism, chip)
        return bitmap.scale(cellSize, cellSize)
    }

    private fun Canvas.drawChip(pos: Pos, chip: Chip) {
        val bitmap = cachedChips[cellSize, chip]
        val centeredScaleMatrix = centeredScaleMatrix(bitmap, chipCellRatio)
        val translateMatrix = pos2translationMatrix(pos)
        drawBitmap(
            bitmap,
            translateMatrix * centeredScaleMatrix,
            bitmapPaint
        ) // ~1ms
    }

    override fun invalidateBoard() {
        cachedChipsAndHighlightingsBitmap.invalidate()
    }

    companion object {
        @ColorInt private const val BACKGROUND_COLOR: Int = Color.BLACK
    }

    class Factory(
        private val boardHighlighting: BoardHighlighting,
        private val bitmapLoader: GameBitmapLoader
    ) : BoardPresenter.Factory {
        override fun of(board: Board, chipSet: ChipSet, colorPrism: ColorPrism, margin: Float) =
            SimpleBoardPresenter(board, boardHighlighting, bitmapLoader, chipSet, colorPrism, margin)
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
    fun startTransitions(turn: Pos, transitions: Sequence<Transition>)
    /** Draw current [game] state with animations */
    override fun draw(canvas: Canvas)

    interface Factory {
        fun of(game: Game, chipsConfig: ChipsConfig, margin: Float = 0f): GamePresenter
    }
}

class SimpleGamePresenter(
    override val game: Game,
    boardHighlighting: BoardHighlighting,
    bitmapLoader: GameBitmapLoader,
    private val chipsConfig: ChipsConfig,
    transitionsHost: TransitionAnimationsHost,
    margin: Float = 1f
) : SimpleBoardPresenter(game.board, boardHighlighting, bitmapLoader, chipsConfig.chipSet, chipsConfig.colorPrism, margin)
    , TransitionAnimationsHost by transitionsHost
    , GamePresenter
{

    override fun advance(timeDelta: Milliseconds) {
        advanceAnimations(timeDelta)
    }

    @Synchronized
    override fun draw(canvas: Canvas) {
        require(cellSize > 0) { "setSize must be called before draw" }
        if (!blocking)
            canvas.drawBoard(board)
        drawAnimations(canvas)
    }

    override fun freezeBoard() {
        board = game.board.copy()
    }

    override fun unfreezeBoard() {
        board = game.board
    }

    override fun startTransitions(turn: Pos, transitions: Sequence<Transition>) {
        // NOTE: leaky leak of SimpleGamePresenter (circular reference)
        // MAYBE: use WeakRef
        hideInterceptedLastTurns(transitions)
        startAdvancer(
            animatedAdvancerOf(
                chipsConfig, bitmapLoader, this, turn, transitions
            )
        )
    }

    class Factory(
        private val boardHighlighting: BoardHighlighting,
        private val bitmapLoader: GameBitmapLoader,
        private val transitionsHost: TransitionAnimationsHost
    ) : GamePresenter.Factory {
        override fun of(game: Game, chipsConfig: ChipsConfig, margin: Float) =
            SimpleGamePresenter(game, boardHighlighting, bitmapLoader, chipsConfig, transitionsHost, margin)
    }
}