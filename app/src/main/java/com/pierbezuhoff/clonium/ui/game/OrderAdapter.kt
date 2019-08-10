package com.pierbezuhoff.clonium.ui.game

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.databinding.OrderItemBinding
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.utils.AndroidLogger
import com.pierbezuhoff.clonium.utils.AndroidLoggerOf
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.impossibleCaseOf

data class OrderItem(val player: Player) {
    val sumLevel = ObservableInt(0)
    val sumLevelText = object : ObservableField<String>(sumLevel) {
        override fun get() = sumLevel.get().toString()
    }
    val chipCount = ObservableInt(0)
    val chipCountText = object : ObservableField<String>(chipCount) {
        override fun get() = chipCount.get().toString()
    }
    val tacticDescription: String = when (player) {
        is BotPlayer -> player.difficultyName
        is HumanPlayer -> "Human"
        else -> impossibleCaseOf(player)
    }
    val alive = ObservableBoolean(true)
}

internal fun orderItemsOf(gameModel: GameModel): List<OrderItem> =
    with(gameModel.game) {
        order.map { OrderItem(it) }
    }

// TODO: highlight current player
class OrderAdapter(
    orderItems: List<OrderItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>()
    , GameModel.StatHolder
    , Logger by AndroidLoggerOf<OrderAdapter>()
{
    class ViewHolder(val binding: OrderItemBinding) : RecyclerView.ViewHolder(binding.root)

    var orderItems: List<OrderItem> = orderItems
        set(value) {
            field = value
            notifyDataSetChanged()
        }

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

    override fun updateStat(stat: GameStat) {
        for (orderItem in orderItems) {
            val (chipCount, sumLevel) = stat.getValue(orderItem.player)
            require(chipCount > 0 == sumLevel > 0)
            orderItem.chipCount.set(chipCount)
            orderItem.sumLevel.set(sumLevel)
            orderItem.alive.set(chipCount > 0)
        }
    }
}