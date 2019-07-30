package com.pierbezuhoff.clonium.ui.newgame

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.clonium.R
import com.pierbezuhoff.clonium.domain.*
import com.pierbezuhoff.clonium.models.GameBitmapLoader
import kotlinx.android.synthetic.main.player_item.view.*
import kotlinx.android.synthetic.main.player_tactic_item.view.*
import org.jetbrains.anko.layoutInflater

data class PlayerItem(
    val playerId: PlayerId,
    var tactics: PlayerTactics,
    var participate: Boolean
) {
    fun toPlayer(): Player =
        when (tactics) {
            PlayerTactics.HUMAN -> HumanPlayer(playerId)
            PlayerTactics.RANDOM_BOT -> RandomPickerBot(playerId)
        }
}

class PlayerAdapter(
    private val playerItems: MutableList<PlayerItem>,
    private val bitmapLoader: GameBitmapLoader
) : RecyclerView.Adapter<PlayerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        lateinit var playerItem: PlayerItem
    }

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
            itemView.player_layout.setOnClickListener {
//                TODO("choose it")
            }
            itemView.use_player.isChecked = playerItem.participate
            itemView.use_player.setOnCheckedChangeListener { _, checked ->
                playerItem.participate = checked
            }
            itemView.colored_chip.setImageBitmap(bitmapLoader.loadChip(Chip(playerItem.playerId, Level1)))
//            itemView.player_tactics.setSelection()
            itemView.player_tactics.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) { }
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    TODO("not implemented")
                }
            }
        }
    }

    override fun getItemCount(): Int =
        playerItems.size
}

class PlayerTacticsAdapter(private val context: Context) : BaseAdapter() {
    private class ViewHolder(val view: View)
    private val tactics = PlayerTactics.values()

    override fun getItem(position: Int): PlayerTactics =
        tactics[position]

    override fun getItemId(position: Int): Long =
        position.toLong()

    override fun getCount(): Int =
        tactics.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
        convertView ?: context.layoutInflater
            .inflate(R.layout.player_tactic_item, parent, false).apply {
                val tactic = getItem(position)
                tactic_description.text = context.getString(tactic.descriptionId)
            }
}

enum class PlayerTactics {
    HUMAN, RANDOM_BOT;
    
    @get:StringRes val descriptionId: Int get() =
        when (this) {
            HUMAN -> R.string.human
            RANDOM_BOT -> R.string.random_picker
        }
}