package com.pierbezuhoff.clonium.ui.newgame

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.BoardPresenter
import com.pierbezuhoff.clonium.models.PlayerItem
import com.pierbezuhoff.clonium.models.PlayersConfig
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.utils.Connection
import com.pierbezuhoff.clonium.utils.Ring
import com.pierbezuhoff.clonium.utils.ringOf
import org.jetbrains.anko.coroutines.experimental.asReference
import org.jetbrains.anko.defaultSharedPreferences
import org.koin.core.get

// TODO: choose color of chip
// TODO: tap to move handle
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
    lateinit var board: SimpleBoard
        private set
    var playerItems: MutableList<PlayerItem> = mutableListOf()
        private set
    val useRandomOrder: MutableLiveData<Boolean> = MutableLiveData(true)

    init {
        loadConfig()
    }

    fun nextBoard() {
        setBoard(boards.next())
    }

    fun previousBoard() {
        setBoard(boards.previous())
    }

    fun setBoard(newBoard: SimpleBoard) {
        val board = newBoard //shufflePlayerIds(newBoard)
        this.initialBoard = board
        this.board = board
        if (playerItems.isEmpty()) {
            playerItems = playerItemsOf(board).toMutableList()
        } else {
            val playerIds = board.players()
            val oldPlayerItems = playerItems.filter { it.playerId in playerIds }
            val oldPlayerIds = oldPlayerItems.map { it.playerId }
            val newPlayerIds = playerIds.subtract(oldPlayerIds)
            val newPlayerItems = newPlayerIds
                .map { PlayerItem(it, PlayerTactic.Bot.RandomPicker, true) }
            playerItems = (oldPlayerItems + newPlayerItems).toMutableList()
        }
        _boardPresenter.value = get<BoardPresenter.Builder>().of(board, margin = 0.01f) // experimental value for visually equal margins from all sides
        boardViewInvalidating.send {
            invalidateBoardView()
        }
        playerItems
            .filterNot { it.participate }
            .forEach {
                hidePlayer(it.playerId)
            }
    }

    private fun shufflePlayerIds(board: SimpleBoard): SimpleBoard {
        val shuffling = board.players()
            .zip((0..5).shuffled().map { PlayerId(it) })
            .toMap()
        val simpleBoard = SimpleBoard(board)
        for ((pos, maybeChip) in simpleBoard.posMap)
            maybeChip?.let { (playerId, level) ->
                simpleBoard.posMap[pos] = Chip(shuffling.getValue(playerId), level)
            }
        return simpleBoard
    }

    private fun playerItemsOf(board: Board): List<PlayerItem> {
        val items = board.players()
            .map { PlayerItem(it, PlayerTactic.Bot.RandomPicker, participate = true) }
        if (items.isNotEmpty())
            items.first().tactic = PlayerTactic.Human
        return items
    }

    override fun hidePlayer(playerId: PlayerId) {
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

    override fun highlightPlayer(playerId: PlayerId) {
        boardPresenter.value?.boardHighlighting?.showHumanPossibleTurns(board.possOf(playerId))
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    override fun unhighlight() {
        boardPresenter.value?.boardHighlighting?.hidePossibleTurns()
        boardViewInvalidating.send {
            invalidateBoardView()
        }
    }

    fun makeGameState(): Game.State {
        val board = SimpleBoard(board)
        val bots = playerItems
            .filter { it.tactic is PlayerTactic.Bot && it.participate }
            .associate { it.playerId to (it.tactic as PlayerTactic.Bot) }
        val order =
            if (useRandomOrder.value!!) null
            else playerItems
                .filter { it.participate }
                .map { it.playerId }
        saveConfig()
        return Game.State(board, bots, order)
    }

    override fun onCleared() {
        saveConfig()
        super.onCleared()
    }

    private fun loadConfig() {
        with(context.defaultSharedPreferences) {
            val playersConfig = PlayersConfig.Builder.fromStringSet(
                getStringSet(PlayersConfig::class.simpleName, mutableSetOf()) ?: mutableSetOf()
            )
            playerItems = playersConfig.playerItems.toMutableList()
            val index = getInt(SimpleBoard.Examples::class.simpleName, 0)
            setBoard(SimpleBoard.Examples.ALL[index])
            useRandomOrder.value = getBoolean(::useRandomOrder.name, useRandomOrder.value ?: true)
        }
    }

    private fun saveConfig() {
        context.defaultSharedPreferences.edit {
            putStringSet(PlayersConfig::class.simpleName, PlayersConfig(playerItems).toStringSet())
            putInt(SimpleBoard.Examples::class.simpleName, SimpleBoard.Examples.ALL.indexOf(initialBoard))
            useRandomOrder.value?.let { putBoolean(::useRandomOrder.name, it) }
        }
    }

    companion object {
        private val boards: Ring<SimpleBoard> = ringOf(*SimpleBoard.Examples.ALL.toTypedArray())
    }
}