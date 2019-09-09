package com.pierbezuhoff.clonium.ui.newgame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityNewGameBinding
import com.pierbezuhoff.clonium.ui.game.GameActivity
import com.pierbezuhoff.clonium.utils.AndroidLoggerOf
import com.pierbezuhoff.clonium.utils.Logger
import kotlinx.android.synthetic.main.activity_new_game.*
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.viewModel

class NewGameActivity : AppCompatActivity()
    , Logger by AndroidLoggerOf<NewGameActivity>()
{
    private val newGameViewModel: NewGameViewModel by viewModel()
    private lateinit var playerAdapter: PlayerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
        withMilestoneScope("onCreate", measureScope = true) {
//            FIX: ~200ms on setContentView!
            val binding: ActivityNewGameBinding =
                DataBindingUtil.setContentView(this@NewGameActivity, R.layout.activity_new_game)
            - "setContentView"
            binding.lifecycleOwner = this@NewGameActivity
            binding.viewModel = newGameViewModel
            - "set lifecycleOwner and viewModel"
            initPlayerRecyclerView()
            - "init recyclerview"
            newGameViewModel.boardPresenter.observe(this@NewGameActivity, Observer {
                playerAdapter.setPlayerItems(newGameViewModel.playerItems)
            })
            - "observe boardPresenter"
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
            - "set up callbacks"
        }
    }

    private fun initPlayerRecyclerView() {
        val adapter = PlayerAdapter(
            newGameViewModel.playerItems,
            get(),
            newGameViewModel.chipSet,
            newGameViewModel.colorPrism
        )
        adapter.boardPlayerVisibilitySubscription
            .subscribeFrom(newGameViewModel)
            .unsubscribeOnDestroy(this@NewGameActivity)
        adapter.boardPlayerHighlightingSubscription
            .subscribeFrom(newGameViewModel)
            .unsubscribeOnDestroy(this@NewGameActivity)
        adapter.boardViewInvalidatingSubscription
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
        newGameViewModel.saveConfig()
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra(GAME_STATE_EXTRA, newGameViewModel.mkGameState())
        startActivityForResult(intent, GAME_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GAME_REQUEST_CODE -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        newGameViewModel.saveConfig()
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        newGameViewModel.saveConfig()
        super.onStop()
    }

    companion object {
        private const val packagePrefix = "com.pierbezuhoff.clonium.ui.newgame."
        const val GAME_STATE_EXTRA = packagePrefix + "gameState"
        private const val GAME_REQUEST_CODE = 1
    }
}

