/*
 * ************************************************************************
 *  EmptyLoadingStateView.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.TransitionManager
import kotlinx.android.synthetic.main.view_empty_loading.view.*
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity

class EmptyLoadingStateView : FrameLayout {

    private val normalConstraintSet = ConstraintSet()
    private val compactConstraintSet = ConstraintSet()

    lateinit var container: ConstraintLayout
    var showNoMedia: Boolean = true
    private var compactMode: Boolean = false
        set(value) {
            field = value
            applyCompactMode()
        }
    var state = EmptyLoadingState.LOADING
        set(value) {
            loadingFlipper.visibility = if (value == EmptyLoadingState.LOADING) View.VISIBLE else View.GONE
            loadingTitle.visibility = if (value == EmptyLoadingState.LOADING) View.VISIBLE else View.GONE
            emptyTextView.visibility = if (value == EmptyLoadingState.EMPTY) View.VISIBLE else View.GONE
            emptyImageView.visibility = if (value == EmptyLoadingState.EMPTY) View.VISIBLE else View.GONE
            noMediaButton.visibility = if (showNoMedia && value == EmptyLoadingState.EMPTY) View.VISIBLE else View.GONE

            field = value
        }

    @StringRes
    var emptyText: Int = R.string.nomedia
        set(value) {
            emptyTextView.text = context.getString(value)
            field = emptyText
        }

    @StringRes
    var loadingText: Int = R.string.loading
        set(value) {
            loadingTitle.text = context.getString(value)
            field = emptyText
        }

    private var noMediaClickListener: (() -> Unit)? = null

    fun setOnNoMediaClickListener(l: () -> Unit) {
        noMediaClickListener = l
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
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.EmptyLoadingStateView, 0, defStyle)
            try {
                emptyTextView.text = a.getString(R.styleable.EmptyLoadingStateView_empty_text)
                showNoMedia = a.getBoolean(R.styleable.EmptyLoadingStateView_show_no_media, true)
                compactMode = a.getBoolean(R.styleable.EmptyLoadingStateView_compact_mode, true)
            } catch (e: Exception) {
                Log.w("", e.message, e)
            } finally {
                a.recycle()
            }

        }

        state = EmptyLoadingState.LOADING

        noMediaButton.setOnClickListener {
            val intent = Intent(context.applicationContext, SecondaryActivity::class.java)
            intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
            (context as Activity).startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES)
            noMediaClickListener?.invoke()
        }
        container = findViewById(R.id.container)
        normalConstraintSet.clone(container)
        compactConstraintSet.clone(context, R.layout.view_empty_loading_compact)
        if (compactMode) {
            applyCompactMode()
        }
    }

    private fun applyCompactMode() {
        if (!::container.isInitialized) return
        TransitionManager.beginDelayedTransition(container)
        if (compactMode) compactConstraintSet.applyTo(container) else normalConstraintSet.applyTo(container)
        emptyTextView.gravity = if (compactMode) Gravity.START else Gravity.CENTER
    }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_empty_loading, this, true)
    }
}

enum class EmptyLoadingState {
    LOADING, EMPTY, NONE
}