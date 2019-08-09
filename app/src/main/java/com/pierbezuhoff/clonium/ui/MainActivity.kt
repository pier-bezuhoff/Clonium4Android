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
import com.pierbezuhoff.clonium.utils.AndroidLogger
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.measureElapsedTimePretty
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity()
    , Logger by AndroidLogger("MainActivity")
{
    interface Callbacks {
        fun onTutorial(view: View)
        fun onNewGame(view: View)
        fun onBoardEditor(view: View)
    }
    private val callbacks = object : Callbacks {
        override fun onTutorial(view: View) {
            logI("onTutorial")
            val order = (0..3).map { PlayerId(it) }
            val board = PrimitiveBoard.Builder.fromString(
                """
                3³2³  □ □ 2³2²3⁰2³
                □ 3⁰1³1²3²2³2¹1¹3²
                □ □ 2¹3²1²2⁰3³3²3³
                  3³1¹3⁰□ 3¹2³3¹□ 
                1²2⁰3¹3³2²□ □ 2²1¹
                3²  □ 2¹3¹□ 3³1¹1⁰
                3³1³1¹1²  □ □ 2²2⁰
                3³1³  2²1³□ □ 2¹1⁰
                1³□ 3²2²1³2²3¹1⁰2¹
                """
            )
            val playerId = PlayerId3
            val (pretty, _) = measureElapsedTimePretty {
                with(LevelMaximizerBot(playerId, depth = 1)) {
                    runBlocking {
                        GlobalScope.makeTurnAsync(board, order).await()
                    }
                }
            }
            logI("lm1 thought $pretty")
            // TODO: check real-time maximizer speed
        }
        override fun onNewGame(view: View) {
            // TODO: optimize new game action: skipping ~30 frames!
            navigateToNewGameActivity()
        }
        override fun onBoardEditor(view: View) {
            logI("onBoardEditor")
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
            NEW_GAME_REQUEST_CODE -> logI("from GameActivity")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val NEW_GAME_REQUEST_CODE = 1
    }
}
