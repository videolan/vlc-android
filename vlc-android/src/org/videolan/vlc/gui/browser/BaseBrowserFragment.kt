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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DirectoryBrowserBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.OTG_SCHEME
import org.videolan.vlc.gui.view.VLCDividerItemDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.browser.BrowserModel
import java.util.*

private const val TAG = "VLC/BaseBrowserFragment"

internal const val KEY_MEDIA = "key_media"
private const val KEY_POSITION = "key_list"
private const val MSG_SHOW_LOADING = 0
internal const val MSG_HIDE_LOADING = 1
private const val MSG_REFRESH = 3

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
abstract class BaseBrowserFragment : MediaBrowserFragment<BrowserModel>(), IRefreshable, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, IEventsHandler, CtxActionReceiver, PathAdapterListener {

    protected val handler = BrowserFragmentHandler(this)
    private lateinit var layoutManager: LinearLayoutManager
    var mrl: String? = null
    protected var currentMedia: AbstractMediaWrapper? = null
    private var savedPosition = -1
    var isRootDirectory: Boolean = false
    protected var goBack = false
    protected var showHiddenFiles: Boolean = false
    protected lateinit var adapter: BaseBrowserAdapter
    protected abstract val categoryTitle: String

    protected lateinit var binding: DirectoryBrowserBinding
    protected lateinit var browserFavRepository: BrowserFavRepository

    protected abstract fun createFragment(): Fragment
    protected abstract fun browseRoot()

