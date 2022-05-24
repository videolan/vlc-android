/*
 * ************************************************************************
 *  LanguageSelector.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.view

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.videolan.vlc.R

class LanguageSelector: ConstraintLayout, DialogInterface.OnDismissListener, DialogInterface.OnMultiChoiceClickListener {
    private lateinit var allEntriesOfLanguages: Array<String>
    lateinit var allValuesOfLanguages: Array<String>
    private var selection = mutableListOf<Boolean>()
    private var listener: OnItemSelectListener? = null
    private val selectedIndices: List<Int>
        get() {
            return selection.mapIndexed { index, b -> Pair(index, b) }.filter { it.second }.map { it.first }
        }

    val badge: TextView by lazy {
        findViewById<TextView>(R.id.badge)
    }

    constructor(context: Context) : super(context) {
        initViews()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initViews()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initViews()
    }

    private fun initViews() {
        allValuesOfLanguages = resources.getStringArray(R.array.language_values)
        allEntriesOfLanguages = resources.getStringArray(R.array.language_entries)
        LayoutInflater.from(context).inflate(R.layout.language_spinner, this, true)
        selection.addAll(allEntriesOfLanguages.map { false })
        setOnClickListener {
            val builder = AlertDialog.Builder(context)
            builder.setOnDismissListener(this)
            builder.setMultiChoiceItems(allEntriesOfLanguages, selection.toBooleanArray(), this)
                    .setPositiveButton(R.string.done) { dialogInterface: DialogInterface, _: Int ->
                        dialogInterface.dismiss()
                    }
                    .show()
        }
    }

    fun setSelection(selectedIndices: List<Int>) {
        selection = selection.map { false }.toMutableList()
        selectedIndices.forEach {
            if (it >= 0 && it < selection.size) selection[it] = true
        }
        contentDescription = context.getString(R.string.talkback_language_selection, selection.filter { it }.size.toString())
        updateBadge()
        listener?.onItemSelect(selectedIndices)
    }


    override fun onDismiss(dialog: DialogInterface?) {
        listener?.onItemSelect(selectedIndices)
    }

    override fun onClick(dialog: DialogInterface?, index: Int, isChecked: Boolean) {
        if (index < selection.size) {
            selection[index] = isChecked
        } else  throw IllegalArgumentException("Argument 'index' is out of bounds.")
        updateBadge()
    }

    private fun updateBadge() {
        badge.text = if (selectedIndices.isNotEmpty()) selectedIndices.size.toString() else "+"
    }

    fun setOnItemsSelectListener(onItemSelectListener: OnItemSelectListener) {
        listener = onItemSelectListener
    }
}

interface OnItemSelectListener {
    fun onItemSelect(selectedItems: List<Int>)
}