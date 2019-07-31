package com.pierbezuhoff.clonium.ui.newgame

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.BoardPresenter
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.utils.Connection
import org.koin.core.get
import org.koin.core.parameter.parametersOf

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

    private fun getLastBoard(): Board {
        return BoardFactory.spawn4players(EmptyBoardFactory.TOWER)
    }

    fun setBoard(board: Board) {
        this.initialBoard = board
        this.board = board
        playerItems = playerItemsOf(board).toMutableList()
        _boardPresenter.value = get<BoardPresenter> { parametersOf(board) }
    }

    private fun playerItemsOf(board: Board): List<PlayerItem> =
        board.players()
            .map { PlayerItem(it, PlayerTactic.Bot.RandomPicker, participate = true) }

    override fun hidePlayer(playerId: PlayerId) {
        Log.i(TAG, "hidePlayer($playerId)")
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
        Log.i(TAG, "highlighPlayer($playerId)")
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
        private const val TAG = "NewGameViewModel"
    }
}