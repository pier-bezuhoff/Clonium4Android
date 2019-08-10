package com.pierbezuhoff.clonium.ui.game

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.*
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.databinding.OrderItemBinding
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.models.GameModel
import com.pierbezuhoff.clonium.utils.AndroidLoggerOf
import com.pierbezuhoff.clonium.utils.Logger
import com.pierbezuhoff.clonium.utils.impossibleCaseOf
import kotlin.math.roundToInt

data class OrderItem(val player: Player) {
    internal val stat = ObservableField(Game.PlayerStat(0, 0, 0.0))
    val sumLevel = stat.projection {
        if (it.sumLevel == 0) "" else "${it.sumLevel}"
    }
    val chipCount = stat.projection {
        if (it.chipCount == 0) "" else "${it.chipCount}"
    }
    val conqueredPercent = stat.projection {
        if (it.conquered == 0.0) "" else "${(it.conquered * 100).roundToInt()}%"
    }
    val tacticDescription: String = when (player) {
        is BotPlayer -> player.difficultyName
        is HumanPlayer -> "Human"
        else -> impossibleCaseOf(player)
    }
    val highlight = ObservableBoolean(false)
    val alive = ObservableBoolean(true)
}

private inline fun <reified S : Any, reified T> ObservableField<S>.projection(
    crossinline project: (S) -> T
): ObservableField<T> =
    object : ObservableField<T>(this) {
        override fun get(): T? = this@projection.get()?.let(project)
    }

internal fun orderItemsOf(gameModel: GameModel): List<OrderItem> =
    with(gameModel.game) {
        order.map { OrderItem(it) }
    }

class OrderAdapter(
    orderItems: List<OrderItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>()
    , GameModel.StatHolder
    , GameModel.CurrentPlayerHolder
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

    override fun updateStat(gameStat: GameStat) {
        for (orderItem in orderItems) {
            val stat = gameStat.getValue(orderItem.player)
            orderItem.stat.set(stat)
            orderItem.alive.set(stat.chipCount > 0)
        }
    }

    override fun updateCurrentPlayer(player: Player) {
        for (orderItem in orderItems) {
            if (orderItem.player == player)
                orderItem.highlight.set(true)
            else
                orderItem.highlight.set(false)
        }
    }
}