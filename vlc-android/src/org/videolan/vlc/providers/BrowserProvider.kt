/*****************************************************************************
 * BrowserProvider.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.providers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.mapNotNullTo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.libvlc.util.MediaBrowser.EventListener
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.R
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.isBrowserMedia
import java.util.*

const val TAG = "VLC/BrowserProvider"

abstract class BrowserProvider(val context: Context, val dataset: LiveDataset<MediaLibraryItem>, val url: String?, private val showHiddenFiles: Boolean) : EventListener, CoroutineScope {

    override val coroutineContext = Dispatchers.Main.immediate

    private val mutex= Mutex()
    protected var mediabrowser: MediaBrowser? = null

    private val foldersContentMap = androidx.collection.SimpleArrayMap<MediaLibraryItem, MutableList<MediaLibraryItem>>()
    private lateinit var browserChannel : Channel<Media>
    protected var job : Job? = null
    private val showAll = Settings.getInstance(context).getBoolean("browser_show_all_files", true)

    val descriptionUpdate = MutableLiveData<Pair<Int, String>>()
    internal val medialibrary = Medialibrary.getInstance()

    private val browserActor = actor<BrowserAction>(Dispatchers.IO, Channel.UNLIMITED) {
        for (action in channel) if (isActive) when (action) {
            is Browse -> browseImpl(action.url)
            is Refresh -> refreshImpl()
            is ParseSubDirectories -> parseSubDirectoriesImpl()
        } else channel.close()
    }

    init {
        fetch()
    }

    protected open fun initBrowser() {
        if (mediabrowser == null) mediabrowser = MediaBrowser(VLCInstance.get(), this, browserHandler)
    }

    open fun fetch() {
        val list by lazy(LazyThreadSafetyMode.NONE) { prefetchLists[url] }
        when {
            url === null -> launch(Dispatchers.Main) {
                browseRoot()
                parseSubDirectories()
            }
            list?.isEmpty() == false -> launch(Dispatchers.Main) {
                dataset.value = list ?: return@launch
                prefetchLists.remove(url)
                parseSubDirectories()
            }
            else -> browse(url)
        }
    }

    protected open fun browse(url: String? = null) {
        if (!browserActor.isClosedForSend) browserActor.offer(Browse(url))
    }

    private fun browseImpl(url: String? = null) {
        browserChannel = Channel(Channel.UNLIMITED)
        requestBrowsing(url)
        job = launch {
            for (media in browserChannel) findMedia(media)?.let { addMedia(it) }
            if (dataset.value.isNotEmpty()) parseSubDirectories()
            else dataset.clear() // send observable event when folder is empty
        }
    }

    protected open fun addMedia(media: MediaLibraryItem) = dataset.add(media)

    open fun refresh() : Boolean {
        if (url === null || browserActor.isClosedForSend) return false
        browserActor.offer(Refresh)
        return true
    }

    internal open fun parseSubDirectories() {
        if (!browserActor.isClosedForSend) browserActor.offer(ParseSubDirectories)
    }

    open fun refreshImpl() {
        browserChannel = Channel(Channel.UNLIMITED)
        requestBrowsing(url)
        job = launch {
            dataset.value = browserChannel.mapNotNullTo(mutableListOf()) { findMedia(it) }
            parseSubDirectories()
        }
    }

    private suspend fun parseSubDirectoriesImpl() {
        if (dataset.value.isEmpty()) return
        val currentMediaList = withContext(Dispatchers.Main) { dataset.value.toList() }
        val directories: MutableList<MediaWrapper> = ArrayList()
        val files: MutableList<MediaWrapper> = ArrayList()
        foldersContentMap.clear()
        mutex.withLock { initBrowser() }
        var currentParsedPosition = -1
        loop@ while (++currentParsedPosition < currentMediaList.size) {
            if (!isActive) {
                browserChannel.close()
                return
            }
            //skip media that are not browsable
            val item = currentMediaList[currentParsedPosition]
            val current = when {
                item.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                    val mw = item as MediaWrapper
                    if (mw.type != MediaWrapper.TYPE_DIR && mw.type != MediaWrapper.TYPE_PLAYLIST) continue@loop
                    if (mw.uri.scheme == "otg" || mw.uri.scheme == "content") continue@loop
                    mw
                }
                item.itemType == MediaLibraryItem.TYPE_STORAGE -> MediaWrapper((item as Storage).uri).apply { type = MediaWrapper.TYPE_DIR }
                else -> continue@loop
            }
            // request parsing
            browserChannel = Channel(Channel.UNLIMITED)
            mutex.withLock { mediabrowser?.browse(current.uri, 0) }
            // retrieve subitems
            for (media in browserChannel) {
                val mw = findMedia(media) ?: continue
                val type = mw.type
                if (type == MediaWrapper.TYPE_DIR) directories.add(mw)
                else files.add(mw)
            }
            // all subitems are in
            getDescription(directories.size, files.size).takeIf { it.isNotEmpty() }?.let {
                val position = currentParsedPosition
                launch {
                    item.description = it
                    descriptionUpdate.value = Pair(position, it)
                }
                directories.addAll(files)
                foldersContentMap.put(item, directories.toMutableList())
            }
            directories.clear()
            files.clear()
        }
    }

    override fun onMediaAdded(index: Int, media: Media) {
        if (!browserChannel.isClosedForSend) {
            media.retain()
            browserChannel.offer(media)
        }
    }
    override fun onBrowseEnd() { if (!browserChannel.isClosedForSend) browserChannel.close() }
    override fun onMediaRemoved(index: Int, media: Media){}

    private val sb = StringBuilder()
    private fun getDescription(folderCount: Int, mediaFileCount: Int): String {
        val res = context.resources
        sb.setLength(0)
        if (folderCount > 0) {
            sb.append(res.getQuantityString(R.plurals.subfolders_quantity, folderCount, folderCount))
            if (mediaFileCount > 0) sb.append(", ")
        }
        if (mediaFileCount > 0) sb.append(res.getQuantityString(R.plurals.mediafiles_quantity, mediaFileCount, mediaFileCount))
        else if (folderCount == 0 && mediaFileCount == 0) sb.append(res.getString(R.string.directory_empty))
        return sb.toString()
    }

    private suspend fun findMedia(media: Media): MediaWrapper? {
        val mw = MediaWrapper(media)
        media.release()
        if (!showAll && !mw.isBrowserMedia()) return null
        val uri = mw.uri
        if ((mw.type == MediaWrapper.TYPE_AUDIO || mw.type == MediaWrapper.TYPE_VIDEO)
                && "file" == uri.scheme) return withContext(Dispatchers.IO) { medialibrary.getMedia(uri) ?: mw }
        return mw
    }

    abstract suspend fun browseRoot()

    open fun getFlags() : Int {
        var flags = MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect
        if (showHiddenFiles) flags = flags or MediaBrowser.Flag.ShowHiddenFiles
        return flags
    }

    private fun requestBrowsing(url: String?) = launch(Dispatchers.IO) {
        mutex.withLock {
            initBrowser()
            mediabrowser?.let {
                if (url != null) it.browse(Uri.parse(url), getFlags())
                else {
                    it.changeEventListener(this@BrowserProvider)
                    it.discoverNetworkShares()
                }
            }
        }
    }

    open fun stop() = job?.cancel()

    open fun release() {
        browserActor.close()
        launch(Dispatchers.IO) {
            if (this@BrowserProvider::browserChannel.isInitialized) browserChannel.close()
            job?.cancelAndJoin()
            mutex.withLock {
                mediabrowser?.let {
                    it.release()
                    mediabrowser = null
                }
            }
        }
    }

    fun saveList(media: MediaWrapper) = foldersContentMap[media]?.let { if (!it.isEmpty()) prefetchLists[media.location] = it }

    fun isFolderEmpty(mw: MediaWrapper) = foldersContentMap[mw]?.isEmpty() ?: true

    companion object {
        private val browserHandler by lazy {
            val handlerThread = HandlerThread("vlc-mProvider", Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE)
            handlerThread.start()
            Handler(handlerThread.looper)
        }
        private val prefetchLists = mutableMapOf<String, MutableList<MediaLibraryItem>>()
    }
}

private sealed class BrowserAction
private class Browse(val url: String?) : BrowserAction()
private object Refresh : BrowserAction()
private object ParseSubDirectories : BrowserAction()