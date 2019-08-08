package com.pierbezuhoff.clonium.ui.newgame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityNewGameBinding
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.domain.Highlighting
import com.pierbezuhoff.clonium.domain.PlayerTactic
import com.pierbezuhoff.clonium.domain.SimpleBoard
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.ui.game.GameActivity
import com.pierbezuhoff.clonium.utils.Once
import kotlinx.android.synthetic.main.activity_new_game.*
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.viewModel

class NewGameActivity : AppCompatActivity() {
    private val newGameViewModel: NewGameViewModel by viewModel()
    private var playerAdapter: PlayerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityNewGameBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_new_game)
        binding.lifecycleOwner = this
        binding.viewModel = newGameViewModel

        newGameViewModel.boardPresenter.observe(this, Observer {
            if (playerAdapter == null) {
                val adapter = PlayerAdapter(newGameViewModel.playerItems, get())
                adapter.boardPlayerVisibilitySubscription.subscribeFrom(newGameViewModel)
                adapter.boardPlayerHighlightingSubscription.subscribeFrom(newGameViewModel)
                players_recycler_view.adapter = adapter
                val callback: ItemTouchHelper.Callback = ItemMoveCallback(adapter)
                ItemTouchHelper(callback).attachToRecyclerView(players_recycler_view)
                playerAdapter = adapter
            } else {
                playerAdapter!!.setPlayerItems(newGameViewModel.playerItems)
            }
        })
        previous_board.setOnClickListener {
            newGameViewModel.previousBoard()
        }
        next_board.setOnClickListener {
            newGameViewModel.nextBoard()
        }
        cancel_button.setOnClickListener {
            onBackPressed()
        }
        start_game_button.setOnClickListener {
            startGame()
        }
    }

    private fun startGame() {
        val intent = Intent(this, GameActivity::class.java)
        val board = SimpleBoard(newGameViewModel.board)
        val bots = newGameViewModel.playerItems
            .filter { it.tactic is PlayerTactic.Bot && it.participate }
            .associate { it.playerId to (it.tactic as PlayerTactic.Bot) }
        val order =
            if (newGameViewModel.useRandomOrder.value!!) null
            else newGameViewModel.playerItems
                .filter { it.participate }
                .map { it.playerId }
        val gameState = Game.State(board, bots, order)
        intent.putExtra(GAME_STATE_EXTRA, gameState)
        startActivityForResult(intent, GAME_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GAME_REQUEST_CODE -> finish()
        }
    }

    companion object {
        private const val packagePrefix = "com.pierbezuhoff.clonium.ui.newgame."
        const val GAME_STATE_EXTRA = packagePrefix + "gameState"
        private const val GAME_REQUEST_CODE = 1
    }
}

