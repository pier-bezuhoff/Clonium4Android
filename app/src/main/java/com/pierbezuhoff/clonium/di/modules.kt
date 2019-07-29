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
    factory<EvolvingBoard> { (board: Board) -> PrimitiveBoard(board) }
    factory<Board> { (emptyBoard: EmptyBoard) -> SimpleBoard(emptyBoard) }

    single<GameBitmapLoader>(named(NAMES.GREEN)) { GreenGameBitmapLoader(androidContext().assets) }
    single<GameBitmapLoader>(named(NAMES.STANDARD)) { StandardGameBitmapLoader(androidContext().assets) }

    factory<Game>(named(NAMES.WITH_ORDER)) { (board: Board, bots: Set<Bot>, initialOrder: List<PlayerId>?) ->
        SimpleGame(get { parametersOf(board) }, bots, initialOrder) }
    factory<Game> { (board: Board, bots: Set<Bot>) ->
        get(named(NAMES.WITH_ORDER)) { parametersOf(board, bots, null) }
    }
    factory<Game>(named(NAMES.EXAMPLE)) { SimpleGame.example() }

    factory<TransitionAnimationsHost> { TransitionAnimationsPool() }
    factory<BoardPresenter> { (board: Board) -> SimpleBoardPresenter(board, get(named(NAMES.CHIP_SET))) }
    factory<GamePresenter> { (game: Game) -> SimpleGamePresenter(game, get(named(NAMES.CHIP_SET)), get(), get()) }

    viewModel<GameViewModel> { GameViewModel(get()) }
    viewModel<NewGameViewModel> { NewGameViewModel(get()) }

    single<GameGestures> { GameGestures(androidContext()) }
    single<NewGameBoardGestures> { NewGameBoardGestures(androidContext()) }
}

object NAMES {
    const val GREEN = "green"
    const val STANDARD = "standard"
    const val CHIP_SET = GREEN
    const val WITH_ORDER = "withOrder"
    const val EXAMPLE = "example"
}

