package com.pierbezuhoff.clonium.ui.game

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.*
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.utils.AndroidLogOf
import com.pierbezuhoff.clonium.utils.Milliseconds
import com.pierbezuhoff.clonium.utils.Once
import com.pierbezuhoff.clonium.utils.WithLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.get
import java.lang.Exception
import kotlin.concurrent.fixedRateTimer
// BUG: hangs on after 1-st turn
// MAYBE: invalidate every once in a while
class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr)
    , LifecycleOwner
    , KoinComponent
    , WithLog by AndroidLogOf<GameView>()
{
    lateinit var viewModel: GameViewModel // inject via data binding
    private val liveGameModel: LiveData<GameModel> by lazy { viewModel.gameModel }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private lateinit var size: Pair<Int, Int>
    private val firstSizeChanged by Once(true)

    private var ended = false
    private var lastUpdateTime: Long = 0L

    init {
        get<GameGestures>().registerAsOnTouchListenerFor(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        log i "onSizeChanged($w, $h, $oldw, $oldh)"
        size = Pair(width, height)
        if (firstSizeChanged) {
            onFirstRun()
        }
    }

    private fun onFirstRun() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        liveGameModel.observe(this, Observer {
            it.setSize(size.first, size.second)
        })
        scheduleUpdates()
    }

    private fun scheduleUpdates() {
        fixedRateTimer(period = UPDATE_TIME_DELTA) {
            postInvalidate()
            log i "postInvalidate"
            if (ended)
                cancel()
        }
//        viewModel.viewModelScope.launch {
//            while (isActive && !ended) {
//                postInvalidate()
//                delay(UPDATE_TIME_DELTA)
//            }
//        }
    }

    override fun onDraw(canvas: Canvas) {
        log i "onDraw"
        super.onDraw(canvas)
        if (!ended) {
            advance()
            try {
                liveGameModel.value?.draw(canvas)
            } catch (e: Exception) {
                e.printStackTrace()
                log w "include exception $e into silent catch"
            }
        }
    }

    private fun advance() {
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - lastUpdateTime
        if (timeDelta >= UPDATE_TIME_DELTA) {
            if (lastUpdateTime != 0L)
                liveGameModel.value?.advance(timeDelta)
            lastUpdateTime = currentTime
        }
    }

    fun onStop() {
        ended = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onRestart() {
        ended = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        scheduleUpdates()
    }

    companion object {
        private const val FPS = 60
        private const val UPDATE_TIME_DELTA: Milliseconds = 1_000L / FPS
    }
}
