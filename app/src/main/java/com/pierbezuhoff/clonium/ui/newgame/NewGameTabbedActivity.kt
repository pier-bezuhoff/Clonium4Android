package com.pierbezuhoff.clonium.ui.newgame

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.NewGamePlayersBinding
import com.pierbezuhoff.clonium.ui.game.GameActivity
import com.pierbezuhoff.clonium.utils.AndroidLogOf
import com.pierbezuhoff.clonium.utils.WithLog
import kotlinx.android.synthetic.main.activity_new_game_tabbed.*
import kotlinx.android.synthetic.main.new_game_players.view.*
import org.koin.android.ext.android.get
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel

class NewGameTabbedActivity : AppCompatActivity()
    , WithLog by AndroidLogOf<NewGameTabbedActivity>()
{
    private val newGameViewModel: NewGameViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_game_tabbed)
        val tabAdapter = TabFragmentAdapter(supportFragmentManager)
        view_pager.adapter = tabAdapter
        tabs.setupWithViewPager(view_pager)
        tabs.newTab().text = "Players"
        cancel_button.setOnClickListener {
            onBackPressed()
        }
        start_game_button.setOnClickListener {
            startGame()
        }
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
            GAME_REQUEST_CODE -> finish() // return to main menu when back from game
        }
    }

    companion object {
        private const val packagePrefix = "com.pierbezuhoff.clonium.ui.newgame."
        const val GAME_STATE_EXTRA = packagePrefix + "gameState"
        private const val GAME_REQUEST_CODE = 1
    }
}

class PlayersFragment : Fragment()
    , WithLog by AndroidLogOf<PlayersFragment>()
{
    private val newGameViewModel: NewGameViewModel by sharedViewModel()
    private lateinit var playerAdapter: PlayerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = NewGamePlayersBinding.inflate(
            inflater,
            container,
            false
        )
        binding.lifecycleOwner = this
        binding.viewModel = newGameViewModel
        val root = binding.root
        initPlayersRecyclerView(root)
        newGameViewModel.boardPresenter.observe(this, Observer {
            playerAdapter.setPlayerItems(newGameViewModel.playerItems)
        })
        return root
    }

    private fun initPlayersRecyclerView(root: View) {
        val adapter = PlayerAdapter(
            newGameViewModel.playerItems,
            get(),
            newGameViewModel.chipSet,
            newGameViewModel.colorPrism
        )
        adapter.boardPlayerVisibilitySubscription.passTo(this, newGameViewModel)
        adapter.boardPlayerHighlightingSubscription.passTo(this, newGameViewModel)
        adapter.boardViewInvalidatingSubscription.passTo(this, newGameViewModel)
        root.players_recycler_view.adapter = adapter // <- most time consuming
        val callback: ItemTouchHelper.Callback = ItemMoveCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        adapter.itemTouchSubscription.passTo(this, itemTouchHelper)
        itemTouchHelper.attachToRecyclerView(root.players_recycler_view)
        playerAdapter = adapter
    }

    override fun onSaveInstanceState(outState: Bundle) {
        newGameViewModel.saveConfig()
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        newGameViewModel.saveConfig()
        super.onStop()
    }
}
