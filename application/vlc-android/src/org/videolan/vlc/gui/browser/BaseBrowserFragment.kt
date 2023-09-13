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
import android.util.Log
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.parcelable
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DirectoryBrowserBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylistAsync
import org.videolan.vlc.gui.helpers.UiTools.showMediaInfo
import org.videolan.vlc.gui.helpers.hf.OTG_SCHEME
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.VLCDividerItemDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.SchedulerCallback
import org.videolan.vlc.util.isSchemeSupported
import org.videolan.vlc.util.isTalkbackIsEnabled
import org.videolan.vlc.viewmodels.browser.BrowserModel
import java.util.*

private const val TAG = "VLC/BaseBrowserFragment"

internal const val KEY_MEDIA = "key_media"
const val KEY_PICKER_TYPE = "key_picker_type"
private const val MSG_SHOW_LOADING = "msg_show_loading"
internal const val MSG_HIDE_LOADING = "msg_hide_loading"
private const val MSG_REFRESH = "msg_refresh"
private const val MSG_SHOW_ENQUEUING = "msg_show_enqueuing"
private const val MSG_HIDE_ENQUEUING = "msg_hide_enqueuing"

abstract class BaseBrowserFragment : MediaBrowserFragment<BrowserModel>(), IRefreshable, SwipeRefreshLayout.OnRefreshListener, IEventsHandler<MediaLibraryItem>, CtxActionReceiver, PathAdapterListener, BrowserContainer<MediaLibraryItem>, SchedulerCallback, PlaybackService.Callback {

    lateinit var scheduler:LifecycleAwareScheduler
    private lateinit var addPlaylistFolderOnly: MenuItem
    private lateinit var layoutManager: LinearLayoutManager
    override var mrl: String? = null
    protected var currentMedia: MediaWrapper? = null
    override var isRootDirectory: Boolean = false
    override val scannedDirectory = false
    override val inCards = false
    protected lateinit var adapter: BaseBrowserAdapter
    protected abstract val categoryTitle: String

    protected lateinit var binding: DirectoryBrowserBinding
    protected lateinit var browserFavRepository: BrowserFavRepository

