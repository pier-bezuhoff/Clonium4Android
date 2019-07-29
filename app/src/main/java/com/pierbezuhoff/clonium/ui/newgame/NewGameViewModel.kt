package com.pierbezuhoff.clonium.ui.newgame

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.clonium.models.BoardPresenter
import com.pierbezuhoff.clonium.ui.meta.CloniumAndroidViewModel

class NewGameViewModel(application: Application) : CloniumAndroidViewModel(application) {
    private val _boardPresenter: MutableLiveData<BoardPresenter> = MutableLiveData()
    val boardPresenter: LiveData<BoardPresenter> = _boardPresenter
}