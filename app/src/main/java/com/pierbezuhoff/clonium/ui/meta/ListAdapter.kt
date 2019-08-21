package com.pierbezuhoff.clonium.ui.meta

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.annotation.LayoutRes
import org.jetbrains.anko.layoutInflater

class ListAdapter<E>(
    private val context: Context,
    @LayoutRes private val itemLayoutId: Int,
    private val elements: List<E>,
    private val onBind: (view: View, element: E) -> Unit = {_,_->}
) : BaseAdapter() {

    override fun getItem(position: Int): E =
        elements[position]

    override fun getItemId(position: Int): Long =
        position.toLong()

    override fun getCount(): Int =
        elements.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
        convertView ?: context.layoutInflater
            .inflate(itemLayoutId, parent, false).also { view ->
                onBind(view, getItem(position))
            }
}
