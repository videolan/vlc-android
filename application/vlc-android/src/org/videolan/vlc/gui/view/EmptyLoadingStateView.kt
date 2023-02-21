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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.FragmentActivity
import androidx.transition.TransitionManager
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.askStoragePermission

class EmptyLoadingStateView : FrameLayout {

    private lateinit var emptyTextView: TextView
    private lateinit var permissionTextView: TextView
    private lateinit var loadingFlipper: ViewFlipper
    private lateinit var grantPermissionButton: Button
    private lateinit var pickFileButton: Button
    private lateinit var loadingTitle: TextView
    private lateinit var emptyImageView: ImageView
    private lateinit var permissionTitle: TextView
    private lateinit var noMediaButton: Button
    private val normalConstraintSet = ConstraintSet()
    private val compactConstraintSet = ConstraintSet()
    var filterQuery: String? = null

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
            emptyTextView.visibility = if (value in arrayOf(EmptyLoadingState.EMPTY, EmptyLoadingState.EMPTY_SEARCH, EmptyLoadingState.EMPTY_FAVORITES)) View.VISIBLE else View.GONE
            emptyImageView.visibility = if (value in arrayOf(EmptyLoadingState.EMPTY,EmptyLoadingState.MISSING_PERMISSION, EmptyLoadingState.EMPTY_SEARCH, EmptyLoadingState.EMPTY_FAVORITES)) View.VISIBLE else View.GONE
            emptyImageView.setImageBitmap(context.getBitmapFromDrawable(if (value == EmptyLoadingState.EMPTY_FAVORITES) R.drawable.ic_fav_empty else if (value in arrayOf(EmptyLoadingState.EMPTY, EmptyLoadingState.EMPTY_SEARCH, EmptyLoadingState.EMPTY_FAVORITES)) R.drawable.ic_empty else R.drawable.ic_empty_warning))
            permissionTitle.visibility = if (value == EmptyLoadingState.MISSING_PERMISSION) View.VISIBLE else View.GONE
            permissionTextView.visibility = if (value == EmptyLoadingState.MISSING_PERMISSION) View.VISIBLE else View.GONE
            grantPermissionButton.visibility = if (value == EmptyLoadingState.MISSING_PERMISSION) View.VISIBLE else View.GONE
            pickFileButton.visibility = if (value == EmptyLoadingState.MISSING_PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.VISIBLE else View.GONE
            noMediaButton.visibility = if (showNoMedia && value == EmptyLoadingState.EMPTY) View.VISIBLE else if (value == EmptyLoadingState.EMPTY_FAVORITES) View.INVISIBLE else  View.GONE
            field = value
        }

    var emptyText: String = context.getString(R.string.nomedia)
        set(value) {
            emptyTextView.text = value
            field = emptyText
        }

    var loadingText: String = context.getString(R.string.loading)
        set(value) {
            loadingTitle.text = value
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

    @SuppressLint("SetTextI18n")
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
        grantPermissionButton.setOnClickListener {
             (context as? FragmentActivity)?.askStoragePermission(false, null)
        }
        pickFileButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (context as BaseActivity).openFile(Uri.parse(""))
            }
        }

        permissionTextView.text = "${context.getString(R.string.permission_expanation_no_allow)}\n\n${context.getString(R.string.permission_expanation_allow)}"
    }

    private fun applyCompactMode() {
        if (!::container.isInitialized) return
        TransitionManager.beginDelayedTransition(container)
        if (compactMode) compactConstraintSet.applyTo(container) else normalConstraintSet.applyTo(container)
        emptyTextView.gravity = if (compactMode) Gravity.START else Gravity.CENTER
    }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_empty_loading, this, true)
        emptyTextView = findViewById(R.id.emptyTextView)
        permissionTextView = findViewById(R.id.permissionTextView)
        loadingFlipper = findViewById(R.id.loadingFlipper)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        pickFileButton = findViewById(R.id.pickFile)
        loadingTitle = findViewById(R.id.loadingTitle)
        emptyImageView = findViewById(R.id.emptyImageView)
        permissionTitle = findViewById(R.id.permissionTitle)
        noMediaButton = findViewById(R.id.noMediaButton)
    }
}

enum class EmptyLoadingState {
    LOADING, EMPTY, EMPTY_SEARCH, NONE, MISSING_PERMISSION, EMPTY_FAVORITES
}