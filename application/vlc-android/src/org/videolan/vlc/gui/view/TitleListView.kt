/*
 * ************************************************************************
 *  TitleListView.kt
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

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.vlc.R

class TitleListView : ConstraintLayout {

    private var actionClickListener: ((View) -> Unit)? = null
    var displayInCards: Boolean = true
        set(value) {
            val changed = field != value
            field = value
            if (changed) manageDisplay()
        }
    private val titleView: TextView by lazy {
        findViewById(R.id.title)
    }

    val list: RecyclerView by lazy {
        findViewById(R.id.list)
    }

    val loading: EmptyLoadingStateView by lazy {
        findViewById(R.id.loading)
    }

    val actionButton: ImageButton by lazy {
        findViewById(R.id.action_button)
    }

    private val titleContent: View by lazy {
        findViewById(R.id.title_content)
    }

    fun setOnActionClickListener(listener: (View) -> Unit) {
        this.actionClickListener = listener
        titleContent.isEnabled = actionClickListener != null
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
        initAttributes(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
        initAttributes(attrs, defStyle)
    }

    private fun initAttributes(attrs: AttributeSet, defStyle: Int) {
        attrs.let {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.TitleListView, 0, defStyle)
            try {
                val titleString = a.getString(R.styleable.TitleListView_title)
                titleView.text = titleString
                titleView.contentDescription = context.getString(R.string.talkback_list_section, titleString)
                actionButton.contentDescription = context.getString(R.string.talkback_enter_screen, titleString)
                if (!a.getBoolean(R.styleable.TitleListView_show_button, false)) actionButton.setGone()
                actionButton.setOnClickListener {
                    actionClickListener?.let { listener -> listener(actionButton) }
                }
                titleContent.setOnClickListener {
                    actionClickListener?.let { listener -> listener(actionButton) }
                }
                titleContent.isEnabled = actionClickListener != null
                list.isNestedScrollingEnabled = false
            } catch (e: Exception) {
                Log.w("", e.message, e)
            } finally {
                a.recycle()
            }
        }

        manageDisplay()
    }

    private fun manageDisplay() {
        if (list.itemDecorationCount > 0) {
            list.removeItemDecorationAt(0)
        }
        if (displayInCards) {
            list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            list.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.left = resources.getDimension(R.dimen.kl_half).toInt()
                    outRect.right = resources.getDimension(R.dimen.kl_half).toInt()
                }
            })

            list.setPadding(12.dp,0,12.dp,0)
        } else {
            list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            list.setPadding(0,0,0,0)
        }
        list.adapter?.let {
            list.adapter = it
        }
    }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.title_list_view, this, true)
    }
}
