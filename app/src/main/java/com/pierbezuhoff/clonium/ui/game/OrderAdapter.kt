package com.pierbezuhoff.clonium.ui.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.databinding.OrderItemBinding
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.utils.impossibleCaseOf

class OrderItem(val player: Player) {
    val sumLevel: MutableLiveData<Int> = MutableLiveData(0)
    val chipCount: MutableLiveData<Int> = MutableLiveData(0)
    val tacticDescription: String = when (player) {
        is BotPlayer -> player.difficultyName
        is HumanPlayer -> "Human"
        else -> impossibleCaseOf(player)
    }
    val alive: MutableLiveData<Boolean> = MutableLiveData(true)
}

class OrderAdapter(
    private val orderItems: List<OrderItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>() {
    class ViewHolder(val binding: OrderItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            OrderItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent, false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = orderItems[position]
        holder.binding.orderItem = item
        // TODO: use chip without holes
        val chipBitmap = bitmapLoader.loadChip(Chip(item.player.playerId, Level1))
        holder.binding.chipView.setImageBitmap(chipBitmap)
    }

    override fun getItemCount(): Int =
        orderItems.size

    fun updateStat(stat: GameStat) {
        for (orderItem in orderItems) {
            val (chipCount, sumLevel) = stat.getValue(orderItem.player)
            orderItem.chipCount.value = chipCount
            orderItem.sumLevel.value = sumLevel
            orderItem.alive.value = chipCount > 0
        }
    }

    fun setSumLevel(playerId: PlayerId, newSumLevel: Int) {
        orderItems.find { it.player.playerId == playerId }!!.sumLevel.value = newSumLevel
    }

    fun setChipCount(playerId: PlayerId, newChipCount: Int) {
        orderItems.find { it.player.playerId == playerId }!!.chipCount.value = newChipCount
    }
}