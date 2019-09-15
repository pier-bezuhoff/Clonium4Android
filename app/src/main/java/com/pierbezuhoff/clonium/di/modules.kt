package com.pierbezuhoff.clonium.di

import android.content.res.AssetManager
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.*
import com.pierbezuhoff.clonium.models.animation.TransitionAnimationsHost
import com.pierbezuhoff.clonium.models.animation.TransitionAnimationsPool
import com.pierbezuhoff.clonium.ui.game.GameGestures
import com.pierbezuhoff.clonium.ui.game.GameViewModel
import com.pierbezuhoff.clonium.ui.newgame.NewGameBoardGestures
import com.pierbezuhoff.clonium.ui.newgame.NewGameViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

@Suppress("RemoveExplicitTypeArguments")
val gameModule = module {
    single<Board.Factory> { SimpleBoard.Factory }
    single<EvolvingBoard.Factory> { PrimitiveBoard.Factory }

    factory<AssetManager> { androidContext().assets }
    single<GameBitmapLoader> { CommonGameBitmapLoader(get()) }

    single<Game.Factory> { AsyncGame.Factory }

    factory<BoardHighlighting> { MapBoardHighlighting() }

    factory<TransitionAnimationsHost> { TransitionAnimationsPool() }
    factory<BoardPresenter.Factory> { SimpleBoardPresenter.Factory(get(), get()) }
    factory<GamePresenter.Factory> { SimpleGamePresenter.Factory(get(), get(), get()) }

    viewModel<GameViewModel> { GameViewModel(get()) }
    viewModel<NewGameViewModel> { NewGameViewModel(get()) }

    single<GameGestures> { GameGestures(androidContext()) }
    single<NewGameBoardGestures> { NewGameBoardGestures(androidContext()) }
}


