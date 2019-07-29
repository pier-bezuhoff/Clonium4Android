package com.pierbezuhoff.clonium.ui.newgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.pierbezuhoff.clonium.R

/**
 * TODO: document your custom view class.
 */
class BoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    lateinit var viewModel: NewGameViewModel

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
