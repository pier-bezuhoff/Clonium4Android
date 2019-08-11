package com.pierbezuhoff.clonium.ui.meta

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import org.koin.core.KoinComponent

abstract class CloniumAndroidViewModel(application: Application) : AndroidViewModel(application)
    , KoinComponent
{
    /** Application context */
    protected val context: Context
        get() = getApplication<Application>().applicationContext
}