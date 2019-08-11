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
import kotlinx.android.synthetic.main.activity_new_game.*
import org.jetbrains.anko.custom.onUiThread
import org.jetbrains.anko.runOnUiThread
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
                adapter.boardPlayerVisibilitySubscription
                    .subscribeFrom(newGameViewModel)
                    .unsubscribeOnDestroy(this)
                adapter.boardPlayerHighlightingSubscription
                    .subscribeFrom(newGameViewModel)
                    .unsubscribeOnDestroy(this)
                players_recycler_view.adapter = adapter
                val verticalSpace = 6
                players_recycler_view.addItemDecoration(
                    object : RecyclerView.ItemDecoration() {
                        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
//                            if (parent.getChildAdapterPosition(view) != parent.adapter!!.itemCount - 1)
                                outRect.bottom = verticalSpace
                        }
                    }
                )
                val callback: ItemTouchHelper.Callback = ItemMoveCallback(adapter)
                val itemTouchHelper = ItemTouchHelper(callback)
                adapter.itemTouchSubscription
                    .subscribeFrom(itemTouchHelper)
                    .unsubscribeOnDestroy(this)
                itemTouchHelper.attachToRecyclerView(players_recycler_view)
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

