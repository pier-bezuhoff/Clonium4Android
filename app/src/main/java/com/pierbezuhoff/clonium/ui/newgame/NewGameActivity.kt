package com.pierbezuhoff.clonium.ui.newgame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityNewGameBinding
import com.pierbezuhoff.clonium.domain.Bot
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.ui.game.GameActivity
import kotlinx.android.synthetic.main.activity_new_game.*
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.viewModel

class NewGameActivity : AppCompatActivity() {
    private val newGameViewModel: NewGameViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityNewGameBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_new_game)
        binding.lifecycleOwner = this
        binding.viewModel = newGameViewModel

        newGameViewModel.boardPresenter.observe(this, Observer {
            @Suppress("RemoveExplicitTypeArguments")
            val adapter = PlayerAdapter(newGameViewModel.playerItems, get<GameBitmapLoader>())
            players_recycler_view.adapter = adapter
            val callback: ItemTouchHelper.Callback = ItemMoveCallback(adapter)
            ItemTouchHelper(callback).attachToRecyclerView(players_recycler_view)
        })
        cancel_button.setOnClickListener {
            onBackPressed()
        }
        start_game_button.setOnClickListener {
            startGame()
        }
    }

    private fun startGame() {
        val intent = Intent(this, GameActivity::class.java)
        val gameState = with(newGameViewModel) {
            Game.State(
                board,
                playerItems
                    .map { it.toPlayer() }
                    .filterIsInstance<Bot>()
                    .toSet(),
                if (useRandomOrder.value!!) null else playerItems.map { it.playerId }
            )
        }
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

