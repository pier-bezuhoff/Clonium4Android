package com.pierbezuhoff.clonium.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.databinding.DataBindingUtil
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    interface Callbacks {
        fun onTutorial()
        fun onNewGame()
        fun onBoardEditor()
    }
    private val callbacks = object : Callbacks {
        override fun onTutorial() {
            Log.i(TAG, "onTutorial")
        }
        override fun onNewGame() {
            Log.i(TAG, "onNewGame")
        }
        override fun onBoardEditor() {
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

    companion object {
        private const val TAG = "MainActivity"
        private const val NEW_GAME_REQUEST_CODE = 1
    }
}
