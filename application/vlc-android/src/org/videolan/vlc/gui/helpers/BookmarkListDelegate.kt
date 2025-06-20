/*
 * ************************************************************************
 *  BookmarkListDelegate.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers

import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.tools.Settings
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.util.LocaleUtil
import org.videolan.vlc.viewmodels.BookmarkModel

class BookmarkListDelegate(val activity: FragmentActivity, val service: PlaybackService, private val bookmarkModel: BookmarkModel, val forVideo:Boolean) :
        LifecycleObserver, BookmarkAdapter.IBookmarkManager {

    private lateinit var nextBookmarkButton: ImageView
    private lateinit var previousBookmarkButton: ImageView
    private lateinit var bookmarkRewind10: ImageView
    private lateinit var bookmarkForward10: ImageView
    private lateinit var bookmarkRewindText: TextView
    private lateinit var bookmarkForwardText: TextView
    lateinit var addBookmarkButton: ImageView
    lateinit var markerContainer: ConstraintLayout
    private lateinit var adapter: BookmarkAdapter
    lateinit var bookmarkList: RecyclerView
    lateinit var rootView: ConstraintLayout
    private lateinit var emptyView: View
    lateinit var visibilityListener: () -> Unit
    lateinit var seekListener: (Boolean, Boolean) -> Unit
    val visible: Boolean
        get() = rootView.visibility != View.GONE

    fun show() {
        activity.findViewById<ViewStubCompat>(R.id.bookmarks_stub)?.let {
            rootView = it.inflate() as ConstraintLayout
            bookmarkList = rootView.findViewById(R.id.bookmark_list)
            rootView.findViewById<ImageView>(R.id.close).setOnClickListener { hide() }
            addBookmarkButton = rootView.findViewById(R.id.add_bookmark)
            nextBookmarkButton = rootView.findViewById(R.id.next_bookmark)
            previousBookmarkButton = rootView.findViewById(R.id.previous_bookmark)
            bookmarkRewind10 = rootView.findViewById(R.id.bookmark_rewind_10)
            bookmarkForward10 = rootView.findViewById(R.id.bookmark_forward_10)
            previousBookmarkButton = rootView.findViewById(R.id.previous_bookmark)
            bookmarkRewindText = rootView.findViewById(R.id.bookmark_rewind_text)
            bookmarkForwardText = rootView.findViewById(R.id.bookmark_forward_text)
            addBookmarkButton.setOnClickListener {
                bookmarkModel.addBookmark(activity)
                addBookmarkButton.announceForAccessibility(activity.getString(R.string.bookmark_added))
            }
            rootView.findViewById<View>(R.id.top_bar).setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                true
            }
            emptyView = rootView.findViewById(R.id.empty_view)
            service.lifecycle.addObserver(this)
            activity.lifecycle.addObserver(this)
            if (bookmarkList.layoutManager == null) bookmarkList.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            adapter = BookmarkAdapter(this)
            bookmarkList.adapter = adapter
            bookmarkList.itemAnimator = null
            previousBookmarkButton.setOnClickListener {
                val bookmark = if (LocaleUtil.isRtl())bookmarkModel.findNext() else bookmarkModel.findPrevious()
                bookmark?.let {
                    service.setTime(it.time)
                }
            }
            nextBookmarkButton.setOnClickListener {
                val bookmark = if (LocaleUtil.isRtl())bookmarkModel.findPrevious() else bookmarkModel.findNext()
                bookmark?.let {
                    service.setTime(it.time)
                }
            }

            bookmarkModel.dataset.observe(activity) { bookmarkList ->
                adapter.update(bookmarkList)
                showBookmarks(markerContainer, service, activity, bookmarkList)



                if (bookmarkList.isNotEmpty()) emptyView.setGone() else emptyView.setVisible()
            }
            bookmarkModel.refresh()
        }
        bookmarkModel.refresh()
        rootView.setVisible()
        markerContainer.setVisible()
        visibilityListener.invoke()
        bookmarkRewind10.contentDescription = activity.getString(R.string.talkback_action_rewind, Settings.audioJumpDelay.toString())
        bookmarkForward10.contentDescription = activity.getString(R.string.talkback_action_forward, Settings.audioJumpDelay.toString())
        val jumpDelay = if (forVideo) Settings.videoJumpDelay else Settings.audioJumpDelay
        bookmarkRewindText.text = "$jumpDelay"
        bookmarkForwardText.text = "$jumpDelay"

        bookmarkRewind10.setOnClickListener {
            seekListener.invoke(false, false)
        }
        bookmarkForward10.setOnClickListener {
            seekListener.invoke(true, false)
        }

        bookmarkRewind10.setOnLongClickListener {
            seekListener.invoke(false, true)
            true
        }
        bookmarkForward10.setOnLongClickListener {
            seekListener.invoke(true, true)
            true
        }
    }

    fun hide() {
        rootView.setGone()
        markerContainer.setGone()
        visibilityListener.invoke()
    }

    override fun onPopupMenu(view: View, position: Int, bookmark: Bookmark?) {
        if (bookmark == null) return
        val menu = PopupMenu(view.context, view)
        menu.inflate(R.menu.bookmark_options)
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bookmark_rename -> {
                    val dialog = RenameDialog.newInstance(bookmark)
                    dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                    true
                }
                R.id.bookmark_delete -> {
                    bookmarkModel.delete(bookmark)
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    override fun onBookmarkClick(position: Int, bookmark: Bookmark) {
        service.setTime(bookmark.time)
    }

    fun setProgressHeight(y: Float) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootView)
        constraintSet.setGuidelineBegin(R.id.progressbar_guideline, y.toInt())
        constraintSet.applyTo(rootView)
    }

    fun renameBookmark(media:Bookmark, name:String) {
        activity.lifecycleScope.launch {
            val bookmarks = bookmarkModel.rename(media, name)
            adapter.update(bookmarks)
            bookmarkModel.refresh()
        }
    }
    companion object {
        fun showBookmarks(markerContainer:ConstraintLayout, service: PlaybackService, activity: FragmentActivity, bookmarkList: List<Bookmark>) {
            markerContainer.removeAllViews()

            //show bookmark markers
            service.currentMediaWrapper?.length?.let { mediaLength ->
                if (mediaLength < 1) return@let
                val constraintSet = ConstraintSet()
                constraintSet.clone(markerContainer)
                bookmarkList.forEach { bookmark ->
                    val imageView = ImageView(activity)
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    imageView.id = View.generateViewId()

                    val guidelineId = View.generateViewId()
                    constraintSet.create(guidelineId, ConstraintSet.VERTICAL_GUIDELINE)
                    constraintSet.setGuidelinePercent(guidelineId, bookmark.time.toFloat() / mediaLength.toFloat())
                    constraintSet.connect(imageView.id, ConstraintSet.START, guidelineId, ConstraintSet.START, 0)
                    constraintSet.connect(imageView.id, ConstraintSet.END, guidelineId, ConstraintSet.END, 0)
                    constraintSet.constrainWidth(imageView.id, ConstraintSet.WRAP_CONTENT)
                    constraintSet.constrainHeight(imageView.id, ConstraintSet.WRAP_CONTENT)
                    constraintSet.connect(imageView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    constraintSet.connect(imageView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    imageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_bookmark_marker))
                    markerContainer.addView(imageView)
                }
                constraintSet.applyTo(markerContainer)
            }
        }
    }
}