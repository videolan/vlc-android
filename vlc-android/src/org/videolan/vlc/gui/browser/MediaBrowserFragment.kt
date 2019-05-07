/*
 * *************************************************************************
 *  MediaBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015-2016 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.view.SwipeRefreshLayout
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.SortableModel
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
abstract class MediaBrowserFragment<T : SortableModel> : Fragment(), ActionMode.Callback, Filterable {

    private lateinit var searchButtonView: View
    var swipeRefreshLayout: SwipeRefreshLayout? = null
    lateinit var mediaLibrary: Medialibrary
    var actionMode: ActionMode? = null
    var fabPlay: FloatingActionButton? = null
    open lateinit var viewModel: T
        protected set
    private var restart = false


    abstract fun getTitle(): String

    open val subTitle: String?
        get() = null

    val menu: Menu?
        get() {
            val activity = activity
            return if (activity !is AudioPlayerContainerActivity) null else activity.menu

        }

    protected open fun hasTabs(): Boolean {
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaLibrary = VLCApplication.mlInstance
        setHasOptionsMenu(!AndroidDevices.isAndroidTv)
        restart = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchButtonView = view.findViewById(R.id.searchButton)
        swipeRefreshLayout = view.findViewById(R.id.swipeLayout)
        swipeRefreshLayout?.setColorSchemeResources(R.color.orange700)
        if (hasFAB()) fabPlay = requireActivity().findViewById(R.id.fab)
    }

    protected open fun hasFAB(): Boolean {
        return swipeRefreshLayout != null
    }

    protected open fun setBreadcrumb() {
        val ariane = requireActivity().findViewById<RecyclerView>(R.id.ariane)
        if (ariane != null) ariane.visibility = View.GONE
    }

    private fun releaseBreadCrumb() {
        val ariane = requireActivity().findViewById<RecyclerView>(R.id.ariane)
        if (ariane != null) ariane.adapter = null
    }

    override fun onStart() {
        super.onStart()
        setBreadcrumb()
        updateActionBar()
        setFabPlayVisibility(true)
        fabPlay?.setOnClickListener { v -> onFabPlayClick(v) }
        if (restart) onRestart()
    }

    protected open fun onRestart() {}

    override fun onStop() {
        super.onStop()
        releaseBreadCrumb()
        setFabPlayVisibility(false)
        restart = true
    }

    private fun updateActionBar() {
        val activity = activity as AppCompatActivity? ?: return
        if (activity.supportActionBar != null) {
            activity.supportActionBar!!.title = getTitle()
            activity.supportActionBar!!.subtitle = subTitle
            activity.supportInvalidateOptionsMenu()
        }
        if (activity is ContentActivity) activity.setTabLayoutVisibility(hasTabs())
    }

    override fun onPause() {
        super.onPause()
        stopActionMode()
    }

    open fun setFabPlayVisibility(enable: Boolean) {
        if (fabPlay != null) {
            if (enable)
                fabPlay!!.show()
            else
                fabPlay!!.hide()
        }
    }

    open fun onFabPlayClick(view: View) {}
    abstract fun onRefresh()
    open fun clear() {}

    protected open fun removeItem(item: MediaLibraryItem): Boolean {
        view ?: return false
        when {
            item.itemType == MediaLibraryItem.TYPE_PLAYLIST -> UiTools.snackerConfirm(view!!, getString(R.string.confirm_delete_playlist, item.title), Runnable { MediaUtils.deletePlaylist(item as Playlist) })
            item.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                val deleteAction = Runnable { deleteMedia(item, false, null) }
                val resid = if ((item as MediaWrapper).type == MediaWrapper.TYPE_DIR) R.string.confirm_delete_folder else R.string.confirm_delete
                UiTools.snackerConfirm(view!!, getString(resid, item.getTitle()), Runnable { if (Util.checkWritePermission(requireActivity(), item, deleteAction)) deleteAction.run() })
            }
            else -> return false
        }
        return true
    }

    protected fun deleteMedia(mw: MediaLibraryItem, refresh: Boolean, failCB: Runnable?) {
        runIO(Runnable {
            val foldersToReload = LinkedList<String>()
            val mediaPaths = LinkedList<String>()
            for (media in mw.tracks) {
                val path = media.uri.path
                val parentPath = FileUtils.getParent(path)
                if (FileUtils.deleteFile(media.uri)) {
                    if (media.id > 0L && !foldersToReload.contains(parentPath)) {
                        if (parentPath != null) {
                            foldersToReload.add(parentPath)
                        }
                    }
                    mediaPaths.add(media.location)
                } else
                    onDeleteFailed(media)
            }
            for (folder in foldersToReload) mediaLibrary.reload(folder)
            if (activity != null) {
                runOnMainThread(Runnable {
                    if (mediaPaths.isEmpty()) {
                        failCB?.run()
                        return@Runnable
                    }
                    if (refresh) onRefresh()
                })
            }
        })
    }

    private fun onDeleteFailed(media: MediaWrapper) {
        val v = view
        if (v != null && isAdded) UiTools.snacker(v, getString(R.string.msg_delete_failed, media.title))
    }

    protected fun showInfoDialog(item: MediaLibraryItem) {
        val i = Intent(activity, InfoActivity::class.java)
        i.putExtra(TAG_ITEM, item)
        startActivity(i)
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        super.onPrepareOptionsMenu(menu)
        menu!!.findItem(R.id.ml_menu_sortby).isVisible = viewModel.canSortByName()
        menu.findItem(R.id.ml_menu_sortby_filename).isVisible = viewModel.canSortByFileNameName()
        menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = viewModel.canSortByArtist()
        menu.findItem(R.id.ml_menu_sortby_album_name).isVisible = viewModel.canSortByAlbum()
        menu.findItem(R.id.ml_menu_sortby_length).isVisible = viewModel.canSortByDuration()
        menu.findItem(R.id.ml_menu_sortby_date).isVisible = viewModel.canSortByReleaseDate()
        menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = viewModel.canSortByLastModified()
        menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
        UiTools.updateSortTitles(this)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.ml_menu_sortby_name -> {
                sortBy(Medialibrary.SORT_ALPHA)
                return true
            }
            R.id.ml_menu_sortby_filename -> {
                sortBy(Medialibrary.SORT_FILENAME)
                return true
            }
            R.id.ml_menu_sortby_length -> {
                sortBy(Medialibrary.SORT_DURATION)
                return true
            }
            R.id.ml_menu_sortby_date -> {
                sortBy(Medialibrary.SORT_RELEASEDATE)
                return true
            }
            R.id.ml_menu_sortby_last_modified -> {
                sortBy(Medialibrary.SORT_LASTMODIFICATIONDATE)
                return true
            }
            R.id.ml_menu_sortby_artist_name -> {
                sortBy(Medialibrary.SORT_ARTIST)
                return true
            }
            R.id.ml_menu_sortby_album_name -> {
                sortBy(Medialibrary.SORT_ALBUM)
                return true
            }
            R.id.ml_menu_sortby_number -> {
                sortBy(Medialibrary.SORT_FILESIZE) //TODO
                return super.onOptionsItemSelected(item)
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    protected open fun sortBy(sort: Int) {
        viewModel.sort(sort)
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    fun startActionMode() {
        val activity = activity as AppCompatActivity? ?: return
        actionMode = activity.startSupportActionMode(this)
        setFabPlayVisibility(false)
    }

    protected fun stopActionMode() {
        actionMode?.let {
            it.finish()
            setFabPlayVisibility(true)
        }
    }

    fun invalidateActionMode() {
        if (actionMode != null) actionMode!!.invalidate()
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun filter(query: String) {
        viewModel.filter(query)
    }

    override fun restoreList() {
        viewModel.restore()
    }

    override fun enableSearchOption(): Boolean {
        return true
    }

    override fun getFilterQuery(): String? {
        return viewModel.filterQuery
    }

    override fun setSearchVisibility(visible: Boolean) {
        if (searchButtonView.visibility == View.VISIBLE == visible) return
        if (searchButtonView.parent is ConstraintLayout) {
            val cl = searchButtonView.parent as ConstraintLayout
            val cs = ConstraintSet()
            cs.clone(cl)
            cs.setVisibility(R.id.searchButton, if (visible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            TransitionManager.beginDelayedTransition(cl)
            cs.applyTo(cl)
        } else
            UiTools.setViewVisibility(searchButtonView, if (visible) View.VISIBLE else View.GONE)
    }

    override fun allowedToExpand(): Boolean {
        return true
    }

    companion object {

        val TAG = "VLC/MediaBrowserFragment"
    }
}
