package com.pierbezuhoff.clonium.ui.game

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.pierbezuhoff.clonium.models.AdvanceableDrawable
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.utils.*
import org.koin.core.KoinComponent
import org.koin.core.get
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class GameSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr)
    , KoinComponent
{
    lateinit var viewModel: GameViewModel // inject via data binding

    init {
        holder.addCallback(SurfaceManager { viewModel.gameModel } )
        get<GameGestures>().registerAsOnTouchListenerFor(this)
    }

    fun onRestart() { }
    fun onStop() { }
}

private class SurfaceManager(liveGameModelInitializer: () -> LiveData<GameModel>) : Any()
    , SurfaceHolder.Callback
    , LifecycleOwner
    , WithLog by AndroidLogOf<SurfaceManager>()
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
        log i "surfaceChanged($holder, $format, $width, $height)"
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

// TODO: profile and found cause of animation delay at the end of Sequence<Transition>
// MAYBE: it can be cancellable coroutine
// MAYBE: rewrap LiveData into Connection
internal class DrawThread(
    private val liveCallback: LiveData<out AdvanceableDrawable>,
    private val surfaceHolder: SurfaceHolder
) : Thread("surface-view-draw-thread")
    , WithLog by AndroidLogOf<DrawThread>()
{
    var ended = false
    private var lastUpdateTime: Long = 0L

    init {
        // NOTE: changing priority yield next to none improvements
        priority = threadGroup.maxPriority // should dominate async game/bots threads
    }

    override fun run() {
        var maybeCanvas: Canvas? = null
        val averageTimeDelta = AverageLong()
        while (!ended) {
            val currentTime = System.currentTimeMillis()
            val timeDelta = currentTime - lastUpdateTime
            if (timeDelta >= UPDATE_TIME_DELTA) {
                if (lastUpdateTime != 0L) {
                    liveCallback.value?.advance(timeDelta)
                    averageTimeDelta += timeDelta
                    val averagePart = averageTimeDelta.value?.let { " (average ${averageTimeDelta.value})" } ?: ""
                    log i "timeDelta = $timeDelta$averagePart"
                    if (timeDelta > MAX_UPDATE_TIME_DELTA)
                        log w "FPS < 24! (timeDelta = $timeDelta)"
                }
                lastUpdateTime = currentTime
            }
            try {
                surfaceHolder.lockCanvas()?.also { canvas: Canvas ->
                    maybeCanvas = canvas
                    synchronized(surfaceHolder) {
                        // FIX: draw takes 25ms - 100ms >> 16ms for 60 FPS!
//                        log i elapsedTime(prefix = "on draw:", startMarker = null) {
                            liveCallback.value?.draw(canvas)
//                        }
                    }
                }
            } catch (e: IllegalArgumentException) { // surface already locked
            } catch (e: Exception) {
                e.printStackTrace()
                log w "include exception $e into silent catch"
            } finally {
                maybeCanvas?.let {
                    try {
                        surfaceHolder.unlockCanvasAndPost(it)
                    } catch (e: IllegalStateException) { // surface was not locked
                    } finally {
                        maybeCanvas = null
                    }
                }
            }
        }
    }
}

private const val FPS = 60
private const val MIN_FPS = 24
private const val UPDATE_TIME_DELTA: Milliseconds = 1000L / FPS
private const val MAX_UPDATE_TIME_DELTA: Milliseconds = 1000L / MIN_FPS
