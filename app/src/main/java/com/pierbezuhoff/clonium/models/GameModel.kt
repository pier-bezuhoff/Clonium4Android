package com.pierbezuhoff.clonium.models

import android.graphics.Canvas
import android.graphics.PointF
import android.util.Log
import com.pierbezuhoff.clonium.domain.BotPlayer
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.domain.HumanPlayer
import com.pierbezuhoff.clonium.ui.game.DrawThread
import com.pierbezuhoff.clonium.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf

// MAYBE: non-significant explosions are non-blocking
// MAYBE: new type: board with highlights
// TODO: show all last turns in the round if no intersection (with decreasing visibility?)
// TODO: issue pre-turn (BoardPresenter.showNextTurn)
class GameModel(
    val game: Game,
    private val config: GameConfig,
    private val coroutineScope: CoroutineScope
) : Any()
    , DrawThread.Callback
    , Logger by AndroidLoggerOf<GameModel>()
    , KoinComponent
{
    private val gamePresenter: GamePresenter = get<GamePresenter.Builder>().of(game, margin = 1f)
    private var continueGameOnce by Once(true)

    init {
        logV("order = ${game.order.map { it.playerId }.joinToString()}")
        logV(game.board.asString())
    }

    fun userTap(point: PointF) {
        if (/*!game.isEnd() && */!gamePresenter.blocking && game.currentPlayer is HumanPlayer) {
            val pos = gamePresenter.pointf2pos(point)
            if (pos in game.possibleTurns()) {
                gamePresenter.boardHighlighting.hidePossibleTurns()
                gamePresenter.freezeBoard()
                val transitions = game.humanTurn(pos)
                gamePresenter.boardHighlighting.showLastTurn(pos, game.order.size)
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
        logI("continueGame()")
        when {
            game.isEnd() -> {
                logI("game ended: ${game.currentPlayer} won")
                // show overall stat
            }
            game.currentPlayer is BotPlayer -> {
                gamePresenter.boardHighlighting.showBotPossibleTurns(game.possibleTurns())
                gamePresenter.freezeBoard()
                coroutineScope.launch {
                    delay(config.botMinTime)
                    val transitions = with(game) { botTurnAsync() }.await()
                    gamePresenter.boardHighlighting.hidePossibleTurns()
                    gamePresenter.boardHighlighting.showLastTurn(game.lastTurn!!, game.nPlayers)
                    gamePresenter.startTransitions(transitions)
                    gamePresenter.unfreezeBoard()
                    continueGameOnce = true
                }
            }
            else -> {
                // before human's turn:
                gamePresenter.boardHighlighting.showHumanPossibleTurns(game.possibleTurns())
            }
        }
    }
}