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

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.parcelableList
import org.videolan.tools.MultiSelectHelper
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_MEDIALIST
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_DEFAULT_VALUE
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_TYPE
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.fillActionMode
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.viewmodels.DisplaySettingsViewModel
import org.videolan.vlc.viewmodels.MedialibraryViewModel
import org.videolan.vlc.viewmodels.SortableModel
import org.videolan.vlc.viewmodels.prepareOptionsMenu
import org.videolan.vlc.viewmodels.sortMenuTitles

private const val TAG = "VLC/MediaBrowserFragment"
private const val KEY_SELECTION = "key_selection"

abstract class MediaBrowserFragment<T : SortableModel> : BaseFragment(), Filterable {

    private lateinit var searchButtonView: View
    lateinit var mediaLibrary: Medialibrary
    private var savedSelection = ArrayList<Int>()
    private val transition = ChangeBounds().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }

    val displaySettingsViewModel: DisplaySettingsViewModel by activityViewModels()

    /**
     * Triggered when a display setting is changed
     *
     * @param key the display settings key
     * @param value the new display settings value
     */
    open fun onDisplaySettingChanged(key:String, value:Any) { }

    open lateinit var viewModel: T
        protected set

    abstract fun getMultiHelper(): MultiSelectHelper<T>?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaLibrary = Medialibrary.getInstance()
        (savedInstanceState?.getIntegerArrayList(KEY_SELECTION))?.let { savedSelection = it }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchButtonView = view.findViewById(R.id.searchButton)
        viewLifecycleOwner.lifecycleScope.launch {
            //listen to display settings changes
            displaySettingsViewModel.settingChangeFlow
                    .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                    .collect {
                        if (isResumed) {
                            onDisplaySettingChanged(it.key, it.value)
                            displaySettingsViewModel.consume()
                        }
                    }
        }
        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_DELETE_DIALOG_RESULT, viewLifecycleOwner) { requestKey, bundle ->
            val items: List<MediaLibraryItem> = bundle.parcelableList(CONFIRM_DELETE_DIALOG_MEDIALIST) ?: listOf()
            val type = bundle.getInt(CONFIRM_DELETE_DIALOG_RESULT_TYPE)
            if (type == CONFIRM_DELETE_DIALOG_RESULT_DEFAULT_VALUE) {
                for (item in items) {
                    items.forEach { mw ->
                        val deleteAction = Runnable {
                            lifecycleScope.launch {
                                MediaUtils.deleteItem(requireActivity(), mw) { viewModel.refresh() }
                                if (this@MediaBrowserFragment is FileBrowserFragment)
                                    viewModel.remove(mw)
                            }
                        }
                        if (Permissions.checkWritePermission(requireActivity(), (item as MediaWrapper), deleteAction)) deleteAction.run()
                    }
                }
            } else if (type == CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER) {
                items.forEach {
                    val path = if (it is Folder) it.mMrl.toUri().path else if (it is MediaWrapper) it.uri.path else null
                    path?.let { path ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val roots: Array<String> = Medialibrary.getInstance().foldersList
                            val strippedPath = path.removePrefix("file://")
                            for (root in roots) {
                                if (root.removePrefix("file://") == strippedPath) {
                                    Log.w(TAG, "banFolder: trying to ban root: $root")
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        UiTools.snacker(requireActivity(), getString(R.string.cant_ban_root))
                                    }
                                    return@launch
                                }
                            }
                            MedialibraryUtils.banDir(strippedPath)
                        }

                    } ?: Log.e(TAG, "banFolder: path is null")
                }
            }
        }

    }

    protected open fun setBreadcrumb() {
        activity?.findViewById<RecyclerView>(R.id.ariane)?.visibility = View.GONE
    }

    private fun releaseBreadCrumb() {
        activity?.findViewById<RecyclerView>(R.id.ariane)?.adapter = null
    }

    override fun onStart() {
        super.onStart()
        setBreadcrumb()
    }

    override fun onStop() {
        super.onStop()
        releaseBreadCrumb()
    }

    override fun onResume() {
        super.onResume()
        (viewModel as? MedialibraryViewModel)?.resume()
    }


    override fun onPause() {
        super.onPause()
        (viewModel as? MedialibraryViewModel)?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        savedSelection.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        getMultiHelper()?.let {
            outState.putIntegerArrayList(KEY_SELECTION, it.selectionMap)
        }
        super.onSaveInstanceState(outState)
    }

    abstract fun onRefresh()
    open fun clear() {}

    protected open fun removeItems(items: List<MediaLibraryItem>) {
        if (items.size == 1) {
            removeItem(items[0])
            return
        }
        val dialog = ConfirmDeleteDialog.newInstance(ArrayList(items))
        dialog.show(requireActivity().supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
    }

    protected open fun removeItem(item: MediaLibraryItem): Boolean {
        val dialog = ConfirmDeleteDialog.newInstance(arrayListOf(item))
        dialog.show(requireActivity().supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
        return true
    }

    private fun onDeleteFailed(item: MediaLibraryItem) {
        if (isAdded) UiTools.snacker(requireActivity(), getString(R.string.msg_delete_failed, item.title))
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        (viewModel as? MedialibraryViewModel)?.prepareOptionsMenu(menu)
        sortMenuTitles()
    }

    open fun sortMenuTitles(index : Int = 0) {
        menu?.let { (viewModel as? MedialibraryViewModel)?.sortMenuTitles(it, index) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
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
            R.id.ml_menu_sortby_insertion_date -> {
                sortBy(Medialibrary.SORT_INSERTIONDATE)
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
            R.id.ml_menu_sortby_media_number -> {
                sortBy(Medialibrary.NbMedia)
                return true
            }
            R.id.ml_menu_sortby_number -> {
                sortBy(Medialibrary.SORT_FILESIZE) //TODO
                return super.onOptionsItemSelected(item)
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    open fun sortBy(sort: Int) {
        viewModel.sort(sort)
    }

    fun restoreMultiSelectHelper() {
        getMultiHelper()?.let {

            if (savedSelection.size > 0) {
                var hasOneSelected = false
                for (i in 0 until savedSelection.size) {

                    it.selectionMap.add(savedSelection[i])
                    hasOneSelected = savedSelection.isNotEmpty()
                }
                if (hasOneSelected) startActionMode()
                savedSelection.clear()
            }
            if (actionMode != null)
                lifecycleScope.launch(Dispatchers.Main) {
                    @Suppress("UNCHECKED_CAST")
                    fillActionMode(requireActivity(), actionMode!!, it as MultiSelectHelper<MediaLibraryItem>)
                }
        }
    }

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

    fun inSearchMode() = searchButtonView.visibility == View.VISIBLE

    override fun allowedToExpand() = true
}
