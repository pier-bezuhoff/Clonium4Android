package com.pierbezuhoff.clonium.domain

import io.kotlintest.matchers.withClue
import io.kotlintest.specs.FreeSpec
import io.kotlintest.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import java.util.concurrent.Executors

@ExperimentalCoroutinesApi
class GameAndPlayersTest : FreeSpec() {
    init {
        val emulatedMainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(emulatedMainDispatcher)
        "run example games" - {
            "emulated players" {
                //
            }
            "RandomPickerBot-s" {
                //
            }

            "maximizers" {
                val board = BoardFactory.DEFAULT_1
                val bots = mapOf(
                    PlayerId0 to PlayerTactic.Bot.LevelMaximizer(depth = 1),
                    PlayerId1 to PlayerTactic.Bot.RandomPicker,
                    PlayerId2 to PlayerTactic.Bot.RandomPicker,
                    PlayerId3 to PlayerTactic.Bot.RandomPicker
                )
                val order = listOf(PlayerId0, PlayerId1, PlayerId2, PlayerId3)
                val state = Game.State(board, bots, order)
                val game = SimpleGame(state)
                with(game) {
                    repeat(100) {
                        withClue("turn $it, player = ${game.currentPlayer}, board = ${game.board}") {
                            runBlocking {
                                val transitions = botTurnAsync().await()
                                Unit
                            }
                        }
                    }
                }
            }
        }
    }
}