package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import androidx.core.graphics.*
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.*
import com.pierbezuhoff.clonium.utils.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ChipAnimation {
    ROTATION, SLIDE
}

fun animatedAdvancerOf(
    chipsConfig: ChipsConfig,
    bitmapLoader: GameBitmapLoader,
    gamePresenter: GamePresenter,
    turn: Pos,
    transitions: Sequence<Transition>
): AnimatedAdvancer<WithProgress<TransitionStep>> {
    val (chipAnimation, chipSet, colorPrism) = chipsConfig
    val params = AnimationParams(chipSet, colorPrism, gamePresenter, bitmapLoader)
    return when (chipAnimation) {
        ChipAnimation.ROTATION -> AnimatedRotationAdvancer(turn, transitions, params)
        ChipAnimation.SLIDE -> AnimatedSlideAdvancer(turn, transitions, params)
    }
}

private data class AnimationParams(
    val chipSet: ChipSet,
    val colorPrism: ColorPrism,
    val gamePresenter: GamePresenter,
    val bitmapLoader: GameBitmapLoader
)


private class AnimatedRotationAdvancer(
    turn: Pos,
    transitions: Sequence<Transition>,
    val params: AnimationParams
) : AnimatedAdvancer<WithProgress<TransitionStep>>(
    AdvancerBuilder.of(
        turn, transitions,
        useSwiftRotations = params.chipSet.symmetry !is ChipSymmetry.Four
    )
)
    , WithLog by AndroidLogOf<AnimatedRotationAdvancer>(minLogLevel = Logger.Level.WARNING)
{
    override fun Canvas.drawOne(output: WithProgress<TransitionStep>) {
        log i elapsedTime(output.value.javaClass.simpleName, startMarker = null) {
            @Suppress("UNCHECKED_CAST")
            when (output.value) {
                is ExplosionsStep -> drawFlippingExplosions(
                    params,
                    output as WithProgress<ExplosionsStep>
                )
                is SwiftRotationsStep -> drawSwiftRotations(
                    params,
                    output as WithProgress<SwiftRotationsStep>
                )
                is IdleStep -> drawIdle(params, output as WithProgress<IdleStep>)
                is FalloutsStep -> drawDirectedFallouts(
                    params,
                    output as WithProgress<FalloutsStep>
                )
                is MadeTurnStep -> drawMadeTurn(params, output as WithProgress<MadeTurnStep>)
            }
        }
    }
}

private class AnimatedSlideAdvancer(
    turn: Pos,
    transitions: Sequence<Transition>,
    val params: AnimationParams
) : AnimatedAdvancer<WithProgress<TransitionStep>>(
    AdvancerBuilder.of(turn, transitions, useSwiftRotations = false)
) {
    override fun Canvas.drawOne(output: WithProgress<TransitionStep>) =
        @Suppress("UNCHECKED_CAST")
        when(output.value) {
            is ExplosionsStep -> drawSlidingExplosions(params, output as WithProgress<ExplosionsStep>)
            is IdleStep -> drawIdle(params, output as WithProgress<IdleStep>)
            is FalloutsStep -> drawUnidirectionalFallouts(params, output as WithProgress<FalloutsStep>)
            is MadeTurnStep -> drawMadeTurn(params, output as WithProgress<MadeTurnStep>)
            else -> impossibleCaseOf(output.value)
        }
}

private const val pi = PI.toFloat()

