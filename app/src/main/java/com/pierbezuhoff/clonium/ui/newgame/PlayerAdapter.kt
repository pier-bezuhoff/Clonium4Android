package com.pierbezuhoff.clonium.ui.newgame

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.domain.Level1
import com.pierbezuhoff.clonium.domain.PLAYER_TACTICS
import com.pierbezuhoff.clonium.domain.PlayerId
import com.pierbezuhoff.clonium.domain.PlayerTactic
import com.pierbezuhoff.clonium.models.ChipSet
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import com.pierbezuhoff.clonium.models.MutableMapColorPrism
import com.pierbezuhoff.clonium.models.PlayerItem
import com.pierbezuhoff.clonium.ui.meta.ListAdapter
import com.pierbezuhoff.clonium.utils.*
import kotlinx.android.synthetic.main.colored_chip_item.view.*
import kotlinx.android.synthetic.main.player_item.view.*
import kotlinx.android.synthetic.main.player_tactic_item.view.*

class ItemMoveCallback(private val rowManager: RowManager) : ItemTouchHelper.Callback() {
    override fun isLongPressDragEnabled() =
        false

    override fun isItemViewSwipeEnabled() =
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

// TODO: select allied for ally
class PlayerAdapter(
    private var playerItems: MutableList<PlayerItem>,
    private val bitmapLoader: GameBitmapLoader,
    private val chipSet: ChipSet,
    private val colorPrism: MutableMapColorPrism
) : RecyclerView.Adapter<PlayerAdapter.ViewHolder>()
    , RowManager
    , Logger by AndroidLoggerOf<PlayerAdapter>()
{
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        lateinit var playerItem: PlayerItem
    }

    interface BoardPlayerHider { fun hidePlayer(playerId: PlayerId); fun showPlayer(playerId: PlayerId) }
    private val boardPlayerVisibility = Connection<BoardPlayerHider>()
    val boardPlayerVisibilitySubscription = boardPlayerVisibility.subscription

    interface BoardPlayerHighlighter { fun highlightPlayer(playerId: PlayerId); fun unhighlight() }
    private val boardPlayerHighlighting = Connection<BoardPlayerHighlighter>()
    val boardPlayerHighlightingSubscription = boardPlayerHighlighting.subscription

    private val boardViewInvalidating = Connection<BoardViewInvalidator>()
    val boardViewInvalidatingSubscription = boardViewInvalidating.subscription

    private val itemTouchConnection = Connection<ItemTouchHelper>()
    val itemTouchSubscription = itemTouchConnection.subscription

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.player_item, parent, false)
        return ViewHolder(view)
    }

    fun setPlayerItems(newPlayerItems: MutableList<PlayerItem>) {
        playerItems = newPlayerItems
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playerItem = playerItems[position]
        holder.playerItem = playerItem
        setupUsePlayerCheckBox(holder, playerItem)
        setupColoredChipsSpinner(holder, playerItem)
        setupPlayerTacticsSpinner(holder, playerItem)
        setupDragHandle(holder)
    }

    private fun setupUsePlayerCheckBox(holder: ViewHolder, playerItem: PlayerItem) {
        val usePlayerCheckBox = holder.itemView.use_player
        usePlayerCheckBox.isChecked = playerItem.participate
        usePlayerCheckBox.setOnCheckedChangeListener { _, checked ->
            playerItem.participate = checked
            val playerId = playerItem.playerId
            boardPlayerVisibility.send {
                if (checked)
                    showPlayer(playerId)
                else
                    hidePlayer(playerId)
            }
        }
    }

    private fun setupColoredChipsSpinner(holder: ViewHolder, playerItem: PlayerItem) {
        val coloredChipsSpinner = holder.itemView.colored_chips
        coloredChipsSpinner.adapter = ListAdapter(
            holder.itemView.context, R.layout.colored_chip_item, chipSet.getColorRange().toList()
        ) { view, colorId ->
            val chipBitmap = bitmapLoader.loadRawChip(chipSet, colorId, Level1)
            val targetSize = holder.itemView.context.resources.getDimensionPixelSize(R.dimen.colored_chip_size)
            view.colored_chip.setImageBitmap(smoothBitmap(chipBitmap, targetSize))
        }
        val initialColorId = colorPrism.player2color(playerItem.playerId) ?: playerItem.playerId.id
        coloredChipsSpinner.setSelection(initialColorId, false)
        coloredChipsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val colorId = coloredChipsSpinner.getItemAtPosition(position) as Int
                val previousColorId = colorPrism.player2color(playerItem.playerId)!!
                if (previousColorId != colorId) {
                    val previousColorIdOwners =
                        playerItems.withIndex()
                            .filter { (_, playerItem) ->
                                colorPrism.player2color(playerItem.playerId) == colorId
                            }
                    if (previousColorIdOwners.isNotEmpty()) {
                        for ((i, previousOwner) in previousColorIdOwners) {
                            colorPrism[previousOwner.playerId] = previousColorId
                            notifyItemChanged(i)
                        }
                    }
                    colorPrism[playerItem.playerId] = colorId
                    // TODO: notify presenter that chipCache should be invalidated
                    boardViewInvalidating.send {
                        invalidateBoardView()
                    }
                }
            }
        }
    }

    private fun setupPlayerTacticsSpinner(holder: ViewHolder, playerItem: PlayerItem) {
        val tacticsSpinner = holder.itemView.player_tactics
        tacticsSpinner.adapter = ListAdapter(
            holder.itemView.context, R.layout.player_tactic_item, PLAYER_TACTICS
        ) { view, tactic ->
            bindTactic(view.tactic_description, tactic)
        }
        tacticsSpinner.setSelection(PLAYER_TACTICS.indexOf(playerItem.tactic), false)
        tacticsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val tactic = tacticsSpinner.getItemAtPosition(position) as PlayerTactic
                if (tactic is PlayerTactic.Bot.AlliedLevelBalancer)
                    Unit // show dialog
                playerItem.tactic = tactic
            }
        }
    }

    private fun setupDragHandle(holder: ViewHolder) {
        val dragHandle = holder.itemView.drag_handle
        dragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN)
                itemTouchConnection.send {
                    startDrag(holder)
                }
            return@setOnTouchListener false
        }
    }

    override fun getItemCount(): Int =
        playerItems.size

    override fun moveRow(fromPosition: Int, toPosition: Int) {
        val removed = playerItems.removeAt(fromPosition)
        if (toPosition < itemCount) {
            playerItems.add(toPosition, removed)
        } else {
            playerItems.add(removed)
        }
        notifyItemMoved(fromPosition, toPosition)

    }

    override fun selectRow(viewHolder: ViewHolder) {
        // MAYBE: highlight row, for example: viewHolder.itemView.setBackgroundColor(Color.YELLOW)
        val playerId = viewHolder.playerItem.playerId
        boardPlayerHighlighting.send {
            highlightPlayer(playerId)
        }
    }

    override fun unselectRow(viewHolder: ViewHolder) {
        boardPlayerHighlighting.send {
            unhighlight()
        }
    }
}
