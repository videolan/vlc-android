/**
 * **************************************************************************
 * BaseBrowserFragment.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser

import android.arch.lifecycle.Observer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.preference.PreferenceManager
import android.support.v7.view.ActionMode
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.*
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DirectoryBrowserBinding
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.network.MRLPanelFragment.KEY_MRL
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaDatabase
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.browser.BrowserModel
import java.util.*

const val TAG = "VLC/BaseBrowserFragment"

internal const val KEY_MEDIA = "key_media"
private const val KEY_POSITION = "key_list"
private const val MSG_SHOW_LOADING = 0
internal const val MSG_HIDE_LOADING = 1
private const val MSG_REFRESH = 3

abstract class BaseBrowserFragment : MediaBrowserFragment<BrowserModel>(), IRefreshable, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, IEventsHandler, CtxActionReceiver {

    protected val handler = BrowserFragmentHandler(this)
    private lateinit var layoutManager: LinearLayoutManager
    var mrl: String? = null
    protected var currentMedia: MediaWrapper? = null
    private var savedPosition = -1
    var isRootDirectory: Boolean = false
    protected var goBack = false
    protected var showHiddenFiles: Boolean = false
    protected lateinit var adapter: BaseBrowserAdapter
    protected abstract val categoryTitle: String

    protected lateinit var binding: DirectoryBrowserBinding


    protected abstract fun createFragment(): Fragment
    protected abstract fun browseRoot()

    override fun onCreate(bundle: Bundle?) {
        @Suppress("NAME_SHADOWING")
        var bundle = bundle
        super.onCreate(bundle)
        if (bundle == null) bundle = arguments
        if (bundle != null) {
            currentMedia = bundle.getParcelable(KEY_MEDIA)
            mrl = if (currentMedia != null) currentMedia!!.location else bundle.getString(KEY_MRL)
            savedPosition = bundle.getInt(KEY_POSITION)
        } else if (requireActivity().intent != null) {
            mrl = requireActivity().intent.dataString
            requireActivity().intent = null
        }
        showHiddenFiles = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("browser_show_hidden_files", false)
        isRootDirectory = defineIsRoot()
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        super.onPrepareOptionsMenu(menu)
        val item = menu!!.findItem(R.id.ml_menu_filter)
        if (item != null) item.isVisible = enableSearchOption()
        val sortItem = menu.findItem(R.id.ml_menu_sortby)
        if (sortItem != null) sortItem.isVisible = !isRootDirectory
    }

    protected open fun defineIsRoot(): Boolean {
        return mrl == null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DirectoryBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (!this::adapter.isInitialized) adapter = BaseBrowserAdapter(this)
        layoutManager = LinearLayoutManager(activity)
        binding.networkList.layoutManager = layoutManager
        binding.networkList.adapter = adapter
        mSwipeRefreshLayout.setOnRefreshListener(this)
        viewModel.dataset.observe(this, Observer<MutableList<MediaLibraryItem>> { mediaLibraryItems -> adapter.update(mediaLibraryItems!!) })
        viewModel.getDescriptionUpdate().observe(this, Observer { pair -> if (pair != null) adapter.notifyItemChanged(pair.first, pair.second) })
        initFavorites()
    }

    override fun setBreadcrumb() {
        val ariane = requireActivity().findViewById<RecyclerView>(R.id.ariane)
        currentMedia?.let {
            ariane.visibility = View.VISIBLE
            ariane.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            ariane.adapter = PathAdapter(this, Uri.decode(it.uri.path))
            if (ariane.itemDecorationCount == 0) {
                val did = DividerItemDecoration(requireContext(), DividerItemDecoration.HORIZONTAL)
                did.setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider_grey_50_18dp)!!)
                ariane.addItemDecoration(did)
            }
            ariane.scrollToPosition(ariane.adapter.itemCount - 1)
        } ?: run { ariane.visibility = View.GONE }
    }

    fun backTo(tag: String) {
        requireActivity().supportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    protected open fun initFavorites() {}

    override fun onStart() {
        super.onStart()
        mFabPlay?.run {
            setImageResource(R.drawable.ic_fab_play)
            updateFab()
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentMedia != null) setSearchVisibility(false)
        if (goBack) goBack()
        else restoreList()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_MRL, mrl)
        outState.putParcelable(KEY_MEDIA, currentMedia)
        outState.putInt(KEY_POSITION, layoutManager.findFirstCompletelyVisibleItemPosition())
        super.onSaveInstanceState(outState)
    }

    override fun getTitle(): String? {
        return when {
            isRootDirectory -> categoryTitle
            currentMedia != null -> currentMedia!!.title
            else -> mrl
        }
    }

    public override fun getSubTitle(): String? {
        if (isRootDirectory) return null
        var mrl = Strings.removeFileProtocole(mrl)
        if (!TextUtils.isEmpty(mrl)) {
            if (this is FileBrowserFragment && mrl.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                mrl = getString(R.string.internal_memory) + mrl.substring(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY.length)
            mrl = Uri.decode(mrl).replace("://".toRegex(), " ").replace("/".toRegex(), " > ")
        }
        return if (currentMedia != null) mrl else null
    }

    fun goBack(): Boolean {
        val activity = activity ?: return false
        if (!isRootDirectory) activity.supportFragmentManager.popBackStack()
        return !isRootDirectory
    }

    fun browse(media: MediaWrapper, save: Boolean) {
        val ctx = activity
        if (ctx == null || !isResumed || isRemoving) return
        val ft = ctx.supportFragmentManager.beginTransaction()
        val next = createFragment()
        val args = Bundle()
        viewModel.saveList(media)
        args.putParcelable(KEY_MEDIA, media)
        next.arguments = args
        if (save) ft.addToBackStack(if (isRootDirectory) "root" else FileUtils.getFileNameFromPath(mrl))
        ft.replace(R.id.fragment_placeholder, next, media.title)
        ft.commit()
    }

    override fun onRefresh() {
        savedPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        viewModel.refresh()
    }

    /**
     * Update views visibility and emptiness info
     */
    protected open fun updateEmptyView() {
        if (mSwipeRefreshLayout == null) return
        if (Util.isListEmpty(getViewModel().dataset.value)) {
            if (mSwipeRefreshLayout.isRefreshing) {
                binding.empty.setText(R.string.loading)
                binding.empty.visibility = View.VISIBLE
                binding.networkList.visibility = View.GONE
            } else {
                binding.empty.setText(R.string.directory_empty)
                binding.empty.visibility = View.VISIBLE
                binding.networkList.visibility = View.GONE
            }
        } else if (binding.empty.visibility == View.VISIBLE) {
            binding.empty.visibility = View.GONE
            binding.networkList.visibility = View.VISIBLE
        }
    }

    override fun refresh() {
        viewModel.refresh()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.fab -> playAll(null)
        }
    }

    class BrowserFragmentHandler(owner: BaseBrowserFragment) : WeakHandler<BaseBrowserFragment>(owner) {

        override fun handleMessage(msg: Message) {
            val fragment = owner ?: return
            when (msg.what) {
                MSG_SHOW_LOADING -> fragment.mSwipeRefreshLayout.isRefreshing = true
                MSG_HIDE_LOADING -> {
                    removeMessages(MSG_SHOW_LOADING)
                    fragment.mSwipeRefreshLayout?.isRefreshing = false
                }
                MSG_REFRESH -> {
                    removeMessages(MSG_REFRESH)
                    if (!fragment.isDetached) fragment.refresh()
                }
            }
        }
    }

    override fun clear() {
        adapter.clear()
    }

    private fun removeMedia(mw: MediaWrapper) {
        viewModel.remove(mw)
        val cancel = Runnable { viewModel.refresh() }
        view?.let { UiTools.snackerWithCancel(it, getString(R.string.file_deleted), Runnable { deleteMedia(mw, false, cancel) }, cancel) }
    }

    private fun showMediaInfo(mw: MediaWrapper) {
        val i = Intent(activity, InfoActivity::class.java)
        i.putExtra(InfoActivity.TAG_ITEM, mw)
        startActivity(i)
    }

    private fun playAll(mw: MediaWrapper?) {
        var positionInPlaylist = 0
        val mediaLocations = LinkedList<MediaWrapper>()
        for (file in adapter.all)
            if (file is MediaWrapper) {
                if (file.type == MediaWrapper.TYPE_VIDEO || file.type == MediaWrapper.TYPE_AUDIO) {
                    mediaLocations.add(file)
                    if (mw != null && file.equals(mw))
                        positionInPlaylist = mediaLocations.size - 1
                }
            }
        activity?.let { MediaUtils.openList(it, mediaLocations, positionInPlaylist) }
    }

    override fun enableSearchOption() = !isRootDirectory

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.action_mode_browser_file, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = adapter.selectionCount
        if (count == 0) {
            stopActionMode()
            return false
        }
        val single = this is FileBrowserFragment && count == 1
        val selection = if (single) adapter.selection else null
        val type = if (!Util.isListEmpty(selection)) selection!![0].type else -1
        menu.findItem(R.id.action_mode_file_info).isVisible = single && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO)
        menu.findItem(R.id.action_mode_file_append).isVisible = PlaylistManager.hasMedia()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val list = adapter.selection
        if (!list.isEmpty()) {
            when (item.itemId) {
                R.id.action_mode_file_play -> MediaUtils.openList(activity, list, 0)
                R.id.action_mode_file_append -> MediaUtils.appendMedia(activity, list)
                R.id.action_mode_file_add_playlist -> UiTools.addToPlaylist(activity, list)
                R.id.action_mode_file_info -> showMediaInfo(list[0])
                else -> {
                    stopActionMode()
                    return false
                }
            }
        }
        stopActionMode()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        mActionMode = null
        var index = -1
        for (media in adapter.all) {
            ++index
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                media.removeStateFlags(MediaLibraryItem.FLAG_SELECTED)
                adapter.notifyItemChanged(index, media)
            }
        }
        adapter.resetSelectionCount()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mediaWrapper = item as MediaWrapper
        if (mActionMode != null) {
            if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                    mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                    mediaWrapper.type == MediaWrapper.TYPE_DIR) {
                item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED)
                adapter.updateSelectionCount(mediaWrapper.hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
                adapter.notifyItemChanged(position, item)
                invalidateActionMode()
            }
        } else {
            mediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            if (mediaWrapper.type == MediaWrapper.TYPE_DIR) browse(mediaWrapper, true)
            else MediaUtils.openMedia(v.context, mediaWrapper)
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        if (mActionMode != null || item.itemType != MediaLibraryItem.TYPE_MEDIA) return false
        val mediaWrapper = item as MediaWrapper
        if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                mediaWrapper.type == MediaWrapper.TYPE_DIR) {
            if (mActionMode != null) return false
            item.setStateFlags(MediaLibraryItem.FLAG_SELECTED)
            adapter.updateSelectionCount(mediaWrapper.hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
            adapter.notifyItemChanged(position, item)
            startActionMode()
        } else onCtxClick(v, position, item)
        return true
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        if (mActionMode == null && item.itemType == MediaLibraryItem.TYPE_MEDIA) uiJob(false) {
            val mw = item as MediaWrapper
            var flags = 0
            if (!isRootDirectory && this is FileBrowserFragment) flags = flags or Constants.CTX_DELETE
            if (mw.type == MediaWrapper.TYPE_DIR) {
                val isEmpty = viewModel.isFolderEmpty(mw)
                if (!isEmpty) flags = flags or Constants.CTX_PLAY
                if (this@BaseBrowserFragment is NetworkBrowserFragment) {
                    val favExists = withContext(VLCIO) { MediaDatabase.getInstance().networkFavExists(mw.uri) }
                    flags = if (favExists) flags or Constants.CTX_NETWORK_EDIT or Constants.CTX_NETWORK_REMOVE
                    else flags or Constants.CTX_NETWORK_ADD
                }
            } else {
                val isVideo = mw.type == MediaWrapper.TYPE_VIDEO
                val isAudio = mw.type == MediaWrapper.TYPE_AUDIO
                val isMedia = isVideo || isAudio
                if (isMedia) flags = flags or Constants.CTX_PLAY_ALL or Constants.CTX_APPEND or Constants.CTX_INFORMATION
                flags = if (!isAudio) flags or Constants.CTX_PLAY_AS_AUDIO else flags or Constants.CTX_ADD_TO_PLAYLIST
                if (isVideo) flags = flags or Constants.CTX_DOWNLOAD_SUBTITLES
            }
            if (flags != 0) showContext(requireActivity(), this@BaseBrowserFragment, position, item.getTitle(), flags)
        }
    }

    override fun onCtxAction(position: Int, option: Int) {
        if (adapter.getItem(position) !is MediaWrapper) return
        val uri = (adapter.getItem(position) as MediaWrapper).uri
        val mwFromMl = if ("file" == uri.scheme) mMediaLibrary.getMedia(uri) else null
        val mw = mwFromMl ?: adapter.getItem(position) as MediaWrapper

        when (option) {
            Constants.CTX_PLAY -> MediaUtils.openMedia(activity, mw)
            Constants.CTX_PLAY_ALL -> {
                mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                playAll(mw)
            }
            Constants.CTX_APPEND -> MediaUtils.appendMedia(activity, mw)
            Constants.CTX_DELETE -> if (checkWritePermission(mw) { removeMedia(mw) })
                removeMedia(mw)
            Constants.CTX_INFORMATION -> showMediaInfo(mw)
            Constants.CTX_PLAY_AS_AUDIO -> {
                mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                MediaUtils.openMedia(activity, mw)
            }
            Constants.CTX_ADD_TO_PLAYLIST -> UiTools.addToPlaylist(requireActivity(), mw.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            Constants.CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs(activity, mw)
        }
    }

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        if (mSwipeRefreshLayout != null) mSwipeRefreshLayout.isRefreshing = false
        handler.sendEmptyMessage(MSG_HIDE_LOADING)
        updateEmptyView()
        if (!Util.isListEmpty(getViewModel().dataset.value)) {
            if (savedPosition > 0) {
                layoutManager.scrollToPositionWithOffset(savedPosition, 0)
                savedPosition = 0
            }
        }
        if (!isRootDirectory) {
            updateFab()
            UiTools.updateSortTitles(this)
        }
    }

    private fun updateFab() {
        mFabPlay?.let {
            if (adapter.mediaCount > 0) {
                it.visibility = View.VISIBLE
                it.setOnClickListener(this)
            } else {
                it.visibility = View.GONE
                it.setOnClickListener(null)
            }
        }
    }
}
