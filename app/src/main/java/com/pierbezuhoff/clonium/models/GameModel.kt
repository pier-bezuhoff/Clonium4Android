package com.pierbezuhoff.clonium.models

import android.graphics.Canvas
import android.graphics.PointF
import android.util.Log
import com.pierbezuhoff.clonium.domain.BotPlayer
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.domain.HumanPlayer
import com.pierbezuhoff.clonium.ui.game.DrawThread
import com.pierbezuhoff.clonium.utils.AndroidLogger
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.Once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf

// MAYBE: non-significant explosions are non-blocking
class GameModel(
    val game: Game,
    private val config: GameConfig,
    private val coroutineScope: CoroutineScope
) : Any()
    , DrawThread.Callback
    , Logger by AndroidLogger("GameModel")
    , KoinComponent
{
    private val gamePresenter: GamePresenter = get<GamePresenter.Builder>().of(game)
    private var continueGameOnce by Once(true)

    init {
        logV("order = ${game.order.map { it.playerId }.joinToString()}")
        logV(game.board.asString())
    }

    fun userTap(point: PointF) {
        game.isEnd()
        if (!gamePresenter.blocking && game.currentPlayer is HumanPlayer) {
            val pos = gamePresenter.pointf2pos(point)
            if (pos in game.possibleTurns()) {
                gamePresenter.unhighlight()
                gamePresenter.freezeBoard()
                val transitions = game.humanTurn(pos)
                gamePresenter.startTransitions(transitions)
                gamePresenter.unfreezeBoard()
                continueGameOnce = true
            }
        }
    }

    fun setSize(width: Int, height: Int) =
        gamePresenter.setSize(width, height)

    override fun draw(canvas: Canvas) =
        gamePresenter.draw(canvas)

    override fun advance(timeDelta: Long) {
        gamePresenter.advance((config.gameSpeed * timeDelta).toLong())
        if (!gamePresenter.blocking && continueGameOnce)
            continueGame()
    }

    private fun continueGame() {
        when {
            game.isEnd() -> {
                logI("game ended")
                // show overall stat
            }
            game.currentPlayer is BotPlayer -> coroutineScope.launch {
                gamePresenter.highlight(game.possibleTurns(), weak = true)
                gamePresenter.freezeBoard()
                delay(config.botMinTime)
                val transitions = with(game) { botTurnAsync() }.await()
                gamePresenter.unhighlight()
                gamePresenter.startTransitions(transitions)
                gamePresenter.unfreezeBoard()
                continueGameOnce = true
            }
            else -> {
                // before human's turn:
                gamePresenter.highlight(game.possibleTurns())
            }
        }
    }
}