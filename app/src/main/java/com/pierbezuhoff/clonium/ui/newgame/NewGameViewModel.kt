package com.pierbezuhoff.clonium.ui.newgame

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.BoardPresenter
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.utils.Connection
import com.pierbezuhoff.clonium.utils.Ring
import com.pierbezuhoff.clonium.utils.ringOf
import org.koin.core.get

// TODO: choose color of chip
// TODO: tap to move handle
// TODO: preserve players and order on next/previous
// BUG: after next/previous dragging does not work
class NewGameViewModel(application: Application) : CloniumAndroidViewModel(application)
    , PlayerAdapter.BoardPlayerHider
    , PlayerAdapter.BoardPlayerHighlighter
{
    interface BoardViewInvalidator { fun invalidateBoardView() }
    private val boardViewInvalidating = Connection<BoardViewInvalidator>()
    val boardViewInvalidatingSubscription = boardViewInvalidating.subscription

    private val _boardPresenter: MutableLiveData<BoardPresenter> = MutableLiveData()
    val boardPresenter: LiveData<BoardPresenter> = _boardPresenter
    private lateinit var initialBoard: Board
    lateinit var board: Board
        private set
    lateinit var playerItems: MutableList<PlayerItem>
        private set
    val useRandomOrder: MutableLiveData<Boolean> = MutableLiveData(true)

    init {
        setBoard(getLastBoard())
    }

    fun nextBoard() {
        setBoard(BOARDS.next())
    }

    fun previousBoard() {
        setBoard(BOARDS.previous())
    }

    private fun getLastBoard(): Board =
        BOARDS.focus

    fun setBoard(newBoard: Board) {
        val board = shufflePlayerIds(newBoard)
        this.initialBoard = board
        this.board = board
        playerItems = playerItemsOf(board).toMutableList()
        _boardPresenter.value = get<BoardPresenter.Builder>().of(board)
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    private fun shufflePlayerIds(board: Board): Board {
        val shuffling = board.players()
            .zip((0..5).shuffled().map { PlayerId(it) })
            .toMap()
        val simpleBoard = SimpleBoard(board)
        for ((pos, maybeChip) in simpleBoard.posMap)
            maybeChip?.let { (playerId, level) ->
                simpleBoard.posMap[pos] = Chip(shuffling.getValue(playerId), level)
            }
        return simpleBoard
    }

    private fun playerItemsOf(board: Board): List<PlayerItem> {
        val items = board.players()
            .map { PlayerItem(it, PlayerTactic.Bot.RandomPicker, participate = true) }
        if (items.isNotEmpty())
            items.first().tactic = PlayerTactic.Human
        return items
    }

    override fun hidePlayer(playerId: PlayerId) {
        board = SimpleBoard(board).apply {
            for (pos in possOf(playerId))
                posMap[pos] = null
        }
        _boardPresenter.value?.board = board
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    override fun showPlayer(playerId: PlayerId) {
        board = SimpleBoard(board).apply {
            for (pos in initialBoard.possOf(playerId))
                posMap[pos] = initialBoard.chipAt(pos)
        }
        _boardPresenter.value?.board = board
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    override fun highlighPlayer(playerId: PlayerId) {
        boardPresenter.value?.highlight(board.possOf(playerId))
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    override fun unhighlight() {
        boardPresenter.value?.unhighlight()
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    companion object {
        private val BOARDS: Ring<Board> = with(SimpleBoard.Examples) {
            ringOf(
                TOWER,
                SMALL_TOWER,
                DEFAULT_1,
                DEFAULT_2,
                DEFAULT_3,
                DEFAULT_4,
                DEFAULT_5
            )
        }
    }
}