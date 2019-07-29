package com.pierbezuhoff.clonium.ui.newgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.utils.Once
import org.koin.core.KoinComponent
import org.koin.core.get

// MAYBE: intercept destroy-like event somehow
class BoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr)
    , LifecycleOwner
    , KoinComponent
{
    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private val firstSizeChanged by Once(true)

    lateinit var viewModel: NewGameViewModel

    init {
        get<NewGameBoardGestures>().registerAsOnTouchListenerFor(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.i("BoardView", "onSizeChanged($w, $h, $oldw, $oldh)")
        if (firstSizeChanged) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            viewModel.boardPresenter.observe(this, Observer {
                it.setSize(width, height)
            })
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        viewModel.boardPresenter.value?.let {
            with(it) { canvas.draw() }
        }
    }
}
