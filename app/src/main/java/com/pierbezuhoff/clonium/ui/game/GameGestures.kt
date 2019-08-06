package com.pierbezuhoff.clonium.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.pierbezuhoff.clonium.ui.meta.TapListener
import com.pierbezuhoff.clonium.utils.AndroidLogger
import com.pierbezuhoff.clonium.utils.Connection
import com.pierbezuhoff.clonium.utils.Logger

/** Listen to single tap */
class GameGestures (context: Context) : GestureDetector.SimpleOnGestureListener()
    , View.OnTouchListener
{
    private val gestureDetector = GestureDetector(context.applicationContext, this)

    private val tapConnection = Connection<TapListener>()
    val tapSubscription = tapConnection.subscription

    fun registerAsOnTouchListenerFor(view: View) {
        view.setOnTouchListener(this)
        gestureDetector.setOnDoubleTapListener(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        super.onDown(e)
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        tapConnection.send { onTap(e.x, e.y) }
        return super.onSingleTapConfirmed(e)
    }
}