private fun Canvas.drawFlippingExplosions(params: AnimationParams, progressingExplosions: WithProgress<ExplosionsStep>) {
    val (chipSet, colorPrism, gamePresenter, bitmapLoader) = params
    val (explosions, progress) = progressingExplosions
    with(gamePresenter) {
        drawBoard(explosions.boardState)
        for ((pos, playerId) in explosions.places) {
            val chip = Chip(playerId, Level1)
            val bitmapTop = bitmapLoader.loadChip(chipSet, colorPrism, chip)
            val bitmapBottom = bitmapLoader.loadBottomOfChip(chipSet, colorPrism, chip)
            val startPoint = pos2point(pos)
            val jumpLength: Float = cellSize.toFloat()
            val bitmap =
                if (progress <= 0.25 || progress >= 0.75) bitmapTop
                else bitmapBottom // when cos(phi) = horizontalSqueeze < 0
            val rescaleMatrix = rescaleMatrix(bitmap)
            val alpha = pi * progress
            // coordinates of chip center
            val r = jumpLength * (1 - cos(alpha)) / 2f
            val z = jumpHeight * sin(alpha)
            val zScale = 1 + z * zZoom
            // angle between chip normal and z axis
            val phi = 2 * pi * progress // complete coup
            val horizontalSqueeze = cos(phi) // negative means upside-down
            for (theta in listOf(0f, 90f, 180f, 270f)) {
                // we construct right explosion, then rotate it by theta
                val point = PointF((startPoint.x + r).toFloat(), startPoint.y.toFloat())
                val rotateMatrix =
                    rotationMatrix(
                        theta,
                        startPoint.x + cellSize / 2f,
                        startPoint.y + cellSize / 2f
                    )
                val centeredScaleMatrix = centeredScaleMatrix(
                    bitmap,
                    (horizontalSqueeze * chipCellRatio * zScale).toFloat(),
                    (chipCellRatio * zScale).toFloat()
                )
                val translateMatrix = translationMatrix(point.x, point.y)
                drawBitmap(
                    bitmap,
                    rotateMatrix * translateMatrix * rescaleMatrix * centeredScaleMatrix,
                    bitmapPaint
                )
            }
        }
    }
}

private fun Canvas.drawSlidingExplosions(params: AnimationParams, progressingExplosions: WithProgress<ExplosionsStep>) {
    val (chipSet, colorPrism, gamePresenter, bitmapLoader) = params
    val (explosions, progress) = progressingExplosions
    with(gamePresenter) {
        drawBoard(explosions.boardState)
        for ((pos, playerId) in explosions.places) {
            val chip = Chip(playerId, Level1)
            val bitmap = bitmapLoader.loadChip(chipSet, colorPrism, chip)
            val startPoint = pos2point(pos)
            val jumpLength: Float = cellSize.toFloat()
            val rescaleMatrix = rescaleMatrix(bitmap)
            val alpha = pi * progress
            // MAYBE: use linear slide
            val r = (jumpLength * (1 - cos(alpha)) / 2f).toFloat()
            val z = jumpHeight * sin(alpha)
            val zScale = 1 + z * zZoom
            val x = startPoint.x.toFloat()
            val y = startPoint.y.toFloat()
            val points = listOf(
                PointF(x + r, y), PointF(x, y - r), PointF(x - r, y), PointF(x, y + r)
            )
            for (point in points) {
                val centeredScaleMatrix = centeredScaleMatrix(
                    bitmap,
                    (chipCellRatio * zScale).toFloat()
                )
                val translateMatrix = translationMatrix(point.x, point.y)
                drawBitmap(
                    bitmap,
                    translateMatrix * rescaleMatrix * centeredScaleMatrix,
                    bitmapPaint
                )
            }
        }
    }
}

private val angles: Map<Direction, Float> = mapOf(
    Direction.Right to 0f,
    Direction.Down to 90f,
    Direction.Left to 180f,
    Direction.Up to 270f
)

private fun Canvas.drawSwiftRotations(params: AnimationParams, progressingSwiftRotations: WithProgress<SwiftRotationsStep>) {
    val (chipSet, colorPrism, gamePresenter, bitmapLoader) = params
    val (swiftRotations, progress) = progressingSwiftRotations
    with(gamePresenter) {
        drawBoard(swiftRotations.boardState)
        for ((pos, playerIdAndDirection) in swiftRotations.places) {
            val (playerId, direction) = playerIdAndDirection
            val bitmap = bitmapLoader.loadChip(chipSet, colorPrism, Chip(playerId, Level1))
            val rescaleMatrix = rescaleMatrix(bitmap)
            val centeredScaleMatrix = centeredScaleMatrix(bitmap, chipCellRatio)
            val translateMatrix = pos2translationMatrix(pos)
            val angle0 = angles.getValue(direction) // 0f <= angle0 < 360f
            val angle = when (chipSet.symmetry) {
                is ChipSymmetry.None -> if (angle0 <= 180f) angle0 else angle0 - 360f
                is ChipSymmetry.Two -> angle0 % 180f
                is ChipSymmetry.Four -> throw IllegalStateException("There is no sense in rotating 4-symmetric chip")
            }
            val theta = (angle * (1 - progress)).toFloat()
            val centeredRotateMatrix = centeredRotateMatrix(bitmap, theta)
            drawBitmap(
                bitmap,
                translateMatrix * rescaleMatrix * centeredScaleMatrix * centeredRotateMatrix,
                bitmapPaint
            )
        }
    }
}

