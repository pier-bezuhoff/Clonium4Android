package com.pierbezuhoff.clonium.models

import android.graphics.Canvas
import android.graphics.PointF
import android.util.Log
import com.pierbezuhoff.clonium.domain.Bot
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.domain.HumanPlayer
import com.pierbezuhoff.clonium.ui.game.DrawThread
import com.pierbezuhoff.clonium.utils.Once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameModel(
    val game: Game,
    bitmapLoader: BitmapLoader,
    private val coroutineScope: CoroutineScope
) : Any()
    , DrawThread.Callback
{
    private val gamePresenter: GamePresenter = SimpleGamePresenter(game, bitmapLoader)
    private var continueGameOnce by Once(true)

    init {
        Log.i(TAG, "order = ${game.order.map { it.playerId }.joinToString()}")
        Log.i(TAG, game.board.asString())
    }

    fun userTap(point: PointF) {
        game.isEnd()
        if (!gamePresenter.hasBlockingAnimations() && game.currentPlayer is HumanPlayer) {
            val pos = gamePresenter.pointf2pos(point)
            if (pos in game.possibleTurns()) {
                gamePresenter.unhighlight()
                gamePresenter.board = game.board.copy() // freeze board before changes
                val transitions = game.humanTurn(pos)
                gamePresenter.startTransitions(transitions) // unfreeze board eventually
                continueGameOnce = true
            }
        }
    }

    fun setSize(width: Int, height: Int) =
        gamePresenter.setSize(width, height)

    override fun draw(canvas: Canvas) =
        gamePresenter.draw(canvas)

    override fun advance(timeDelta: Long) {
        gamePresenter.advance(timeDelta)
        if (!gamePresenter.hasBlockingAnimations() && continueGameOnce)
            continueGame()
    }

    private fun continueGame() {
        Log.i(TAG, "${game.currentPlayer} (${game.currentPlayer.playerId})")
        Log.i(TAG, game.board.asString())
        when {
            game.isEnd() -> {
                Log.i(TAG, "game ended")
                // stat, back
            }
            game.currentPlayer is Bot -> coroutineScope.launch {
                gamePresenter.highlight(game.possibleTurns(), weak = true)
                delay(300L)
                gamePresenter.unhighlight()
                gamePresenter.board = game.board.copy() // freeze board before changes
                val transitions = with(game) { botTurnAsync() }.await()
                gamePresenter.startTransitions(transitions) // unfreeze board eventually
                continueGameOnce = true
            }
            else -> {
                gamePresenter.highlight(game.possibleTurns())
                // highlight possible turns, etc.
            }
        }
    }

    companion object {
        private const val TAG = "GameModel"
    }
}