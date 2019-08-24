package com.pierbezuhoff.clonium.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/** Partially prevents faults after downscaling */
fun smoothBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
    // MAYBE: try using BitmapDrawable
    val size = (1.5 * targetSize).roundToInt() // xp-ed
    val scaled = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val sx = size.toFloat() / bitmap.width
    val sy = size.toFloat() / bitmap.height
    val matrix = Matrix()
    matrix.setScale(sx, sy, size/2f, size/2f)
    val canvas = Canvas(scaled)
    canvas.matrix = matrix
    canvas.drawBitmap(
        bitmap,
        size/2f - bitmap.width/2f,
        size/2f - bitmap.height/2f,
        Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
    )
    return scaled
}
