package com.pierbezuhoff.clonium.ui.newgame

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityNewGameBinding
import com.pierbezuhoff.clonium.ui.game.GameActivity
import com.pierbezuhoff.clonium.utils.AndroidLoggerOf
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.VerticalSpaceItemDecoration
import kotlinx.android.synthetic.main.activity_new_game.*
import org.jetbrains.anko.custom.onUiThread
import org.jetbrains.anko.runOnUiThread
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.viewModel

class NewGameActivity : AppCompatActivity()
{
    private val newGameViewModel: NewGameViewModel by viewModel()
    private lateinit var playerAdapter: PlayerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
//             FIX: ~200ms on setContentView!
            val binding: ActivityNewGameBinding =
                DataBindingUtil.setContentView(this@NewGameActivity, R.layout.activity_new_game)
//            setContentView(R.layout.activity_new_game)
            binding.lifecycleOwner = this@NewGameActivity
            binding.viewModel = newGameViewModel
            initPlayerRecyclerView()
            newGameViewModel.boardPresenter.observe(this@NewGameActivity, Observer {
                playerAdapter.setPlayerItems(newGameViewModel.playerItems)
            })
            board_view.viewModel = newGameViewModel
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

    private fun initPlayerRecyclerView() {
        val adapter = PlayerAdapter(newGameViewModel.playerItems, get())
        adapter.boardPlayerVisibilitySubscription
            .subscribeFrom(newGameViewModel)
            .unsubscribeOnDestroy(this@NewGameActivity)
        adapter.boardPlayerHighlightingSubscription
            .subscribeFrom(newGameViewModel)
            .unsubscribeOnDestroy(this@NewGameActivity)
        players_recycler_view.adapter = adapter
        val callback: ItemTouchHelper.Callback = ItemMoveCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        adapter.itemTouchSubscription
            .subscribeFrom(itemTouchHelper)
            .unsubscribeOnDestroy(this@NewGameActivity)
        itemTouchHelper.attachToRecyclerView(players_recycler_view)
        playerAdapter = adapter
    }

    private fun startGame() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra(GAME_STATE_EXTRA, newGameViewModel.makeGameState())
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

