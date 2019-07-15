package com.pierbezuhoff.clonium.models

import android.graphics.PointF
import android.util.Log
import com.pierbezuhoff.clonium.domain.Bot
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.domain.HumanPlayer
import com.pierbezuhoff.clonium.ui.game.DrawThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameModel(
    val game: Game,
    private val bitmapLoader: BitmapLoader,
    private val coroutineScope: CoroutineScope
) : Any()
    , GamePresenter by SimpleGamePresenter(game.board, bitmapLoader)
    , DrawThread.Callback
{
    init {
        Log.i(TAG, "order = ${game.order.map { it.playerId }.joinToString()}")
        Log.i(TAG, game.board.asString())
        continueGame()
    }

    fun userTap(point: PointF) {
        game.isEnd()
        if (!hasBlockingAnimations() && game.currentPlayer is HumanPlayer) {
            val pos = pointf2pos(point)
            _exampleExplosion(pos)
            if (false && pos in game.possibleTurns()) {
                unhighlight()
                val transitions = game.humanTurn(pos)
//                startTransitions(transitions.iterator())
                // wait until GamePresenter.AnimationState.Normal
                continueGame()
            }
        }
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
                highlight(game.possibleTurns(), weak = true)
                unhighlight()
                delay(1_000L)
                val transitions = with(game) { botTurnAsync() }.await()
//                startTransitions(transitions.iterator())
                // wait until GamePresenter.AnimationState.Normal
                continueGame()
            }
            else -> {
                highlight(game.possibleTurns())
                // highlight possible turns, etc.
            }
        }
    }

    companion object {
        private const val TAG = "GameModel"
    }
}