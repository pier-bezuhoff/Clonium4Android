package com.pierbezuhoff.clonium.ui.game

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.utils.Once
import org.koin.core.get
import org.koin.core.parameter.parametersOf

class GameViewModel(application: Application) : CloniumAndroidViewModel(application) {
    private val firstNewGame by Once(true)
    private val _gameModel: MutableLiveData<GameModel> = MutableLiveData()
    val gameModel: LiveData<GameModel> = _gameModel

    fun newGame() {
        if (firstNewGame) {
            exampleGame()
        }
    }

    private fun exampleGame() {
        val board = BoardFactory.DEFAULT_4
        val bots3 = setOf(1, 2, 3).map { RandomPickerBot(PlayerId(it)) }.toSet()
        _gameModel.value = get<GameModel> { parametersOf(board, bots3) }
    }
}