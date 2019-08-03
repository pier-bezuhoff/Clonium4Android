package com.pierbezuhoff.clonium.utils

import android.util.Log

interface Logger {
    fun log(message: String)
    fun log(tag: String, message: String)
}

object StandardLogger : Logger {
    override fun log(message: String) {
        println(message)
    }
    override fun log(tag: String, message: String) {
        println("$tag: $message")
    }
}

object NoLogger : Logger {
    override fun log(message: String) { }
    override fun log(tag: String, message: String) { }
}

object AndroidLogger : Logger {
    override fun log(message: String) {
        Log.i("AndroidLogger", message)
    }

    override fun log(tag: String, message: String) {
        Log.i(tag, message)
    }
}