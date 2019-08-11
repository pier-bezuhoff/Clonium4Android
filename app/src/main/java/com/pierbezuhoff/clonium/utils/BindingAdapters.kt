package com.pierbezuhoff.clonium.utils

import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.domain.PlayerTactic
import kotlin.math.roundToInt

@BindingAdapter("tactic")
fun tactic(textView: TextView, tactic: PlayerTactic) {
    with(textView.context) {
        textView.text = when (tactic) {
            PlayerTactic.Human -> getString(R.string.human)
            PlayerTactic.Bot.RandomPicker -> getString(R.string.random_picker)
            is PlayerTactic.Bot.LevelMaximizer -> getString(R.string.level_maximizer)
            is PlayerTactic.Bot.ChipCountMaximizer -> getString(R.string.chip_count_maximizer)
            is PlayerTactic.Bot.LevelMinimizer -> getString(R.string.level_minimizer)
            is PlayerTactic.Bot.LevelBalancer -> getString(R.string.level_balancer)
            is PlayerTactic.Bot.AlliedLevelBalancer -> getString(R.string.allied_level_balancer, tactic.allyId.id)
        }
    }
}

@BindingAdapter("verticalSpace")
fun verticalSpace(recyclerView: RecyclerView, verticalSpace: Float) {
    recyclerView.addItemDecoration(VerticalSpaceItemDecoration(verticalSpace = verticalSpace.roundToInt()))
}
