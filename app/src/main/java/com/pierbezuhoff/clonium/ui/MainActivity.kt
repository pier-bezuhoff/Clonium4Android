package com.pierbezuhoff.clonium.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityMainBinding
import com.pierbezuhoff.clonium.domain.LevelMaximizerBot
import com.pierbezuhoff.clonium.domain.PlayerId
import com.pierbezuhoff.clonium.domain.PlayerId3
import com.pierbezuhoff.clonium.domain.PrimitiveBoard
import com.pierbezuhoff.clonium.ui.game.GameActivity
import com.pierbezuhoff.clonium.ui.newgame.NewGameActivity
import com.pierbezuhoff.clonium.ui.newgame.NewGameTabbedActivity
import com.pierbezuhoff.clonium.utils.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity()
    , WithLog by AndroidLogOf<MainActivity>()
{
    interface Callbacks {
        fun onTutorial(view: View)
        fun onNewGame(view: View)
        fun onBoardEditor(view: View)
    }
    private val callbacks = object : Callbacks {
        override fun onTutorial(view: View) {
            log i "onTutorial"
        }
        override fun onNewGame(view: View) {
            // TODO: optimize new game action: skipping ~30 frames!
            navigateToNewGameActivity()
        }
        override fun onBoardEditor(view: View) {
            log i "onBoardEditor"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.callbacks = callbacks
    }

    private fun navigateToExampleGameActivity() {
        val intent = Intent(this, GameActivity::class.java)
        startActivityForResult(intent, NEW_GAME_REQUEST_CODE)
    }

    private fun navigateToNewGameActivity() {
        val intent = Intent(this, NewGameTabbedActivity::class.java)
//        val intent = Intent(this, NewGameActivity::class.java)
        startActivityForResult(intent, NEW_GAME_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (resultCode) {
            NEW_GAME_REQUEST_CODE ->
                log i "from GameActivity"
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val NEW_GAME_REQUEST_CODE = 1
    }
}
