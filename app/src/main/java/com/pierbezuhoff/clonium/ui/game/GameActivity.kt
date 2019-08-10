package com.pierbezuhoff.clonium.ui.game

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityGameBinding
import com.pierbezuhoff.clonium.domain.Game
import com.pierbezuhoff.clonium.ui.newgame.NewGameActivity
import kotlinx.android.synthetic.main.activity_game.*
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.viewModel

// TODO: action bar
class GameActivity : AppCompatActivity() {
    private val gameViewModel: GameViewModel by viewModel()
    private var orderAdapter: OrderAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        val binding: ActivityGameBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_game)
        binding.lifecycleOwner = this
        binding.viewModel = gameViewModel
        (intent.getSerializableExtra(NewGameActivity.GAME_STATE_EXTRA) as Game.State?)?.let {
            gameViewModel.newGame(it)
        } ?: gameViewModel.newGame()

        gameViewModel.gameModel.observe(this, Observer { gameModel ->
            if (orderAdapter == null) {
                val adapter = OrderAdapter(orderItemsOf(gameModel), get())
                adapter.updateStat(gameModel.game.stat())
                gameModel.statUpdatingSubscription
                    .subscribeFrom(adapter)
                    .unsubscribeOnDestroy(this)
                gameModel.currentPlayerUpdatingSubscription
                    .subscribeFrom(adapter)
                    .unsubscribeOnDestroy(this)
                orderAdapter = adapter
                order_recycler_view.adapter = adapter
            } else {
                orderAdapter!!.orderItems = orderItemsOf(gameModel)
                orderAdapter!!.updateStat(gameModel.game.stat())
            }
        })
    }

    /** Enforce fullscreen sticky immersive mode */
    private fun setupWindow() {
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            }
        }
    }

    companion object {
        /** Distraction free mode */
        private const val IMMERSIVE_UI_VISIBILITY =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
