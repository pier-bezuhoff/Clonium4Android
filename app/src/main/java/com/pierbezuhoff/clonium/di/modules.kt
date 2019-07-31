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
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

@Suppress("RemoveExplicitTypeArguments")
val gameModule = module {
    single<ChipSymmetry> { ChipSymmetry.None }
    factory<Board> { (emptyBoard: EmptyBoard) -> SimpleBoard(emptyBoard) }
    factory<EvolvingBoard> { (board: Board) -> PrimitiveBoard(board) }

    single<GameBitmapLoader>(named(NAMES.GREEN)) { GreenGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader>(named(NAMES.STANDARD)) { StandardGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader> { get(named(NAMES.GREEN)) }

    factory<Game> { (gameState: Game.State) -> SimpleGame(gameState) }
    factory<Game>(named(NAMES.EXAMPLE)) { SimpleGame.example() }

    factory<TransitionAnimationsHost> { TransitionAnimationsPool() }
    factory<BoardPresenter> { (board: Board) -> SimpleBoardPresenter(board, get()) }
    factory<GamePresenter> { (game: Game) -> SimpleGamePresenter(game, get(), get(), get()) }

    viewModel<GameViewModel> { GameViewModel(get()) }
    viewModel<NewGameViewModel> { NewGameViewModel(get()) }

    single<GameGestures> { GameGestures(androidContext()) }
    single<NewGameBoardGestures> { NewGameBoardGestures(androidContext()) }
}

object NAMES {
    const val GREEN = "green"
    const val STANDARD = "standard"
    const val WITH_ORDER = "withOrder"
    const val EXAMPLE = "example"
}

