package org.videolan.vlc.viewmodels.browser

import android.arch.lifecycle.MutableLiveData
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.support.annotation.MainThread
import android.support.v4.util.SimpleArrayMap
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.HandlerContext
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.libvlc.util.MediaBrowser.EventListener
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.VLCIO
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.viewmodels.BaseModel
import java.util.*

const val TAG = "VLC/BrowserProvider"

abstract class BrowserProvider(val url: String?, private val showHiddenFiles: Boolean) : BaseModel<MediaLibraryItem>(), EventListener {

    protected lateinit var mediabrowser: MediaBrowser

    private val foldersContentMap = SimpleArrayMap<MediaLibraryItem, MutableList<MediaLibraryItem>>()
    private lateinit var browserChannel : Channel<Media>
    private var job : Job? = null

    val descriptionUpdate = MutableLiveData<Pair<Int, String>>()
    internal val medialibrary = Medialibrary.getInstance()

    protected open fun initBrowser() {
        if (!this::mediabrowser.isInitialized) mediabrowser = MediaBrowser(VLCInstance.get(), this, browserHandler)
    }

    override fun fetch() {
        val prefetchList by lazy(LazyThreadSafetyMode.NONE) { prefetchLists[url] }
        when {
            url === null -> launch(UI) {
                browseRoot()
                parseSubDirectories()
            }
            prefetchList?.isEmpty() == false -> launch(UI) {
                dataset.value = prefetchList
                prefetchLists.remove(url)
                parseSubDirectories()
            }
            else -> browse(url)
        }
    }

    protected fun browse(url: String? = null) {
        browserChannel = Channel(Channel.UNLIMITED)
        requestBrowsing(url)
        job = launch(UI, CoroutineStart.UNDISPATCHED) {
            for (media in browserChannel) addMedia(findMedia(media))
            parseSubDirectories()
        }
    }

    override fun refresh(): Boolean {
        if (url === null) return false
        browserChannel = Channel(Channel.UNLIMITED)
        val refreshList = mutableListOf<MediaLibraryItem>()
        requestBrowsing(url)
        job = launch(UI, CoroutineStart.UNDISPATCHED) {
            for (media in browserChannel) refreshList.add(findMedia(media))
            dataset.value = refreshList
            parseSubDirectories()
        }
        return true
    }

    @MainThread
    override fun sort(sort: Int) {
        launch(UI, CoroutineStart.UNDISPATCHED) {
            this@BrowserProvider.sort = sort
            desc = !desc
            dataset.value = withContext(CommonPool) { dataset.value.apply { sortWith(if (desc) descComp else ascComp) } }
        }
    }

    private suspend fun parseSubDirectories() {
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
                        mw
                    }
                    item.itemType == MediaLibraryItem.TYPE_STORAGE -> MediaWrapper((item as Storage).uri).apply { type = MediaWrapper.TYPE_DIR }
                    else -> continue@loop
                }
                // request parsing
                browserChannel = Channel(Channel.UNLIMITED)
                mediabrowser.browse(current.uri, 0)
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
        val res = VLCApplication.getAppResources()
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
        if (url != null) mediabrowser.browse(Uri.parse(url), getFlags())
        else mediabrowser.discoverNetworkShares()
    }

    fun stop() = job?.cancel()

    override fun onCleared() {
        launch(browserContext) { mediabrowser.release() }
    }

    fun saveList(media: MediaWrapper) {
        foldersContentMap[media]?.let { if (!it.isEmpty()) prefetchLists[media.location] = it }
    }

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

private val ascComp by lazy {
    Comparator<MediaLibraryItem> { item1, item2 ->
        if (item1?.itemType == MediaLibraryItem.TYPE_MEDIA) {
            val type1 = (item1 as MediaWrapper).type
            val type2 = (item2 as MediaWrapper).type
            if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR) return@Comparator -1
            else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR) return@Comparator 1
        }
        item1?.title?.toLowerCase()?.compareTo(item2?.title?.toLowerCase() ?: "") ?: -1
    }
}
private val descComp by lazy {
    Comparator<MediaLibraryItem> { item1, item2 ->
        if (item1?.itemType == MediaLibraryItem.TYPE_MEDIA) {
            val type1 = (item1 as MediaWrapper).type
            val type2 = (item2 as MediaWrapper).type
            if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR) return@Comparator -1
            else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR) return@Comparator 1
        }
        item2?.title?.toLowerCase()?.compareTo(item1?.title?.toLowerCase() ?: "") ?: -1
    }
}