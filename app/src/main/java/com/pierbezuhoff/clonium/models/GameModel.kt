package com.pierbezuhoff.clonium.models

import android.graphics.Canvas
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
        if (game.currentPlayer is HumanPlayer) {
            val pos = pointf2pos(point)
            if (pos in game.possibleTurns()) {
                val transitions = game.humanTurn(pos)
                // animate transitions
                continueGame()
            }
        }
    }

    private fun continueGame() {
        Log.i(TAG, "${game.currentPlayer} (${game.currentPlayer.playerId})")
        Log.i(TAG, game.board.asString())
        if (game.currentPlayer is Bot) {
            coroutineScope.launch {
                delay(1_000) // TMP
                val transitions = with(game) { botTurnAsync() }.await()
                continueGame()
                Unit
            }
        } else {
            // highlight possible turns, etc.
        }
    }

    override fun advance(timeDelta: Long) {
        // advance transitions animation
    }

    override fun draw(canvas: Canvas) {
        drawCurrentBoard(canvas)
    }

    companion object {
        private const val TAG = "GameModel"
    }
}