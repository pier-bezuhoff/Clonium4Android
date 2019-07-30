package com.pierbezuhoff.clonium.ui.newgame

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.domain.Board
import com.pierbezuhoff.clonium.domain.BoardFactory
import com.pierbezuhoff.clonium.domain.EmptyBoardFactory
import com.pierbezuhoff.clonium.models.BoardPresenter
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import org.koin.core.get
import org.koin.core.parameter.parametersOf

class NewGameViewModel(application: Application) : CloniumAndroidViewModel(application) {
    private val _boardPresenter: MutableLiveData<BoardPresenter> = MutableLiveData()
    val boardPresenter: LiveData<BoardPresenter> = _boardPresenter
    private lateinit var board: Board
    lateinit var playerItems: MutableList<PlayerItem>
        private set

    init {
        setBoard(getLastBoard())
    }

    private fun getLastBoard(): Board {
        return BoardFactory.spawn4players(EmptyBoardFactory.TOWER)
    }

    private fun setBoard(board: Board) {
        this.board = board
        playerItems = playerItemsOf(board).toMutableList()
        _boardPresenter.value = get<BoardPresenter> { parametersOf(board) }
    }

    private fun playerItemsOf(board: Board): List<PlayerItem> =
        board.players()
            .map { PlayerItem(it, PlayerTactics.RANDOM_BOT, participate = true) }
}