package com.pierbezuhoff.clonium.ui.newgame

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.*
import com.pierbezuhoff.clonium.models.animation.ChipAnimation
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel
import com.pierbezuhoff.clonium.utils.*
import org.jetbrains.anko.defaultSharedPreferences
import org.koin.core.get

class NewGameViewModel(application: Application) : CloniumAndroidViewModel(application)
    , PlayerAdapter.BoardPlayerHider
    , PlayerAdapter.BoardPlayerHighlighter
    , BoardViewInvalidator
    , Logger by AndroidLoggerOf<NewGameViewModel>()
{
    private val boardViewInvalidating = Connection<BoardViewInvalidator>()
    val boardViewInvalidatingSubscription = boardViewInvalidating.subscription
    override fun invalidateBoardView() {
        _boardPresenter.value?.invalidateBoard()
        boardViewInvalidating.send { invalidateBoardView() }
    }

    private val _boardPresenter: MutableLiveData<BoardPresenter> = MutableLiveData()
    val boardPresenter: LiveData<BoardPresenter> = _boardPresenter
    private lateinit var initialBoard: Board
    lateinit var board: SimpleBoard
        private set
    var playerItems: MutableList<PlayerItem> = mutableListOf()
        private set
    val useRandomOrder: MutableLiveData<Boolean> = MutableLiveData(true)

    private lateinit var chipAnimation: ChipAnimation
    lateinit var chipSet: ChipSet
        private set
    lateinit var colorPrism: MutableMapColorPrism
        private set

    init {
        // FIX: load cfg and init SimpleBoard.Examples take ~300ms
        loadConfig() // ~150ms after "New game" button press
    }

    fun nextBoard() {
        setBoard(boards.next())
    }

    fun previousBoard() {
        setBoard(boards.previous())
    }

    fun setBoard(newBoard: SimpleBoard) {
        initialBoard = newBoard
        board = newBoard
        playerItems = if (playerItems.isEmpty()) {
            playerItemsOf(newBoard).toMutableList()
        } else {
            val playerIds = newBoard.players()
            val oldPlayerItems = playerItems.filter { it.playerId in playerIds }
            val oldPlayerIds = oldPlayerItems.map { it.playerId }
            val newPlayerIds = playerIds.subtract(oldPlayerIds)
            val newPlayerItems = newPlayerIds
                .map { PlayerItem(it, PlayerTactic.Bot.RandomPicker, true) }
            (oldPlayerItems + newPlayerItems).toMutableList()
        }
        _boardPresenter.value = get<BoardPresenter.Factory>().of(
            newBoard,
            chipSet, colorPrism,
            margin = 0.01f // experimental value for visually equal margins from all sides
        )
        invalidateBoardView()
        playerItems
            .filterNot { it.participate }
            .forEach {
                hidePlayer(it.playerId)
            }
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
        invalidateBoardView()
    }

    override fun showPlayer(playerId: PlayerId) {
        board = SimpleBoard(board).apply {
            for (pos in initialBoard.possOf(playerId))
                posMap[pos] = initialBoard.chipAt(pos)
        }
        _boardPresenter.value?.board = board
        invalidateBoardView()
    }

    override fun highlightPlayer(playerId: PlayerId) {
        boardPresenter.value?.showHumanPossibleTurns(board.possOf(playerId))
        invalidateBoardView()
        useRandomOrder.value = false // dragging started => not random order
    }

    override fun unhighlight() {
        boardPresenter.value?.hidePossibleTurns()
        invalidateBoardView()
    }

    fun mkGameState(): Game.State {
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

    private fun loadConfig() {
        context.defaultSharedPreferences.let {
            chipAnimation = it.chipAnimation
            chipSet = it.chipSet
            colorPrism = MutableMapColorPrism.Factory.of(
                it.colorPrism ?: chipSet.customColorPrism
            )
            chipAnimation = ChipAnimation.ROTATION //tmp
            chipSet = CircuitChipSet //tmp
            val playersConfig = it.playersConfig
            playerItems = playersConfig.playerItems.toMutableList()
            val index = it.getInt(SimpleBoard.Examples::class.simpleName, 0)
            setBoard(SimpleBoard.Examples.ALL[index])
            useRandomOrder.value = it.getBoolean(::useRandomOrder.name, useRandomOrder.value ?: true)
        }
    }

    fun saveConfig() {
        context.defaultSharedPreferences.edit {
            putInt(SimpleBoard.Examples::class.simpleName, SimpleBoard.Examples.ALL.indexOf(initialBoard))
            useRandomOrder.value?.let { putBoolean(::useRandomOrder.name, it) }
        }
        context.defaultSharedPreferences.let {
            it.playersConfig = PlayersConfig(playerItems)
            it.chipAnimation = chipAnimation
            it.chipSet = chipSet
            it.colorPrism = colorPrism
        }
    }

    companion object {
        private val boards: Ring<SimpleBoard> = ringOf(*SimpleBoard.Examples.ALL.toTypedArray())
    }
}