    protected abstract fun createFragment(): Fragment
    protected abstract fun browseRoot()
    private var needToRefreshMeta = false
    private var enqueuingSnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduler =  LifecycleAwareScheduler(this)
        val bundle = savedInstanceState ?: arguments
        if (bundle != null) {
            currentMedia = bundle.parcelable(KEY_MEDIA)
            mrl = currentMedia?.location ?: bundle.getString(KEY_MRL)
        } else if (requireActivity().intent != null) {
            mrl = requireActivity().intent.dataString
            requireActivity().intent = null
        }
        isRootDirectory = defineIsRoot()
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
        PlaybackService.serviceFlow.onEach {
            it?.addCallback(this)
        }.launchIn(MainScope())
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_filter)?.isVisible = enableSearchOption()
        menu.findItem(R.id.ml_menu_sortby)?.isVisible = !isRootDirectory
        menu.findItem(R.id.ml_menu_sortby_media_number)?.isVisible = false
        menu.findItem(R.id.ml_menu_add_playlist)?.isVisible = !isRootDirectory
        addPlaylistFolderOnly = menu.findItem(R.id.folder_add_playlist)
        addPlaylistFolderOnly.isVisible = adapter.mediaCount > 0
        val browserShowAllFiles = menu.findItem(R.id.browser_show_all_files)
        browserShowAllFiles.isVisible = true
        browserShowAllFiles.isChecked = Settings.getInstance(requireActivity()).getBoolean("browser_show_all_files", true)

        val browserShowHiddenFiles = menu.findItem(R.id.browser_show_hidden_files)
        browserShowHiddenFiles.isVisible = true
        browserShowHiddenFiles.isChecked = Settings.showHiddenFiles
        if (requireActivity().isTalkbackIsEnabled()) menu.findItem(R.id.play_all).isVisible = true
        UiTools.updateSortTitles(this)
    }

    protected open fun defineIsRoot() = mrl == null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DirectoryBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!this::adapter.isInitialized) adapter = BaseBrowserAdapter(this, viewModel.sort, !viewModel.desc).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        layoutManager = LinearLayoutManager(activity)
        binding.networkList.layoutManager = layoutManager
        binding.networkList.adapter = adapter
        registerSwiperRefreshlayout()
        viewModel.dataset.observe(viewLifecycleOwner) { mediaLibraryItems ->
            adapter.update(mediaLibraryItems!!)
            if (::addPlaylistFolderOnly.isInitialized) addPlaylistFolderOnly.isVisible = adapter.mediaCount > 0
        }
        viewModel.getDescriptionUpdate().observe(viewLifecycleOwner) { pair -> if (pair != null) adapter.notifyItemChanged(pair.first, pair.second) }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            swipeRefreshLayout.isRefreshing = loading
            updateEmptyView()
        }
        (view.rootView.findViewById<View?>(R.id.appbar) as? AppBarLayout)?.let {
            binding.browserFastScroller.attachToCoordinator(it, view.rootView.findViewById<View>(R.id.coordinator) as CoordinatorLayout, view.rootView.findViewById<View>(R.id.fab) as FloatingActionButton)
            binding.browserFastScroller.setRecyclerView(binding.networkList, viewModel.provider)
        }
    }

    override fun sortBy(sort: Int) {
        super.sortBy(sort)
        adapter.sort = sort
        adapter.changeSort(sort, !viewModel.desc)
        UiTools.updateSortTitles(this)
    }

    open fun registerSwiperRefreshlayout() = swipeRefreshLayout.setOnRefreshListener(this)

    override fun setBreadcrumb() {
        val ariane = binding.ariane
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
        if (tag == "root") {
            requireActivity().finish()
            return
        }
        val supportFragmentManager = requireActivity().supportFragmentManager
        var poped = false
        for (i in 0 until supportFragmentManager.backStackEntryCount) {
            if (tag == supportFragmentManager.getBackStackEntryAt(i).name) {
                supportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                poped = true
                break
            }
        }
        if (!poped) {
            browse(MLServiceLocator.getAbstractMediaWrapper(tag.toUri()),false)
        }
    }

    override fun currentContext() = requireContext()

    override fun showRoot() = true

    override fun getPathOperationDelegate() = viewModel

    override fun onStart() {
        super.onStart()
        fabPlay?.run {
            setImageResource(R.drawable.ic_fab_play)
            updateFab()
            fabPlay?.contentDescription = getString(R.string.play)
        }
        (activity as? AudioPlayerContainerActivity)?.expandAppBar()
    }


    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.cancel()
        PlaybackService.serviceFlow.value?.removeCallback(this)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_MRL, mrl)
        outState.putParcelable(KEY_MEDIA, currentMedia)
        super.onSaveInstanceState(outState)
    }

    override fun getTitle(): String = when {
        isRootDirectory -> categoryTitle
        currentMedia != null -> currentMedia!!.title
        else -> mrl ?: ""
    }

    @Suppress("UNCHECKED_CAST")
    override fun getMultiHelper(): MultiSelectHelper<BrowserModel>? = if (::adapter.isInitialized) adapter.multiSelectHelper as? MultiSelectHelper<BrowserModel> else null

    override val subTitle: String? =
            if (isRootDirectory) null else {
                var mrl = mrl?.removeFileScheme() ?: ""
                if (mrl.isNotEmpty()) {
                    if (this is FileBrowserFragment && mrl.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                        mrl = getString(R.string.internal_memory) + mrl.substring(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY.length)
                    mrl = Uri.decode(mrl).replace("://".toRegex(), " ").replace("/".toRegex(), " > ")
                }
                if (currentMedia != null) mrl else null
            }

    fun goBack(): Boolean {
        val activity = activity
        if (activity?.isStarted() != true) return false
        if (!isRootDirectory && !activity.isFinishing && !activity.isDestroyed) activity.supportFragmentManager.popBackStack()
        return !isRootDirectory
    }

    fun browse(media: MediaWrapper, save: Boolean) {
        val ctx = activity
        if (ctx == null || !isResumed || isRemoving) return
        val ft = ctx.supportFragmentManager.beginTransaction()
        val next = createFragment()
        viewModel.saveList(media)
        next.arguments = bundleOf(KEY_MEDIA to media)
        if (save) ft.addToBackStack(if (isRootDirectory) "root" else if (currentMedia != null) currentMedia?.uri.toString() else mrl!!)
        if (BuildConfig.DEBUG) for (i in 0 until ctx.supportFragmentManager.backStackEntryCount) {
            Log.d(this::class.java.simpleName, "Adding to back stack from PathAdapter: ${ctx.supportFragmentManager.getBackStackEntryAt(i).name}")
        }
        ft.replace(R.id.fragment_placeholder, next, media.title)
        ft.commit()
    }

    override fun onRefresh() {
        viewModel.refresh()
    }

    /**
     * Update views visibility and emptiness info
     */
    protected open fun updateEmptyView() {
        binding.emptyLoading.emptyText = viewModel.filterQuery?.let {  getString(R.string.empty_search, it) } ?: getString(R.string.nomedia)
        swipeRefreshLayout.let {
            when {
               !Permissions.canReadStorage(requireActivity()) -> binding.emptyLoading.state = EmptyLoadingState.MISSING_PERMISSION
                it.isRefreshing -> {
                    binding.emptyLoading.state = EmptyLoadingState.LOADING
                    binding.networkList.visibility = View.GONE
                }
                viewModel.isEmpty() && viewModel.filterQuery != null -> {
                    binding.emptyLoading.state = EmptyLoadingState.EMPTY_SEARCH
                    binding.networkList.visibility = View.GONE
                }
                viewModel.isEmpty() -> {
                    binding.emptyLoading.state = EmptyLoadingState.EMPTY
                    binding.networkList.visibility = View.GONE
                }
                else -> {
                    binding.emptyLoading.state = EmptyLoadingState.NONE
                    binding.networkList.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun refresh() = viewModel.refresh()

    override fun onFabPlayClick(view: View) {
        playAll(null)
    }

    override fun onTaskTriggered(id: String, data:Bundle) {
        when (id) {
            MSG_SHOW_LOADING -> swipeRefreshLayout.isRefreshing = true
            MSG_HIDE_LOADING -> {
                scheduler.cancelAction(MSG_SHOW_LOADING)
                swipeRefreshLayout.isRefreshing = false
            }

            MSG_REFRESH -> {
                scheduler.cancelAction(MSG_REFRESH)
                if (!isDetached) refresh()
            }

            MSG_SHOW_ENQUEUING -> {
                activity?.let {
                    enqueuingSnackbar = UiTools.snackerMessageInfinite(it, it.getString(R.string.enqueuing))
                }
                enqueuingSnackbar?.show()

            }

            MSG_HIDE_ENQUEUING -> {
                enqueuingSnackbar?.dismiss()
                scheduler.cancelAction(MSG_SHOW_ENQUEUING)
            }
        }
    }


    override fun clear() = adapter.clear()

    override fun removeItem(item: MediaLibraryItem): Boolean {
        val mw = item as? MediaWrapper
                ?: return false
        val deleteAction = Runnable {
            lifecycleScope.launch {
                MediaUtils.deleteItem(requireActivity(), mw) { viewModel.refresh() }
                viewModel.remove(mw)
            }
        }
        val dialog = ConfirmDeleteDialog.newInstance(arrayListOf(mw))
        dialog.show(requireActivity().supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
        dialog.setListener {
            if (Permissions.checkWritePermission(requireActivity(), mw, deleteAction)) deleteAction.run()
        }
        return true
    }

    private fun playAll(mw: MediaWrapper?) {
        lifecycleScope.launch {
            var positionInPlaylist = 0
            val mediaLocations = LinkedList<MediaWrapper>()
            scheduler.scheduleAction(MSG_SHOW_ENQUEUING, 1000L)
            withContext(Dispatchers.IO) {
                val files = if (viewModel.url?.startsWith("file") == true) viewModel.provider.browseUrl(viewModel.url!!) else viewModel.dataset.getList()
                    for (file in files.filterIsInstance(MediaWrapper::class.java))
                        if (file.type == MediaWrapper.TYPE_VIDEO || file.type == MediaWrapper.TYPE_AUDIO) {
                            mediaLocations.add(getMediaWithMeta(file))
                            if (mw != null && file.equals(mw))
                                positionInPlaylist = mediaLocations.size - 1
                        }
            }
            scheduler.startAction(MSG_HIDE_ENQUEUING)
            activity?.let { MediaUtils.openList(it, mediaLocations, positionInPlaylist) }
        }
    }

    override fun enableSearchOption() = !isRootDirectory

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        getMultiHelper()?.toggleActionMode(true, adapter.itemCount)
        mode.menuInflater.inflate(R.menu.action_mode_browser_file, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = adapter.multiSelectHelper.getSelectionCount()
        if (count == 0) {
            stopActionMode()
            return false
        }
        mode.title = requireActivity().getString(R.string.selection_count, count)
        val fileBrowser = this is FileBrowserFragment
        val single = fileBrowser && count == 1
        val selection = if (single) adapter.multiSelectHelper.getSelection() else null
        val type = if (!selection.isNullOrEmpty()) (selection[0] as MediaWrapper).type else -1
        menu.findItem(R.id.action_mode_file_info).isVisible = single && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO)
        menu.findItem(R.id.action_mode_file_append).isVisible = PlaylistManager.hasMedia()
        menu.findItem(R.id.action_mode_file_delete).isVisible = fileBrowser
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isStarted()) return false
        @Suppress("UNCHECKED_CAST") val list = adapter.multiSelectHelper.getSelection() as? List<MediaWrapper>
                ?: return false
        if (list.isNotEmpty()) {
            when (item.itemId) {
                R.id.action_mode_file_play -> lifecycleScope.launch { MediaUtils.openList(activity, list.map { getMediaWithMeta(it) }, 0) }
                R.id.action_mode_file_append -> lifecycleScope.launch { MediaUtils.appendMedia(activity, list.map { getMediaWithMeta(it) }) }
                R.id.action_mode_file_add_playlist -> requireActivity().addToPlaylist(list)
                R.id.action_mode_file_info -> requireActivity().showMediaInfo(list[0])
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
        getMultiHelper()?.toggleActionMode(false, adapter.itemCount)
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
            R.id.browser_show_all_files -> {
                item.isChecked = !Settings.getInstance(requireActivity()).getBoolean("browser_show_all_files", true)
                Settings.getInstance(requireActivity()).putSingle("browser_show_all_files", item.isChecked)
                viewModel.updateShowAllFiles(item.isChecked)
                true
            }
            R.id.browser_show_hidden_files -> {
                item.isChecked = !Settings.getInstance(requireActivity()).getBoolean(BROWSER_SHOW_HIDDEN_FILES, true)
                Settings.getInstance(requireActivity()).putSingle(BROWSER_SHOW_HIDDEN_FILES, item.isChecked)
                Settings.showHiddenFiles = item.isChecked
                viewModel.refresh()
                true
            }
            R.id.ml_menu_scan -> {
                currentMedia?.let { media ->
                    addToScannedFolders(media)
                    item.isVisible = false
                }
                true
            }
            R.id.folder_add_playlist -> {
                currentMedia?.let { requireActivity().addToPlaylistAsync(it.uri.toString()) }
                true
            }
            R.id.subfolders_add_playlist -> {
                currentMedia?.let { requireActivity().addToPlaylistAsync(it.uri.toString(), true) }
                true
            }
            R.id.play_all -> {
                onFabPlayClick(binding.root)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addToScannedFolders(mw: MediaWrapper) {
        MedialibraryUtils.addDir(mw.uri.toString(), requireActivity().applicationContext)
        Snackbar.make(binding.root, getString(R.string.scanned_directory_added, mw.uri.toString().toUri().lastPathSegment), Snackbar.LENGTH_LONG).show()
    }

    private fun toggleFavorite() = lifecycleScope.launch {
        val mw = currentMedia ?: return@launch
        when {
            browserFavRepository.browserFavExists(mw.uri) -> browserFavRepository.deleteBrowserFav(mw.uri)
            mw.uri.scheme == "file" -> browserFavRepository.addLocalFavItem(mw.uri, mw.title, mw.artworkURL)
            else -> browserFavRepository.addNetworkFavItem(mw.uri, mw.title, mw.artworkURL)
        }
        activity?.invalidateOptionsMenu()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mediaWrapper = item as MediaWrapper
        if (actionMode != null) {
            if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                    mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                    mediaWrapper.type == MediaWrapper.TYPE_DIR) {
                adapter.multiSelectHelper.toggleSelection(position)
                invalidateActionMode()
            }
        } else {
            mediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            if (mediaWrapper.type == MediaWrapper.TYPE_DIR) browse(mediaWrapper, true)
            else {
                val forcePlayType = if (mediaWrapper.type == MediaWrapper.TYPE_VIDEO) FORCE_PLAY_ALL_VIDEO else FORCE_PLAY_ALL_AUDIO
                if (!Settings.getInstance(requireContext()).getBoolean(forcePlayType, forcePlayType == FORCE_PLAY_ALL_VIDEO && Settings.tvUI)) {
                    lifecycleScope.launch {
                        MediaUtils.openMedia(requireContext(), getMediaWithMeta(item))
                    }
                } else {
                    lifecycleScope.launch {
                            val media = viewModel.dataset.getList().filter { it.itemType != MediaWrapper.TYPE_DIR }
                                    .map { getMediaWithMeta(it as MediaWrapper) }
                            MediaUtils.openList(v.context, media, position)
                        }
                }
            }
        }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        if (item.itemType != MediaLibraryItem.TYPE_MEDIA) return false
        val mediaWrapper = item as MediaWrapper
        if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                mediaWrapper.type == MediaWrapper.TYPE_DIR) {
            adapter.multiSelectHelper.toggleSelection(position)
            if (actionMode == null) startActionMode() else invalidateActionMode()
        } else onCtxClick(v, position, item)
        return true
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode == null && item.itemType == MediaLibraryItem.TYPE_MEDIA) lifecycleScope.launch {
            val mw = item as MediaWrapper
            if (mw.uri.scheme == "content" || mw.uri.scheme == OTG_SCHEME) return@launch
            var flags = if (!isRootDirectory && this@BaseBrowserFragment is FileBrowserFragment) CTX_DELETE else 0
            if (!isRootDirectory && this is FileBrowserFragment) flags = flags or CTX_DELETE
            if (mw.type == MediaWrapper.TYPE_DIR) {
                val isEmpty = viewModel.isFolderEmpty(mw)
                if (!isEmpty) flags = flags or CTX_PLAY
                val isFileBrowser = this@BaseBrowserFragment is FileBrowserFragment && item.uri.scheme == "file"
                val isNetworkBrowser = this@BaseBrowserFragment is NetworkBrowserFragment
                if (isFileBrowser || isNetworkBrowser) {
                    val favExists = browserFavRepository.browserFavExists(mw.uri)
                    flags = if (favExists) flags or CTX_FAV_REMOVE else flags or CTX_FAV_ADD
                }
                if (isFileBrowser && !isRootDirectory && !MedialibraryUtils.isScanned(item.uri.toString())) {
                    flags = flags or CTX_ADD_SCANNED
                }
                if (isFileBrowser) {
                    if (viewModel.provider.hasMedias(mw)) flags = flags or CTX_ADD_FOLDER_PLAYLIST
                    if (viewModel.provider.hasSubfolders(mw)) flags = flags or CTX_ADD_FOLDER_AND_SUB_PLAYLIST
                    flags = flags or CTX_APPEND
                }
            } else {
                val isVideo = mw.type == MediaWrapper.TYPE_VIDEO
                val isAudio = mw.type == MediaWrapper.TYPE_AUDIO
                val isMedia = isVideo || isAudio
                if (isMedia) flags = flags or CTX_PLAY_ALL or CTX_APPEND or CTX_INFORMATION or CTX_ADD_TO_PLAYLIST
                if (!isAudio && isMedia) flags = flags or CTX_PLAY_AS_AUDIO
                if (!isMedia) flags = flags or CTX_PLAY
                if (isVideo) flags = flags or CTX_DOWNLOAD_SUBTITLES
            }
            if (flags != 0L) showContext(requireActivity(), this@BaseBrowserFragment, position, item, flags)
        }
    }

    /**
     * Get the media metadata from ML if needed
     * This is useful in case a playback has already been running since this fragment has been started
     * As the ML events are not listened to refresh the browser content, it will reload the ML metadata
     * for this media to ensure the progress (and other metadata) are up to date
     *
     * @param mw the [MediaWrapper] to look into
     * @return a [MediaWrapper] with up to date ML metadata
     */
    private suspend fun getMediaWithMeta(mw:MediaWrapper):MediaWrapper {
        return if (!needToRefreshMeta) mw else requireActivity().getFromMl {
            getMedia(mw.uri) ?: mw
        }
    }

    override fun onCtxAction(position: Int, option: Long) {
        val mw = adapter.getItem(position) as? MediaWrapper
                ?: return
        when (option) {
            CTX_PLAY -> lifecycleScope.launch { MediaUtils.openMedia(activity, getMediaWithMeta(mw)) }
            CTX_PLAY_ALL -> {
                mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                playAll(mw)
            }
            CTX_APPEND -> lifecycleScope.launch {
                    MediaUtils.appendMedia(activity, getMediaWithMeta(mw))
            }
            CTX_DELETE -> removeItem(mw)
            CTX_INFORMATION -> requireActivity().showMediaInfo(mw)
            CTX_PLAY_AS_AUDIO -> lifecycleScope.launch {
                mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                MediaUtils.openMedia(activity, getMediaWithMeta(mw))
            }
            CTX_ADD_TO_PLAYLIST -> requireActivity().addToPlaylist(mw.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs(requireActivity(), mw)
            CTX_FAV_REMOVE -> lifecycleScope.launch(Dispatchers.IO) { browserFavRepository.deleteBrowserFav(mw.uri) }
            CTX_ADD_SCANNED -> addToScannedFolders(mw)
            CTX_FIND_METADATA -> {
                val intent = Intent().apply {
                    setClassName(requireContext().applicationContext, MOVIEPEDIA_ACTIVITY)
                    putExtra(MOVIEPEDIA_MEDIA, mw)
                }
                startActivity(intent)
            }
            CTX_ADD_FOLDER_PLAYLIST -> {
                requireActivity().addToPlaylistAsync(mw.uri.toString(), false)
            }
            CTX_ADD_FOLDER_AND_SUB_PLAYLIST -> {
                requireActivity().addToPlaylistAsync(mw.uri.toString(), true)
            }
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
        if (!isStarted()) return
        restoreMultiSelectHelper()
        swipeRefreshLayout.isRefreshing = false
        scheduler.startAction(MSG_HIDE_LOADING)
        updateEmptyView()
        if (!isRootDirectory) {
            updateFab()
        }
    }

    override fun onItemFocused(v: View, item: MediaLibraryItem) {
    }

    private fun updateFab() {
        fabPlay?.let {
            if (this !is StorageBrowserFragment && (adapter.mediaCount > 0 || viewModel.url?.startsWith("file") == true)) {
                setFabPlayVisibility(true)
                it.setOnClickListener{onFabPlayClick(it)}
            } else {
                setFabPlayVisibility(false)
                it.setOnClickListener(null)
            }
        }
    }


    override fun setSearchVisibility(visible: Boolean) {
        // prevents the medialibrary search to be displayed in a browser context
    }

    override fun update() {}

    override fun onMediaEvent(event: IMedia.Event) {}

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        needToRefreshMeta = true
    }

}
