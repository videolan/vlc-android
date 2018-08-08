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

import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.support.v4.util.SimpleArrayMap
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.HandlerContext
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.mapTo
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.libvlc.util.MediaBrowser.EventListener
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.R
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.VLCIO
import org.videolan.vlc.util.VLCInstance
import java.util.*

const val TAG = "VLC/BrowserProvider"

abstract class BrowserProvider(val context: Context, val dataset: LiveDataset<MediaLibraryItem>, val url: String?, private val showHiddenFiles: Boolean) : EventListener {

    init {
        fetch()
    }

    protected var mediabrowser: MediaBrowser? = null

    private val foldersContentMap = SimpleArrayMap<MediaLibraryItem, MutableList<MediaLibraryItem>>()
    private lateinit var browserChannel : Channel<Media>
    protected var job : Job? = null

    val descriptionUpdate = MutableLiveData<Pair<Int, String>>()
    internal val medialibrary = Medialibrary.getInstance()

    protected open fun initBrowser() {
        if (mediabrowser == null) mediabrowser = MediaBrowser(VLCInstance.get(), this, browserHandler)
    }

    open fun fetch() {
        val prefetchList by lazy(LazyThreadSafetyMode.NONE) { BrowserProvider.prefetchLists[url] }
        when {
            url === null -> launch(UI) {
                browseRoot()
                parseSubDirectories()
            }
            prefetchList?.isEmpty() == false -> launch(UI) {
                dataset.value = prefetchList
                BrowserProvider.prefetchLists.remove(url)
                parseSubDirectories()
            }
            else -> browse(url)
        }
    }

    protected open fun browse(url: String? = null) {
        browserChannel = Channel(Channel.UNLIMITED)
        requestBrowsing(url)
        job = launch(UI.immediate) {
            for (media in browserChannel) addMedia(findMedia(media))
            parseSubDirectories()
        }
    }

    protected open fun addMedia(media: MediaLibraryItem) = dataset.add(media)

    open fun refresh(): Boolean {
        if (url === null) return false
        browserChannel = Channel(Channel.UNLIMITED)
        requestBrowsing(url)
        job = launch(UI.immediate) {
            dataset.value = browserChannel.mapTo(mutableListOf()) { findMedia(it) }
            parseSubDirectories()
        }
        return true
    }

    internal open suspend fun parseSubDirectories() {
        if (dataset.value.isEmpty()) return
        val currentMediaList = dataset.value.toList()
        launch(browserContext, parent = job) {
            val directories: MutableList<MediaWrapper> = ArrayList()
            val files: MutableList<MediaWrapper> = ArrayList()
            foldersContentMap.clear()
            initBrowser()
            var currentParsedPosition = -1
            loop@ while (++currentParsedPosition < currentMediaList.size) {
                if (!isActive) {
                    browserChannel.close()
                    return@launch
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
                mediabrowser?.browse(current.uri, 0)
                // retrieve subitems
                for (media in browserChannel) {
                    val type = media.type
                    val mw = findMedia(media)
                    if (type == Media.Type.Directory) directories.add(mw)
                    else if (type == Media.Type.File) files.add(mw)
                }
                // all subitems are in
                val holderText = getDescription(directories.size, files.size)
                if (holderText != "") {
                    val position = currentParsedPosition
                    launch(UI) {
                        item.description = holderText
                        descriptionUpdate.value = Pair(position, holderText)
                    }
                    directories.addAll(files)
                    foldersContentMap.put(item, directories.toMutableList())
                }
                directories.clear()
                files.clear()
            }
        }
    }

    override fun onMediaAdded(index: Int, media: Media) { browserChannel.offer(media) }
    override fun onBrowseEnd() { browserChannel.close() }
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

    private suspend fun findMedia(media: Media): MediaWrapper {
        val mw = MediaWrapper(media)
        val uri = mw.uri
        if ((mw.type == MediaWrapper.TYPE_AUDIO || mw.type == MediaWrapper.TYPE_VIDEO)
                && "file" == uri.scheme) return withContext(VLCIO) { medialibrary.getMedia(uri) ?: mw }
        return mw
    }

    abstract fun browseRoot()

    open fun getFlags() : Int {
        var flags = MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect
        if (showHiddenFiles) flags = flags or MediaBrowser.Flag.ShowHiddenFiles
        return flags
    }

    private fun requestBrowsing(url: String?) = launch(browserContext) {
        initBrowser()
        mediabrowser?.let {
            if (url != null) it.browse(Uri.parse(url), getFlags())
            else it.discoverNetworkShares()
        }
    }

    fun stop() = job?.cancel()

    open fun release() = launch(BrowserProvider.browserContext) {
        if (this@BrowserProvider::browserChannel.isInitialized) browserChannel.close()
        mediabrowser?.let {
            it.release()
            mediabrowser = null
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
        private val browserContext by lazy { HandlerContext(browserHandler, "provider-context") }
    }
}