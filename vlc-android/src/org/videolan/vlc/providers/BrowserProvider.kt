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
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.collection.SimpleArrayMap
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.libvlc.util.MediaBrowser.EventListener
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.R
import org.videolan.vlc.util.*
import java.util.*

const val TAG = "VLC/BrowserProvider"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
abstract class BrowserProvider(val context: Context, val dataset: LiveDataset<MediaLibraryItem>, val url: String?, private val showHiddenFiles: Boolean) : CoroutineScope, HeaderProvider() {

    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()
    val loading = MutableLiveData<Boolean>().apply { value = false }

    protected var mediabrowser: MediaBrowser? = null
    private var parsingJob : Job? = null
    private var discoveryJob : Job? = null

    private val foldersContentMap = SimpleArrayMap<MediaLibraryItem, MutableList<MediaLibraryItem>>()
    private val showAll = Settings.getInstance(context).getBoolean("browser_show_all_files", true)

    val descriptionUpdate = MutableLiveData<Pair<Int, String>>()
    internal val medialibrary = AbstractMedialibrary.getInstance()

    private val completionHandler : CompletionHandler = object : CompletionHandler {
        override fun invoke(cause: Throwable?) {
            if (mediabrowser != null) AppScope.launch(Dispatchers.IO) { // use global scope because current is cancelled
                mediabrowser?.release()
                mediabrowser = null
            }
        }
    }

    private val browserActor = actor<BrowserAction>(capacity = Channel.UNLIMITED, onCompletion = completionHandler) {
        for (action in channel) if (isActive) {
            when (action) {
                is Browse -> browseImpl(action.url)
                BrowseRoot -> browseRootImpl()
                Refresh -> refreshImpl()
                is ParseSubDirectories -> parseSubDirectoriesImpl(action.list)
                ClearListener -> withContext(Dispatchers.IO) { mediabrowser?.changeEventListener(null) }
                Release -> withContext(Dispatchers.IO) {
                    mediabrowser?.release()
                    mediabrowser = null
                }
            }
        }
    }

    protected open fun initBrowser() {
        if (mediabrowser == null) mediabrowser = MediaBrowser(VLCInstance[context], null, browserHandler)
    }

    protected abstract suspend fun requestBrowsing(url: String?, eventListener: EventListener, interact : Boolean) : Unit?

    open fun fetch() {
        val list by lazy(LazyThreadSafetyMode.NONE) { prefetchLists[url] }
        when {
            url === null -> {
                browseRoot()
                parseSubDirectories()
            }
            !list.isNullOrEmpty() -> {
                dataset.value = list ?: return
                prefetchLists.remove(url)
                computeHeaders(list!!)
                parseSubDirectories()
            }
            else -> browse(url)
        }
    }

    protected open fun browse(url: String? = null) {
        if (url != null ) loading.postValue(true)
        browserActor.post(Browse(url))
    }

    protected open suspend fun browseImpl(url: String? = null) {
        if (url == null) {
            coroutineScope {
                discoveryJob = launch { filesFlow().collect { findMedia(it)?.let { item -> addMedia(item) } } }
            }
        } else {
            val files = filesFlow().mapNotNull { findMedia(it) }.onEach { addMedia(it) }.toList()
            computeHeaders(files)
            parseSubDirectories(files)
            loading.postValue(false)
        }
    }

    protected open suspend fun refreshImpl() {
        val files = filesFlow(url, true).mapNotNull { findMedia(it) }.toList()
        dataset.value = files as MutableList<MediaLibraryItem>
        computeHeaders(files)
        parseSubDirectories(files)
        loading.postValue(false)
    }

    private fun filesFlow(url: String? = this.url, interact : Boolean = true) = channelFlow {
        val listener = object : EventListener {
            override fun onMediaAdded(index: Int, media: Media) {
                if (!isClosedForSend) offer(media.apply { retain() })
            }

            override fun onBrowseEnd() {
                if (!isClosedForSend) close()
            }

            override fun onMediaRemoved(index: Int, media: Media) {}
        }
        requestBrowsing(url, listener, interact)
        awaitClose { if (url != null) browserActor.post(ClearListener) }
    }.buffer(Channel.UNLIMITED)

    protected open fun addMedia(media: MediaLibraryItem) = dataset.add(media)

    open fun refresh() {
        if (url === null) return
        parsingJob?.cancel()
        parsingJob = null
        loading.postValue(true)
        browserActor.post(Refresh)
    }

    open fun computeHeaders(value: List<MediaLibraryItem>) {
        privateHeaders.clear()
        for ((position, item) in value.withIndex()) {
            val previous = when {
                position > 0 -> value[position - 1]
                else -> null
            }
            ModelsHelper.getHeader(context, AbstractMedialibrary.SORT_ALPHA, item, previous)?.let {
                privateHeaders.put(position, it)
            }
        }
        (liveHeaders as MutableLiveData).postValue(privateHeaders.clone())
    }

    internal open fun parseSubDirectories(list : List<MediaLibraryItem>? = null) {
        browserActor.post(ParseSubDirectories(list))
    }

