package com.pierbezuhoff.clonium.ui.newgame

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.utils.Connection
import kotlinx.android.synthetic.main.player_item.view.*
import kotlinx.android.synthetic.main.player_tactic_item.view.*
import org.jetbrains.anko.layoutInflater

data class PlayerItem(
    val playerId: PlayerId,
    var tactic: PlayerTactic,
    var participate: Boolean
) {
    fun toPlayer(): Player =
        tactic.toPlayer(playerId)
}

class ItemMoveCallback(private val rowManager: RowManager) : ItemTouchHelper.Callback() {
    override fun isLongPressDragEnabled(): Boolean =
        true
    override fun isItemViewSwipeEnabled(): Boolean =
        false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
        makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        rowManager.moveRow(
            fromPosition = viewHolder.adapterPosition,
            toPosition = target.adapterPosition
        )
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder is PlayerAdapter.ViewHolder) {
            rowManager.selectRow(viewHolder)
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is PlayerAdapter.ViewHolder) {
            rowManager.unselectRow(viewHolder)
        }
    }
}

interface RowManager {
    fun moveRow(fromPosition: Int, toPosition: Int)
    fun selectRow(viewHolder: PlayerAdapter.ViewHolder)
    fun unselectRow(viewHolder: PlayerAdapter.ViewHolder)
}

class PlayerAdapter(
    private val playerItems: MutableList<PlayerItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<PlayerAdapter.ViewHolder>()
    , RowManager
{
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        lateinit var playerItem: PlayerItem
    }

    interface BoardPlayerHider { fun hidePlayer(playerId: PlayerId); fun showPlayer(playerId: PlayerId) }
    private val boardPlayerVisibility = Connection<BoardPlayerHider>()
    val boardPlayerVisibilitySubscription = boardPlayerVisibility.subscription

    interface BoardPlayerHighlighter { fun highlighPlayer(playerId: PlayerId); fun unhighlight() }
    private val boardPlayerHighlighting = Connection<BoardPlayerHighlighter>()
    val boardPlayerHighlightingSubscription = boardPlayerHighlighting.subscription

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.player_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playerItem = playerItems[position]
        holder.playerItem = playerItem
        with(holder) {
            itemView.use_player.isChecked = playerItem.participate
            itemView.use_player.setOnCheckedChangeListener { _, checked ->
                playerItem.participate = checked
                val playerId = playerItem.playerId
                boardPlayerVisibility.send {
                    if (checked)
                        showPlayer(playerId)
                    else
                        hidePlayer(playerId)
                }
            }
            val chipBitmap = bitmapLoader.loadChip(Chip(playerItem.playerId, Level1))
            itemView.colored_chip.setImageBitmap(chipBitmap)
            // BUG: does not set random picker
            itemView.player_tactics.setSelection(PLAYER_TACTICS.indexOf(playerItem.tactic))
            itemView.player_tactics.adapter = PlayerTacticsAdapter(itemView.context)
            itemView.player_tactics.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) { }
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val tactic = itemView.player_tactics.getItemAtPosition(position) as PlayerTactic
                    playerItem.tactic = tactic
                }
            }
        }
    }

    override fun getItemCount(): Int =
        playerItems.size

    override fun moveRow(fromPosition: Int, toPosition: Int) {
        val removed = playerItems.removeAt(fromPosition)
        if (toPosition < itemCount) {
            val targetPosition = if (toPosition >= fromPosition) (toPosition + 1) else toPosition
            playerItems.add(targetPosition, removed)
        } else {
            playerItems.add(removed)
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun selectRow(viewHolder: ViewHolder) {
        // TODO: highlight row, for example: viewHolder.itemView.setBackgroundColor(Color.YELLOW)
        val playerId = viewHolder.playerItem.playerId
        boardPlayerHighlighting.send {
            highlighPlayer(playerId)
        }
    }

    override fun unselectRow(viewHolder: ViewHolder) {
        boardPlayerHighlighting.send {
            unhighlight()
        }
    }
}

class PlayerTacticsAdapter(private val context: Context) : BaseAdapter() {
    private val tactics = PLAYER_TACTICS

    override fun getItem(position: Int): PlayerTactic =
        tactics[position]

    override fun getItemId(position: Int): Long =
        position.toLong()

    override fun getCount(): Int =
        tactics.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
        convertView ?: context.layoutInflater
            .inflate(R.layout.player_tactic_item, parent, false).apply {
                val tactic = getItem(position)
                tactic_description.text = context.getString(tacticDescriptionId(tactic))
            }
}

fun tacticDescriptionId(tactic: PlayerTactic): Int =
    when (tactic) {
        PlayerTactic.Human -> R.string.human
        PlayerTactic.Bot.RandomPicker -> R.string.random_picker
        else -> throw NotImplementedError("todo")
    }
