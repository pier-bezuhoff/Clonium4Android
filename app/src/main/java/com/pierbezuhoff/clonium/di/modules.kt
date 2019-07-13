package com.pierbezuhoff.clonium.di

import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.BitmapLoader
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.ui.game.GameGestures
import com.pierbezuhoff.clonium.ui.game.GameViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val gameModule = module {
    factory<EvolvingBoard> { (board: Board) -> PrimitiveBoard(board) }
    factory<Board> { (emptyBoard: EmptyBoard) -> SimpleBoard(emptyBoard) }
    single { BitmapLoader(androidContext().assets) }
    factory { (board: Board, bots: Set<Bot>, coroutineScope: CoroutineScope) ->
        GameModel(SimpleGame(get { parametersOf(board) }, bots), get(), coroutineScope) }
    factory(named("withOrder")) { (board: Board, bots: Set<Bot>, initialOrder: List<PlayerId>?, coroutineScope: CoroutineScope) ->
        GameModel(SimpleGame(get { parametersOf(board) }, bots, initialOrder), get(), coroutineScope)
    }
    viewModel { GameViewModel(get()) }
    single { GameGestures(androidContext()) }
}