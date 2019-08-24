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
import org.koin.core.qualifier.named
import org.koin.dsl.module

@Suppress("RemoveExplicitTypeArguments")
val gameModule = module {
    single<Board.Builder> { SimpleBoard.Builder }
    single<EvolvingBoard.Builder> { PrimitiveBoard.Builder }

    factory<AssetManager> { androidContext().assets }
    single<GameBitmapLoader> { CommonGameBitmapLoader(get()) }

    single<Game.Builder> { AsyncGame.Builder }

    factory<BoardHighlighting> { MapBoardHighlighting() }

    factory<TransitionAnimationsHost> { TransitionAnimationsPool() }
    factory<BoardPresenter.Builder> { SimpleBoardPresenter.Builder(get(), get()) }
    factory<GamePresenter.Builder> { SimpleGamePresenter.Builder(get(), get(), get()) }

    viewModel<GameViewModel> { GameViewModel(get()) }
    viewModel<NewGameViewModel> { NewGameViewModel(get()) }

    single<GameGestures> { GameGestures(androidContext()) }
    single<NewGameBoardGestures> { NewGameBoardGestures(androidContext()) }
}


