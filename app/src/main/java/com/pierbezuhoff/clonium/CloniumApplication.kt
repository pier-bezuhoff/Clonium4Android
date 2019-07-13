package com.pierbezuhoff.clonium

import android.app.Application
import com.pierbezuhoff.clonium.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CloniumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@CloniumApplication)
            modules(appModule)
        }
    }
}