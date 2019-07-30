package com.pierbezuhoff.clonium.ui.newgame

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityNewGameBinding
import com.pierbezuhoff.clonium.models.GameBitmapLoader
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
            players_recycler_view.adapter = PlayerAdapter(newGameViewModel.playerItems, get<GameBitmapLoader>())
        })
    }
}
