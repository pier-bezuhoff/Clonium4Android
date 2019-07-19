package com.pierbezuhoff.clonium.ui.game

import android.app.Application
import android.graphics.PointF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.clonium.di.NAMES
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.ui.meta.TapListener
import com.pierbezuhoff.clonium.utils.Once
import org.koin.core.get
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

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
        val game = get<Game>(named(NAMES.EXAMPLE))
        val newGameModel = get<GameModel> { parametersOf(game, viewModelScope) }
        _gameModel.value = newGameModel
    }

    override fun onTap(x: Float, y: Float) {
        gameModel.value?.userTap(PointF(x, y))
    }
}