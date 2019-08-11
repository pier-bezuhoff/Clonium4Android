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
import kotlinx.coroutines.*
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
    private var orderItems: List<OrderItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>()
    , GameModel.StatHolder
    , GameModel.CurrentPlayerHolder
    , Logger by AndroidLoggerOf<OrderAdapter>()
{
    class ViewHolder(val binding: OrderItemBinding) : RecyclerView.ViewHolder(binding.root)

    private var nOfAlivePlayers = orderItems.size

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
        val dying = mutableSetOf<Int>()
        for ((ix, orderItem) in orderItems.withIndex()) {
            val stat = gameStat.getValue(orderItem.player)
            orderItem.stat.set(stat)
            val alive = stat.chipCount > 0
            orderItem.alive.set(alive)
            if (ix < nOfAlivePlayers && !alive) {
                dying += ix
            }
        }
        for (ix in dying) {
            nOfAlivePlayers --
            if (ix != nOfAlivePlayers) // may be already at the right place
                moveOrderItem(ix, nOfAlivePlayers)
        }
    }

    override fun updateCurrentPlayer(player: Player) {
        if (orderItems.first().player != player) {
            moveOrderItem(0, nOfAlivePlayers - 1)
        }
    }

    fun setOrderItems(newOrderItems: List<OrderItem>) {
        orderItems = newOrderItems
        nOfAlivePlayers = newOrderItems.size
        notifyDataSetChanged()
    }

    private fun moveOrderItem(from: Int, to: Int) {
        val size = orderItems.size
        require(from in 0 until size)
        require(to in 0 until size)
        require(from != to)
        orderItems = when {
            to == size -> {
                val before = orderItems.subList(0, from)
                val between = orderItems.subList(from + 1, size)
                before + between + orderItems[from]
            }
            // pushing item at `to` backward
            from < to -> {
                val before = orderItems.subList(0, from)
                val between = orderItems.subList(from + 1, to + 1)
                val after = orderItems.subList(to + 1, size)
                before + between + orderItems[from] + after
            }
            // pushing item at `to` forward
            else -> {
                val before = orderItems.subList(0, to)
                val between = orderItems.subList(to, from)
                val after = orderItems.subList(from + 1, size)
                before + orderItems[from] + between + after
            }
        }
        runBlocking(Dispatchers.Main) {
            notifyItemMoved(from, minOf(size - 1, to))
        }
    }
}