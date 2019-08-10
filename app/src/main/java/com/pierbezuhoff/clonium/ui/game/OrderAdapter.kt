package com.pierbezuhoff.clonium.ui.game

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.databinding.OrderItemBinding
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.models.GameModel
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

internal fun orderItemsOf(gameModel: GameModel): List<OrderItem> =
    gameModel.game.players.values.map { OrderItem(it) }

// TODO: highlight current player
class OrderAdapter(
    var orderItems: List<OrderItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>()
    , GameModel.StatHolder
{
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

    override fun updateStat(stat: GameStat) {
        for (orderItem in orderItems) {
            val (chipCount, sumLevel) = stat.getValue(orderItem.player)
            orderItem.chipCount.value = chipCount
            orderItem.sumLevel.value = sumLevel
            orderItem.alive.value = chipCount > 0
        }
    }
}