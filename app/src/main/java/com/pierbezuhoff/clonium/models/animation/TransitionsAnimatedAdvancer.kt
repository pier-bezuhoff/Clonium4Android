package com.pierbezuhoff.clonium.models.animation

import android.graphics.Canvas
import android.graphics.PointF
import androidx.core.graphics.rotationMatrix
import androidx.core.graphics.times
import androidx.core.graphics.translationMatrix
import com.pierbezuhoff.clonium.domain.Chip
import com.pierbezuhoff.clonium.domain.Level1
import com.pierbezuhoff.clonium.domain.Transition
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.models.GamePresenter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class TransitionsAnimatedAdvancer(
    transitions: Sequence<Transition>,
    private val gamePresenter: GamePresenter,
    private val bitmapLoader: GameBitmapLoader
) : AnimatiedAdvancer<WithProgress<TransitionStep>>(
    TransitionsAdvancer.make(transitions)
) {
    @Suppress("UNCHECKED_CAST")
    override fun Canvas.drawOne(output: WithProgress<TransitionStep>) {
        when(output.value) {
            is ExplosionsStep -> drawExplosions(output as WithProgress<ExplosionsStep>)
            is SwiftRotationsStep -> drawSwiftRotations(output as WithProgress<SwiftRotationsStep>)
            is IdleStep -> drawIdle(output as WithProgress<IdleStep>)
            is FalloutsStep -> drawFallouts(output as WithProgress<FalloutsStep>)
        }
    }

    private fun Canvas.drawExplosions(progressingExplosions: WithProgress<ExplosionsStep>) {
        val (explosions, progress) = progressingExplosions
        with(gamePresenter) {
            drawBoard(explosions.boardState)
            for ((pos, playerId) in explosions.places) {
                val chip = Chip(playerId, Level1)
                val bitmapTop = bitmapLoader.loadChip(chip)
                val bitmapBottom = bitmapLoader.loadBottomOfChip(chip)
                val startPoint = pos2point(pos)
                val jumpLength: Float = cellSize.toFloat()
                val bitmap =
                    if (progress <= 0.25 || progress >= 0.75) bitmapTop
                    else bitmapBottom // when cos(phi) = horizontalSqueeze < 0
                val rescaleMatrix = rescaleMatrix(bitmap)
                val alpha = PI * progress
                // coordinates of chip center
                val r = jumpLength * (1 - cos(alpha)) / 2f
                val z = jumpHeight * sin(alpha)
                val zScale = 1 + z * zZoom
                // angle between chip normal pAndP z axis
                val phi = 2 * PI * progress // complete coup
                val horizontalSqueeze = cos(phi) // negative means upside-down
                for (theta in listOf(0f, 90f, 180f, 270f)) {
                    // we construct right explosion, sThenS rotate it by theta
                    val point = PointF((startPoint.x + r).toFloat(), startPoint.y.toFloat())
                    val rotateMatrix =
                        rotationMatrix(theta, startPoint.x + cellSize / 2f, startPoint.y + cellSize / 2f)
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

    private fun Canvas.drawSwiftRotations(progressingSwiftRotations: WithProgress<SwiftRotationsStep>) {
        val (swiftRotations, progress) = progressingSwiftRotations
        with(gamePresenter) {
            drawBoard(swiftRotations.boardState)
            TODO("copy from respective branch")
        }
    }

    private fun Canvas.drawIdle(progressingIdle: WithProgress<IdleStep>) {
        val (idle, _) = progressingIdle
        with(gamePresenter) {
            drawBoard(idle.boardState)
        }
    }

    private fun Canvas.drawFallouts(progressingFallouts: WithProgress<FalloutsStep>) {
        val (fallouts, progress) = progressingFallouts
        with(gamePresenter) {
            for ((pos, playerId) in fallouts.places) {
                val bitmap = bitmapLoader.loadChip(Chip(playerId, Level1))
                val rescaleMatrix = rescaleMatrix(bitmap)
                val translateMatrix = pos2translationMatrix(pos)
                val phi = (falloutAngleSpeed * progress).toFloat()
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
}