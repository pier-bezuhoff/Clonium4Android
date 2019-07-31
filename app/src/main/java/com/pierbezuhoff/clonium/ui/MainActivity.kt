package com.pierbezuhoff.clonium.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.databinding.DataBindingUtil
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityMainBinding
import com.pierbezuhoff.clonium.ui.game.GameActivity
import com.pierbezuhoff.clonium.ui.newgame.NewGameActivity

class MainActivity : AppCompatActivity() {
    interface Callbacks {
        fun onTutorial(view: View)
        fun onNewGame(view: View)
        fun onBoardEditor(view: View)
    }
    private val callbacks = object : Callbacks {
        override fun onTutorial(view: View) {
            Log.i(TAG, "onTutorial")
        }
        override fun onNewGame(view: View) {
            Log.i(TAG, "onNewGame")
            navigateToNewGameActivity()
        }
        override fun onBoardEditor(view: View) {
            Log.i(TAG, "onBoardEditor")
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
        val intent = Intent(this, NewGameActivity::class.java)
        startActivityForResult(intent, NEW_GAME_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (resultCode) {
            NEW_GAME_REQUEST_CODE -> Log.i(TAG, "from GameActivity")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val NEW_GAME_REQUEST_CODE = 1
    }
}
