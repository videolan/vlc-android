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
import android.util.SparseBooleanArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.helpers.SparseBooleanArrayParcelable
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.snackerConfirm
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.gui.view.SwipeRefreshLayout
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.MedialibraryViewModel
import org.videolan.vlc.viewmodels.SortableModel
import java.lang.Runnable
import java.util.*

private const val TAG = "VLC/MediaBrowserFragment"
private const val KEY_SELECTION = "key_selection"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
abstract class MediaBrowserFragment<T : SortableModel> : Fragment(), ActionMode.Callback, Filterable, CoroutineScope by MainScope() {

    private lateinit var searchButtonView: View
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var mediaLibrary: AbstractMedialibrary
    var actionMode: ActionMode? = null
    var fabPlay: FloatingActionButton? = null
    private var savedSelection = SparseBooleanArray()
    private val transition = ChangeBounds().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }

    open lateinit var viewModel: T
        protected set
    open val hasTabs = false

    abstract fun getTitle(): String
    abstract fun getMultiHelper(): MultiSelectHelper<T>?

    open val subTitle: String?
        get() = null

    val menu: Menu?
        get() = (activity as? AudioPlayerContainerActivity)?.menu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaLibrary = AbstractMedialibrary.getInstance()
        setHasOptionsMenu(!AndroidDevices.isAndroidTv)
        if (savedInstanceState?.keySet()?.contains(KEY_SELECTION) == true) {
            savedSelection = (savedInstanceState.getParcelable(KEY_SELECTION) as SparseBooleanArrayParcelable).data
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchButtonView = view.findViewById(R.id.searchButton)
        view.findViewById<SwipeRefreshLayout>(R.id.swipeLayout)?.let {
            swipeRefreshLayout = it
            it.setColorSchemeResources(R.color.orange700)
        }
        if (hasFAB()) fabPlay = requireActivity().findViewById(R.id.fab)
    }

    protected open fun hasFAB() = ::swipeRefreshLayout.isInitialized

    protected open fun setBreadcrumb() {
        activity?.findViewById<RecyclerView>(R.id.ariane)?.visibility = View.GONE
    }

    private fun releaseBreadCrumb() {
        activity?.findViewById<RecyclerView>(R.id.ariane)?.adapter = null
    }

    override fun onStart() {
        super.onStart()
        setBreadcrumb()
        updateActionBar()
        setFabPlayVisibility(true)
        fabPlay?.setOnClickListener { v -> onFabPlayClick(v) }
    }

    override fun onStop() {
        super.onStop()
        releaseBreadCrumb()
        setFabPlayVisibility(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        getMultiHelper()?.let {
            outState.putParcelable(KEY_SELECTION, SparseBooleanArrayParcelable(it.selectionMap))
        }
        super.onSaveInstanceState(outState)
    }

    private fun updateActionBar() {
        val activity = activity as? AppCompatActivity ?: return
        activity.supportActionBar?.let {
            it.title = getTitle()
            it.subtitle = subTitle
            activity.invalidateOptionsMenu()
        }
        if (activity is ContentActivity) activity.setTabLayoutVisibility(hasTabs)
    }


    open fun setFabPlayVisibility(enable: Boolean) {
        fabPlay?.run {
            if (enable) show()
            else hide()
        }
    }

    open fun onFabPlayClick(view: View) {}
    abstract fun onRefresh()
    open fun clear() {}

    protected open fun removeItems(items: List<MediaLibraryItem>) {
        if (items.size == 1) {
            removeItem(items[0])
            return
        }
        val v = view ?: return
        snackerConfirm(v,getString(R.string.confirm_delete_several_media, items.size)) {
            for (item in items) {
                if (!isStarted()) break
                when(item) {
                    is AbstractMediaWrapper -> if (getWritePermission(item.uri)) deleteMedia(item)
                    is AbstractPlaylist -> withContext(Dispatchers.IO) { item.delete() }
                }
            }
            if (isStarted()) viewModel.refresh()
        }
    }

    protected open fun removeItem(item: MediaLibraryItem): Boolean {
        view ?: return false
        when {
            item.itemType == MediaLibraryItem.TYPE_PLAYLIST -> UiTools.snackerConfirm(view!!, getString(R.string.confirm_delete_playlist, item.title), Runnable { MediaUtils.deletePlaylist(item as AbstractPlaylist) })
            item.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                val deleteAction = Runnable {
                    if (isStarted()) launch { deleteMedia(item, false, null) }
                }
                val resid = if ((item as AbstractMediaWrapper).type == AbstractMediaWrapper.TYPE_DIR) R.string.confirm_delete_folder else R.string.confirm_delete
                UiTools.snackerConfirm(view!!, getString(resid, item.getTitle()), Runnable { if (Util.checkWritePermission(requireActivity(), item, deleteAction)) deleteAction.run() })
            }
            else -> return false
        }
        return true
    }

    protected suspend fun deleteMedia(mw: MediaLibraryItem, refresh: Boolean = false, failCB: Runnable? = null) = withContext(Dispatchers.IO) {
        val foldersToReload = LinkedList<String>()
        val mediaPaths = LinkedList<String>()
        for (media in mw.tracks) {
            val path = media.uri.path
            val parentPath = FileUtils.getParent(path)
            if (FileUtils.deleteFile(media.uri)) parentPath?.let {
                if (media.id > 0L && !foldersToReload.contains(it)) {
                    foldersToReload.add(it)
                }
                mediaPaths.add(media.location)
            } else
                onDeleteFailed(media)
        }
        for (folder in foldersToReload) mediaLibrary.reload(folder)
        if (isStarted()) launch {
            if (mediaPaths.isEmpty()) {
                failCB?.run()
                return@launch
            }
            if (refresh) onRefresh()
        }
    }

    private fun onDeleteFailed(media: AbstractMediaWrapper) {
        if (isAdded) view?.let { UiTools.snacker(it, getString(R.string.msg_delete_failed, media.title)) }
    }

    protected fun showInfoDialog(item: MediaLibraryItem) {
        val i = Intent(activity, InfoActivity::class.java)
        i.putExtra(TAG_ITEM, item)
        startActivity(i)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        (viewModel as? MedialibraryViewModel)?.run {
            menu.findItem(R.id.ml_menu_sortby).isVisible = canSortByName()
            menu.findItem(R.id.ml_menu_sortby_filename).isVisible = canSortByFileNameName()
            menu.findItem(R.id.ml_menu_sortby_artist_name).isVisible = canSortByArtist()
            menu.findItem(R.id.ml_menu_sortby_album_name).isVisible = canSortByAlbum()
            menu.findItem(R.id.ml_menu_sortby_length).isVisible = canSortByDuration()
            menu.findItem(R.id.ml_menu_sortby_date).isVisible = canSortByReleaseDate()
            menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = canSortByLastModified()
            menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
        }
        sortMenuTitles()
    }

    open fun sortMenuTitles() {
        (viewModel as? MedialibraryViewModel)?.let { model ->
            menu?.let { UiTools.updateSortTitles(it, model.providers[0]) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ml_menu_sortby_name -> {
                sortBy(AbstractMedialibrary.SORT_ALPHA)
                return true
            }
            R.id.ml_menu_sortby_filename -> {
                sortBy(AbstractMedialibrary.SORT_FILENAME)
                return true
            }
            R.id.ml_menu_sortby_length -> {
                sortBy(AbstractMedialibrary.SORT_DURATION)
                return true
            }
            R.id.ml_menu_sortby_date -> {
                sortBy(AbstractMedialibrary.SORT_RELEASEDATE)
                return true
            }
            R.id.ml_menu_sortby_last_modified -> {
                sortBy(AbstractMedialibrary.SORT_LASTMODIFICATIONDATE)
                return true
            }
            R.id.ml_menu_sortby_artist_name -> {
                sortBy(AbstractMedialibrary.SORT_ARTIST)
                return true
            }
            R.id.ml_menu_sortby_album_name -> {
                sortBy(AbstractMedialibrary.SORT_ALBUM)
                return true
            }
            R.id.ml_menu_sortby_number -> {
                sortBy(AbstractMedialibrary.SORT_FILESIZE) //TODO
                return super.onOptionsItemSelected(item)
            }
            R.id.video_min_group_length_disable -> {
                Settings.getInstance(requireActivity()).edit().putString("video_min_group_length", "-1").apply()
                (activity as ContentActivity).forceLoadVideoFragment()
                return true
            }
            R.id.video_min_group_length_folder -> {
                Settings.getInstance(requireActivity()).edit().putString("video_min_group_length", "0").apply()
                (activity as ContentActivity).forceLoadVideoFragment()
                return true
            }
            R.id.video_min_group_length_name -> {
                Settings.getInstance(requireActivity()).edit().putString("video_min_group_length", "6").apply()
                (activity as ContentActivity).forceLoadVideoFragment()
                return true
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
        actionMode?.invalidate()
    }

    fun restoreMultiSelectHelper() {
        getMultiHelper()?.let {

            if (savedSelection.size() > 0) {
                var hasOneSelected = false
                for (i in 0 until savedSelection.size()) {

                    it.selectionMap.append(savedSelection.keyAt(i), savedSelection.valueAt(i))
                    if (savedSelection.valueAt(i)) hasOneSelected = true
                }
                if (hasOneSelected) startActionMode()
                savedSelection.clear()
            }
        }
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun filter(query: String) = viewModel.filter(query)

    override fun restoreList() = viewModel.restore()

    override fun enableSearchOption() = true

    override fun getFilterQuery() = viewModel.filterQuery

    override fun setSearchVisibility(visible: Boolean) {
        if (searchButtonView.visibility == View.VISIBLE == visible) return
        if (searchButtonView.parent is ConstraintLayout) {
            val cl = searchButtonView.parent as ConstraintLayout
            val cs = ConstraintSet()
            cs.clone(cl)
            cs.setVisibility(R.id.searchButton, if (visible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            transition.excludeChildren(RecyclerView::class.java, true)
            TransitionManager.beginDelayedTransition(cl, transition)
            cs.applyTo(cl)
        } else
            searchButtonView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun allowedToExpand() = true
}
