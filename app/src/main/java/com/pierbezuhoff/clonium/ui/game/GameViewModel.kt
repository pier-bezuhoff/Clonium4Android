package com.pierbezuhoff.clonium.ui.game

import android.app.Application
import android.graphics.PointF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.ui.meta.TapListener
import com.pierbezuhoff.clonium.utils.Once
import org.koin.core.get
import org.koin.core.inject
import org.koin.core.parameter.parametersOf

class GameViewModel(application: Application) : CloniumAndroidViewModel(application)
    , TapListener
{
    private val firstNewGame by Once(true)

    private val gestures: GameGestures by inject()
    private val _gameModel: MutableLiveData<GameModel> = MutableLiveData()
    val gameModel: LiveData<GameModel> = _gameModel

    init {
        gestures.tapSubscription.subscribeFrom(this)
    }

    fun newGame() {
        if (firstNewGame) {
            exampleGame()
        }
    }

    private fun exampleGame() {
        val board = BoardFactory.spawn4players(EmptyBoardFactory.TOWER) // BoardFactory.DEFAULT_2
        val bots3: Set<Bot> = setOf(0, 1, 2, 3).map { RandomPickerBot(PlayerId(it)) }.toSet()
        val newGameModel = get<GameModel> { parametersOf(board, bots3, viewModelScope) }
        _gameModel.value = newGameModel
    }

    override fun onTap(x: Float, y: Float) {
        gameModel.value?.userTap(PointF(x, y))
    }
}