    private suspend fun parseSubDirectoriesImpl(list : List<MediaLibraryItem>? = null) {
        if (list === null && dataset.value.isEmpty()) return
        val currentMediaList = list ?: withContext(Dispatchers.Main) { dataset.value.toList() }
        val directories: MutableList<AbstractMediaWrapper> = ArrayList()
        val files: MutableList<AbstractMediaWrapper> = ArrayList()
        foldersContentMap.clear()
        coroutineScope { // allow child coroutine to be cancelled without closing the actor.
            parsingJob = launch (Dispatchers.IO) {
                initBrowser()
                var currentParsedPosition = -1
                loop@ while (++currentParsedPosition < currentMediaList.size) {
                    if (!isActive) break@loop
                    //skip media that are not browsable
                    val item = currentMediaList[currentParsedPosition]
                    val current = when {
                        item.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                            val mw = item as AbstractMediaWrapper
                            if (mw.type != AbstractMediaWrapper.TYPE_DIR && mw.type != AbstractMediaWrapper.TYPE_PLAYLIST) continue@loop
                            if (mw.uri.scheme == "otg" || mw.uri.scheme == "content") continue@loop
                            mw
                        }
                        item.itemType == MediaLibraryItem.TYPE_STORAGE ->
                            MLServiceLocator.getAbstractMediaWrapper((item as Storage).uri).apply { type = AbstractMediaWrapper.TYPE_DIR }
                        else -> continue@loop
                    }
                    // retrieve subitems
                    val mediaList = filesFlow(current.uri.toString(), false).toList()
                    for (media in mediaList) {
                        val mw = findMedia(media) ?: continue
                        if (mw is AbstractMediaWrapper) {
                            val type = mw.type
                            if (type == AbstractMediaWrapper.TYPE_DIR) directories.add(mw)
                            else files.add(mw)
                        } else if (mw is Storage) directories.add(MLServiceLocator.getAbstractMediaWrapper(media))
                    }
                    // all subitems are in
                    getDescription(directories.size, files.size).takeIf { it.isNotEmpty() }?.let {
                        val position = currentParsedPosition
                        withContext(Dispatchers.Main) {
                            item.description = it
                            descriptionUpdate.value = Pair(position, it)
                        }
                        directories.addAll(files)
                        withContext(Dispatchers.Main) { foldersContentMap.put(item, directories.toMutableList()) }
                    }
                    directories.clear()
                    files.clear()
                }
            }
        }
        parsingJob = null
    }

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

    protected open suspend fun findMedia(media: Media): MediaLibraryItem? {
        val mw = MLServiceLocator.getAbstractMediaWrapper(media)
        media.release()
        if (!mw.isMedia()) {
            if (mw.isBrowserMedia()) return mw
            else if (!showAll) return null
        }
        val uri = mw.uri
        if ((mw.type == AbstractMediaWrapper.TYPE_AUDIO || mw.type == AbstractMediaWrapper.TYPE_VIDEO)
                && "file" == uri.scheme) return withContext(Dispatchers.IO) {
            medialibrary.getMedia(uri) ?: mw
        }
        return mw
    }

    fun browseRoot() = browserActor.post(BrowseRoot)

    abstract suspend fun browseRootImpl()

    open fun getFlags(interact : Boolean) : Int {
        var flags = if (interact) MediaBrowser.Flag.Interact else 0
        if (showHiddenFiles) flags = flags or MediaBrowser.Flag.ShowHiddenFiles
        return flags
    }

    open fun stop() {
        browserActor.offer(Release)
        discoveryJob?.cancel()
        discoveryJob = null
        parsingJob?.cancel()
        parsingJob = null
    }

    protected fun clearListener() = browserActor.post(ClearListener)

    open fun release() {
        cancel()
        if (url != null) loading.postValue(false)
    }

    protected fun getList(url: String) =  prefetchLists[url]

    protected fun removeList(url: String) =  prefetchLists.remove(url)

    fun saveList(media: AbstractMediaWrapper) = foldersContentMap[media]?.let { if (!it.isEmpty()) prefetchLists[media.location] = it }

    fun isFolderEmpty(mw: AbstractMediaWrapper) = foldersContentMap[mw]?.isEmpty() ?: true

    companion object {
        private val browserHandler by lazy {
            val handlerThread = HandlerThread("vlc-provider", Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE)
            handlerThread.start()
            Handler(handlerThread.looper)
        }
        private val prefetchLists = mutableMapOf<String, MutableList<MediaLibraryItem>>()
    }

    private fun <E> SendChannel<E>.post(element: E) = isActive && !isClosedForSend && offer(element)
}

private sealed class BrowserAction
private class Browse(val url: String?) : BrowserAction()
private object BrowseRoot : BrowserAction()
private object Refresh : BrowserAction()
private class ParseSubDirectories(val list : List<MediaLibraryItem>? = null) : BrowserAction()
private object ClearListener : BrowserAction()
private object Release : BrowserAction()