    override fun onCreate(bundle: Bundle?) {
        @Suppress("NAME_SHADOWING")
        var bundle = bundle
        super.onCreate(bundle)
        if (bundle == null) bundle = arguments
        if (bundle != null) {
            currentMedia = bundle.getParcelable(KEY_MEDIA)
            mrl = currentMedia?.location ?: bundle.getString(KEY_MRL)
            savedPosition = bundle.getInt(KEY_POSITION)
        } else if (requireActivity().intent != null) {
            mrl = requireActivity().intent.dataString
            requireActivity().intent = null
        }
        showHiddenFiles = Settings.getInstance(requireContext()).getBoolean("browser_show_hidden_files", false)
        isRootDirectory = defineIsRoot()
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_filter)?.isVisible = enableSearchOption()
        menu.findItem(R.id.ml_menu_sortby)?.isVisible = !isRootDirectory
    }

    protected open fun defineIsRoot() = mrl == null

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
        registerSwiperRefreshlayout()
        viewModel.dataset.observe(this, Observer<MutableList<MediaLibraryItem>> { mediaLibraryItems -> adapter.update(mediaLibraryItems!!) })
        viewModel.getDescriptionUpdate().observe(this, Observer { pair -> if (pair != null) adapter.notifyItemChanged(pair.first, pair.second) })
        viewModel.loading.observe(this, Observer {
            (activity as? MainActivity)?.refreshing = it
            updateEmptyView()
        })
    }

    open fun registerSwiperRefreshlayout() = swipeRefreshLayout.setOnRefreshListener(this)

    override fun setBreadcrumb() {
        val ariane = requireActivity().findViewById<RecyclerView>(R.id.ariane) ?: return
        val media = currentMedia
        if (media != null && isSchemeSupported(media.uri?.scheme)) {
            ariane.visibility = View.VISIBLE
            ariane.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            ariane.adapter = PathAdapter(this, media)
            if (ariane.itemDecorationCount == 0) {
                ariane.addItemDecoration(VLCDividerItemDecoration(requireContext(), DividerItemDecoration.HORIZONTAL, ContextCompat.getDrawable(requireContext(), R.drawable.ic_divider)!!))
            }
            ariane.scrollToPosition(ariane.adapter!!.itemCount - 1)
        } else ariane.visibility = View.GONE
    }

    override fun backTo(tag: String) {
        requireActivity().supportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun currentContext() = requireContext()

    override fun showRoot() = true

    override fun onStart() {
        super.onStart()
        fabPlay?.run {
            setImageResource(R.drawable.ic_fab_play)
            updateFab()
        }
        (activity as? AudioPlayerContainerActivity)?.expandAppBar()
    }

    override fun onResume() {
        super.onResume()
        if (goBack) goBack()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_MRL, mrl)
        outState.putParcelable(KEY_MEDIA, currentMedia)
        outState.putInt(KEY_POSITION, if (::layoutManager.isInitialized) layoutManager.findFirstCompletelyVisibleItemPosition() else 0)
        super.onSaveInstanceState(outState)
    }

    override fun getTitle(): String = when {
        isRootDirectory -> categoryTitle
        currentMedia != null -> currentMedia!!.title
        else -> mrl ?: ""
    }

    override fun getMultiHelper(): MultiSelectHelper<BrowserModel>? = if (::adapter.isInitialized) adapter.multiSelectHelper as? MultiSelectHelper<BrowserModel> else null

    override val subTitle: String? =
            if (isRootDirectory) null else {
                var mrl = mrl?.removeFileProtocole() ?: ""
                if (!TextUtils.isEmpty(mrl)) {
                    if (this is FileBrowserFragment && mrl.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                        mrl = getString(R.string.internal_memory) + mrl.substring(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY.length)
                    mrl = Uri.decode(mrl).replace("://".toRegex(), " ").replace("/".toRegex(), " > ")
                }
                if (currentMedia != null) mrl else null
            }

    fun goBack(): Boolean {
        val activity = activity ?: return false
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        if (!isRootDirectory && !activity.isFinishing && !activity.isDestroyed) activity.supportFragmentManager.popBackStack()
        return !isRootDirectory
    }

    fun browse(media: AbstractMediaWrapper, save: Boolean) {
        val ctx = activity
        if (ctx == null || !isResumed || isRemoving) return
        val ft = ctx.supportFragmentManager.beginTransaction()
        val next = createFragment()
        val args = Bundle()
        viewModel.saveList(media)
        args.putParcelable(KEY_MEDIA, media)
        next.arguments = args
        if (save) ft.addToBackStack(if (isRootDirectory) "root" else currentMedia?.title
                ?: FileUtils.getFileNameFromPath(mrl))
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
        swipeRefreshLayout.let {
            if (Util.isListEmpty(viewModel.dataset.value)) {
                if (it.isRefreshing) {
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
    }

    override fun refresh() = viewModel.refresh()

    override fun onClick(v: View) {
        when (v.id) {
            R.id.fab -> playAll(null)
        }
    }

    class BrowserFragmentHandler(owner: BaseBrowserFragment) : WeakHandler<BaseBrowserFragment>(owner) {

        override fun handleMessage(msg: Message) {
            val fragment = owner ?: return
            when (msg.what) {
                MSG_SHOW_LOADING -> fragment.swipeRefreshLayout.isRefreshing = true
                MSG_HIDE_LOADING -> {
                    removeMessages(MSG_SHOW_LOADING)
                    fragment.swipeRefreshLayout.isRefreshing = false
                }
                MSG_REFRESH -> {
                    removeMessages(MSG_REFRESH)
                    if (!fragment.isDetached) fragment.refresh()
                }
            }
        }
    }

    override fun clear() = adapter.clear()

    override fun removeItem(item: MediaLibraryItem): Boolean {

        val view = view ?: return false
        val mw = item as? AbstractMediaWrapper ?: return false
        val cancel = Runnable { viewModel.refresh() }
        val deleteAction = Runnable {
            launch {
                deleteMedia(mw, false, cancel)
                viewModel.remove(mw)
            }
        }
        val resId = if (mw.type == AbstractMediaWrapper.TYPE_DIR) R.string.confirm_delete_folder else R.string.confirm_delete
        UiTools.snackerConfirm(view, getString(resId, mw.title), Runnable { if (Util.checkWritePermission(requireActivity(), mw, deleteAction)) deleteAction.run() })
        return true
    }

    private fun showMediaInfo(mw: AbstractMediaWrapper) {
        val i = Intent(activity, InfoActivity::class.java)
        i.putExtra(TAG_ITEM, mw)
        startActivity(i)
    }

    private fun playAll(mw: AbstractMediaWrapper?) {
        var positionInPlaylist = 0
        val mediaLocations = LinkedList<AbstractMediaWrapper>()
        for (file in viewModel.dataset.value)
            if (file is AbstractMediaWrapper) {
                if (file.type == AbstractMediaWrapper.TYPE_VIDEO || file.type == AbstractMediaWrapper.TYPE_AUDIO) {
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
        val count = adapter.multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        val fileBrowser = this is FileBrowserFragment
        val single = fileBrowser && count == 1
        val selection = if (single) adapter.multiSelectHelper.getSelection() else null
        val type = if (!Util.isListEmpty(selection)) (selection!![0] as AbstractMediaWrapper).type else -1
        menu.findItem(R.id.action_mode_file_info).isVisible = single && (type == AbstractMediaWrapper.TYPE_AUDIO || type == AbstractMediaWrapper.TYPE_VIDEO)
        menu.findItem(R.id.action_mode_file_append).isVisible = PlaylistManager.hasMedia()
        menu.findItem(R.id.action_mode_file_delete).isVisible = fileBrowser
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        val list = adapter.multiSelectHelper.getSelection() as? List<AbstractMediaWrapper> ?: return false
        if (list.isNotEmpty()) {
            when (item.itemId) {
                R.id.action_mode_file_play -> MediaUtils.openList(activity, list, 0)
                R.id.action_mode_file_append -> MediaUtils.appendMedia(activity, list)
                R.id.action_mode_file_add_playlist -> UiTools.addToPlaylist(requireActivity(), list)
                R.id.action_mode_file_info -> showMediaInfo(list[0])
                R.id.action_mode_file_delete -> removeItems(list)
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
        actionMode = null
        adapter.multiSelectHelper.clearSelection()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_save -> {
                toggleFavorite()
                menu?.let { onPrepareOptionsMenu(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleFavorite() = launch {
        val mw = currentMedia ?: return@launch
        withContext(Dispatchers.IO) {
            when {
                browserFavRepository.browserFavExists(mw.uri) -> browserFavRepository.deleteBrowserFav(mw.uri)
                mw.uri.scheme == "file" -> browserFavRepository.addLocalFavItem(mw.uri, mw.title, mw.artworkURL)
                else -> browserFavRepository.addNetworkFavItem(mw.uri, mw.title, mw.artworkURL)
            }
        }
        activity?.invalidateOptionsMenu()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mediaWrapper = item as AbstractMediaWrapper
        if (actionMode != null) {
            if (mediaWrapper.type == AbstractMediaWrapper.TYPE_AUDIO ||
                    mediaWrapper.type == AbstractMediaWrapper.TYPE_VIDEO ||
                    mediaWrapper.type == AbstractMediaWrapper.TYPE_DIR) {
                adapter.multiSelectHelper.toggleSelection(position)
                invalidateActionMode()
            }
        } else {
            mediaWrapper.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
            if (mediaWrapper.type == AbstractMediaWrapper.TYPE_DIR) browse(mediaWrapper, true)
            else MediaUtils.openMedia(v.context, mediaWrapper)
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        if (item.itemType != MediaLibraryItem.TYPE_MEDIA) return false
        val mediaWrapper = item as AbstractMediaWrapper
        if (mediaWrapper.type == AbstractMediaWrapper.TYPE_AUDIO ||
                mediaWrapper.type == AbstractMediaWrapper.TYPE_VIDEO ||
                mediaWrapper.type == AbstractMediaWrapper.TYPE_DIR) {
            adapter.multiSelectHelper.toggleSelection(position)
            if (actionMode == null) startActionMode()
        } else onCtxClick(v, position, item)
        return true
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode == null && item.itemType == MediaLibraryItem.TYPE_MEDIA) launch {
            val mw = item as AbstractMediaWrapper
            if (mw.uri.scheme == "content" || mw.uri.scheme == OTG_SCHEME) return@launch
            var flags = if (!isRootDirectory && this@BaseBrowserFragment is FileBrowserFragment) CTX_DELETE else 0
            if (!isRootDirectory && this is FileBrowserFragment) flags = flags or CTX_DELETE
            if (mw.type == AbstractMediaWrapper.TYPE_DIR) {
                val isEmpty = viewModel.isFolderEmpty(mw)
                if (!isEmpty) flags = flags or CTX_PLAY
                val isFileBrowser = this@BaseBrowserFragment is FileBrowserFragment && item.uri.scheme == "file"
                val isNetworkBrowser = this@BaseBrowserFragment is NetworkBrowserFragment
                if (isFileBrowser || isNetworkBrowser) {
                    val favExists = withContext(Dispatchers.IO) { browserFavRepository.browserFavExists(mw.uri) }
                    flags = if (favExists) {
                        if (isNetworkBrowser) flags or CTX_FAV_EDIT or CTX_FAV_REMOVE
                        else flags or CTX_FAV_REMOVE
                    } else flags or CTX_FAV_ADD
                }
            } else {
                val isVideo = mw.type == AbstractMediaWrapper.TYPE_VIDEO
                val isAudio = mw.type == AbstractMediaWrapper.TYPE_AUDIO
                val isMedia = isVideo || isAudio
                if (isMedia) flags = flags or CTX_PLAY_ALL or CTX_APPEND or CTX_INFORMATION or CTX_ADD_TO_PLAYLIST
                if (!isAudio) flags = flags or CTX_PLAY_AS_AUDIO
                if (isVideo) flags = flags or CTX_DOWNLOAD_SUBTITLES
            }
            if (flags != 0) showContext(requireActivity(), this@BaseBrowserFragment, position, item.getTitle(), flags)
        }
    }

    override fun onCtxAction(position: Int, option: Int) {
        val mw = adapter.getItem(position) as? AbstractMediaWrapper
                ?: return
        when (option) {
            CTX_PLAY -> MediaUtils.openMedia(activity, mw)
            CTX_PLAY_ALL -> {
                mw.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
                playAll(mw)
            }
            CTX_APPEND -> MediaUtils.appendMedia(activity, mw)
            CTX_DELETE -> removeItem(mw)
            CTX_INFORMATION -> showMediaInfo(mw)
            CTX_PLAY_AS_AUDIO -> {
                mw.addFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
                MediaUtils.openMedia(activity, mw)
            }
            CTX_ADD_TO_PLAYLIST -> UiTools.addToPlaylist(requireActivity(), mw.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs(requireActivity(), mw)
            CTX_FAV_REMOVE -> launch(Dispatchers.IO) { browserFavRepository.deleteBrowserFav(mw.uri) }
        }
    }

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode != null) {
            onClick(v, position, item)
            return
        }
        onLongClick(v, position, item)
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {
        restoreMultiSelectHelper()
        swipeRefreshLayout.isRefreshing = false
        handler.sendEmptyMessage(MSG_HIDE_LOADING)
        updateEmptyView()
        if (!Util.isListEmpty(viewModel.dataset.value)) {
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

    override fun onItemFocused(v: View, item: MediaLibraryItem) {
    }

    private fun updateFab() {
        fabPlay?.let {
            if (adapter.mediaCount > 0) {
                it.show()
                it.setOnClickListener(this)
            } else {
                it.hide()
                it.setOnClickListener(null)
            }
        }
    }
}
