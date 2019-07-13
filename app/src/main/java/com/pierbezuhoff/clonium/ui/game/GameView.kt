package com.pierbezuhoff.clonium.ui.game

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.utils.Once

class GameView(context: Context) : SurfaceView(context) {
    lateinit var viewModel: GameViewModel // inject via data binding

    init {
        holder.addCallback(SurfaceManager { viewModel.gameModel } )
    }
}

class SurfaceManager(liveGameModelInitializer: () -> LiveData<GameModel>) : Any()
    , SurfaceHolder.Callback
    , LifecycleOwner
{
    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private lateinit var drawThread: DrawThread
    private val firstSurfaceChange by Once(true)
    private lateinit var size: Pair<Int, Int>
    private val gameModel: LiveData<GameModel> by lazy(liveGameModelInitializer)

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        drawThread = DrawThread(gameModel, holder).apply {
            start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        Log.i("SurfaceManager", "surfaceChanged($holder, $format, $width, $height)")
        size = Pair(width, height)
        if (firstSurfaceChange)
            gameModel.observe(this, Observer {
                it.setSize(size.first, size.second)
            })
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        drawThread.ended = true
        drawThread.join() // NOTE: may throw some exceptions
    }
}

// MAYBE: it can be cancellable coroutine
// MAYBE: cr. interface Callback { fun advance(timeDelta: Long); draw(canvas: Canvas) }
class DrawThread(
    private val gameModel: LiveData<GameModel>,
    private val surfaceHolder: SurfaceHolder
) : Thread() {
    var ended = false
    private var lastUpdateTime: Long = 0L

    override fun run() {
        while (!ended) {
            val currentTime = System.currentTimeMillis()
            val timeDelta = currentTime - lastUpdateTime
            if (timeDelta >= UPDATE_TIME_DELTA) {
                if (lastUpdateTime != 0L)
                    gameModel.value?.advance(timeDelta)
                lastUpdateTime = currentTime
            }
            try {
                surfaceHolder.lockCanvas()?.let { canvas: Canvas ->
                    synchronized(surfaceHolder) {
                        gameModel.value?.drawCurrentBoard(canvas)
                    }
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w("DrawThread", "include exception $e into silent catch")
            }
        }
    }

    companion object {
        private const val FPS = 60
        private const val UPDATE_TIME_DELTA: Long = 1000L / FPS
    }
}