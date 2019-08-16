package com.pierbezuhoff.clonium.di

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
    single<ChipSymmetry> { ChipSymmetry.Two }
    single<Board.Builder> { SimpleBoard.Builder }
    single<EvolvingBoard.Builder> { PrimitiveBoard.Builder }

    single<GameBitmapLoader>(named(NAMES.GREEN)) { GreenGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader>(named(NAMES.STANDARD)) { StandardGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader>(named(NAMES.STAR)) { StarGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader>(named(NAMES.WHITE_STAR)) { WhiteStarGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader> { get(named(NAMES.GREEN)) }

    single<Game.Builder> { AsyncGame.Builder }

    factory<TransitionAnimationsHost> { TransitionAnimationsPool() }
    factory<BoardPresenter.Builder> { SimpleBoardPresenter.Builder(get()) }
    factory<GamePresenter.Builder> { SimpleGamePresenter.Builder(get(), get(), get()) }

    viewModel<GameViewModel> { GameViewModel(get()) }
    viewModel<NewGameViewModel> { NewGameViewModel(get()) }

    single<GameGestures> { GameGestures(androidContext()) }
    single<NewGameBoardGestures> { NewGameBoardGestures(androidContext()) }
}

object NAMES {
    const val GREEN = "green"
    const val STANDARD = "standard"
    const val STAR = "star"
    const val WHITE_STAR = "white star"
}

