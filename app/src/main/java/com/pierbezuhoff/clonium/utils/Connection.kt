package com.pierbezuhoff.clonium.utils

import androidx.lifecycle.*
import java.lang.ref.WeakReference

class Connection<ListenerInterface> {
    private var listener: WeakReference<ListenerInterface>? = null
    val subscription: Subscription = Subscription()

    fun <Response> send(act: ListenerInterface.() -> Response): Response? {
        // callsInPlace(act, InvocationKind.AT_MOST_ONCE)
        return listener?.get()?.act()
    }

    /** [Subscription] to [Connection] is like [LiveData] to [MutableLiveData] */
    inner class Subscription internal constructor() {
        fun subscribeFrom(listener: ListenerInterface): Output {
            this@Connection.listener = WeakReference(listener)
            return Output()
        }
    }

    inner class Output internal constructor() {
        fun unsubscribe() {
            listener = null
        }

        fun unsubscribeOnDestroy(lifecycleOwner: LifecycleOwner) {
            lifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() { unsubscribe() }
            })
        }
    }
}