private fun Canvas.drawIdle(params: AnimationParams, progressingIdle: WithProgress<IdleStep>) {
    val (_, _, gamePresenter, _) = params
    val (idle, _) = progressingIdle
    with(gamePresenter) {
        drawBoard(idle.boardState)
    }
}

private fun Canvas.drawDirectedFallouts(params: AnimationParams, progressingFallouts: WithProgress<FalloutsStep>) {
    val (chipSet, colorPrism, gamePresenter, bitmapLoader) = params
    val (fallouts, progress) = progressingFallouts
    with(gamePresenter) {
        for ((pos, chipAndDirection) in fallouts.places) {
            val (chip, direction) = chipAndDirection
            val bitmap = bitmapLoader.loadChip(chipSet, colorPrism, chip)
            val rescaleMatrix = rescaleMatrix(bitmap)
            val translateMatrix = pos2translationMatrix(pos)
            val phi = (angles.getValue(direction) + falloutAngularSpeed * progress).toFloat()
            val centeredRotateMatrix = centeredRotateMatrix(bitmap, phi)
            val zScale = 1 - falloutVerticalSpeed * progress * zZoom
            val centeredScaleMatrix = centeredScaleMatrix(
                bitmap,
                (chipCellRatio * zScale).toFloat()
            )
            drawBitmap(
                bitmap,
                translateMatrix * rescaleMatrix * centeredScaleMatrix * centeredRotateMatrix,
                bitmapPaint
            )
        }
    }
}

private fun Canvas.drawUnidirectionalFallouts(params: AnimationParams, progressingFallouts: WithProgress<FalloutsStep>) {
    val (chipSet, colorPrism, gamePresenter, bitmapLoader) = params
    val (fallouts, progress) = progressingFallouts
    with(gamePresenter) {
        for ((pos, chipAndDirection) in fallouts.places) {
            val (chip, _) = chipAndDirection
            val bitmap = bitmapLoader.loadChip(chipSet, colorPrism, chip)
            val rescaleMatrix = rescaleMatrix(bitmap)
            val translateMatrix = pos2translationMatrix(pos)
            val phi = (falloutAngularSpeed * progress).toFloat()
            val centeredRotateMatrix = centeredRotateMatrix(bitmap, phi)
            val zScale = 1 - falloutVerticalSpeed * progress * zZoom
            val centeredScaleMatrix = centeredScaleMatrix(
                bitmap,
                (chipCellRatio * zScale).toFloat()
            )
            drawBitmap(
                bitmap,
                translateMatrix * rescaleMatrix * centeredScaleMatrix * centeredRotateMatrix,
                bitmapPaint
            )
        }
    }
}

private fun Canvas.drawMadeTurn(params: AnimationParams, progressingMadeTurn: WithProgress<MadeTurnStep>) {
    val (_, _, gamePresenter, bitmapLoader) = params
    val (step, progress) = progressingMadeTurn
    val pos = step.place
    val madeTurnBitmap = bitmapLoader.loadMadeTurn()
    with(gamePresenter) {
        val rescaleMatrix = rescaleMatrix(madeTurnBitmap)
        val translateMatrix = pos2translationMatrix(pos)
        val initialAngularSpeed = 2 * madeTurnMeanAngularSpeed
        val phi = (initialAngularSpeed * (progress - progress * progress / 2f)).toFloat()
        val centeredRotateMatrix = centeredRotateMatrix(madeTurnBitmap, phi)
        val scale = (1 + madeTurnGrowth * progress).toFloat()
        val centeredScaleMatrix = centeredScaleMatrix(madeTurnBitmap, scale)
        val maxAlpha = 255
        drawBitmap(
            madeTurnBitmap,
            translateMatrix * rescaleMatrix * centeredScaleMatrix * centeredRotateMatrix,
            Paint(bitmapPaint).apply {
                alpha = (maxAlpha * (1 - progress)).roundToInt()
            }
        )
    }
}

