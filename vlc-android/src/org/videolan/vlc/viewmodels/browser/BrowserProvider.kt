package org.videolan.vlc.viewmodels.browser

import android.arch.lifecycle.MutableLiveData
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.support.annotation.MainThread
import android.support.v4.util.SimpleArrayMap
import android.text.TextUtils
import kotlinx.coroutines.experimental.android.HandlerContext
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.libvlc.util.MediaBrowser.EventListener
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Storage
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.viewmodels.BaseModel
import java.util.*

abstract class BrowserProvider(val url: String?) : BaseModel<MediaLibraryItem>(), EventListener {

    protected var mediabrowser: MediaBrowser? = null
    private val browserHandler by lazy {
        val handlerThread = HandlerThread("vlc-mProvider", Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE)
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    val descriptionUpdate = MutableLiveData<Pair<Int, String>>()
    protected val browserContext by lazy { HandlerContext(browserHandler, "mProvider-context") }
    internal val medialibrary = Medialibrary.getInstance()

    protected open fun initBrowser(listener: EventListener = this) {
        if (mediabrowser === null) mediabrowser = MediaBrowser(VLCInstance.get(), listener, browserHandler)
        else mediabrowser?.changeEventListener(listener)
    }

    override fun fetch() {
        val prefetchList = prefectLists[url]
        if (!refreshing && prefetchList !== null && !prefetchList.isEmpty()) {
            dataset.value = prefetchList
            prefectLists.remove(url)
            parseSubDirectories()
        } else if (url === null) {
            browseRoot()
            parseSubDirectories()
        } else browse(url)
    }

    @Volatile
    private var refreshing = false
    private val refreshList = mutableListOf<MediaLibraryItem>()
    override fun refresh(): Boolean {
        refreshing = true
        fetch()
        return true
    }

    @MainThread
    override fun sort(sort: Int): Boolean {
        this.sort = sort
        desc = !desc
        dataset.value = dataset.value?.apply { sortWith(if (desc) descComp else ascComp) }
        return true
    }

    override fun onMediaAdded(index: Int, media: Media?) {
        media?.apply {
            if (refreshing) refreshList.add(MediaWrapper(this@apply))
            else launch(UI) { addMedia(MediaWrapper(this@apply)) }
        }
    }

    override fun onMediaRemoved(index: Int, media: Media?) {
        media?.let { launch(UI) { remove(MediaWrapper(it)) } }
    }

    override fun onBrowseEnd() {
        if (refreshing) {
            refreshing = false
            val list = refreshList.toMutableList();
            refreshList.clear()
            launch(UI) { dataset.value = list }
        }
        launch(UI) { parseSubDirectories() }
    }

    fun releaseBrowser() {
        launch(browserContext) {
            mediabrowser?.release()
            mediabrowser = null
        }
    }

    //TODO Show hidden files
    private val foldersContentMap = SimpleArrayMap<MediaLibraryItem, MutableList<MediaLibraryItem>>()
    private val currentMediaList = mutableListOf<MediaLibraryItem>()
    private var currentParsedPosition = 0
    private fun parseSubDirectories() {
        synchronized(currentMediaList) {
            currentMediaList.addAll(dataset.value ?: mutableListOf())
            if (currentMediaList.isEmpty()) return
        }
        launch(browserContext) {
            synchronized(currentMediaList) {
                foldersContentMap.clear()
                initBrowser(parserListener)
                currentParsedPosition = 0
                while (currentParsedPosition < currentMediaList.size) {
                    val item = currentMediaList[currentParsedPosition]
                    val mw: MediaWrapper?
                    if (item.itemType == MediaLibraryItem.TYPE_STORAGE) {
                        mw = MediaWrapper((item as Storage).uri)
                        mw.type = MediaWrapper.TYPE_DIR
                    } else if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
                        mw = item as MediaWrapper
                    } else
                        mw = null
                    if (mw != null) {
                        if (mw.type == MediaWrapper.TYPE_DIR || mw.type == MediaWrapper.TYPE_PLAYLIST) {
                            val uri = mw.uri
                            mediabrowser?.browse(uri, 0)
                            return@launch
                        }
                    }
                    ++currentParsedPosition
                }
            }
        }
    }

    private val parserListener by lazy { object: EventListener {
        private val directories: MutableList<MediaWrapper> = ArrayList()
        private val files: MutableList<MediaWrapper> = ArrayList()

        override fun onMediaAdded(index: Int, media: Media?) {
            media?.apply {
                val type = this.type
                val mw = getMediaWrapper(MediaWrapper(this))
                if (type == Media.Type.Directory) directories.add(mw)
                else if (type == Media.Type.File) files.add(mw)
            }
        }

        override fun onMediaRemoved(index: Int, media: Media?) {}

        override fun onBrowseEnd() {
            synchronized(currentMediaList) {
                if (currentMediaList.isEmpty()) {
                    currentParsedPosition = -1
                    releaseBrowser()
                    return
                }
                val holderText = getDescription(directories.size, files.size)
                var mw: MediaWrapper? = null

                if (!TextUtils.equals(holderText, "")) {
                    val item = currentMediaList[currentParsedPosition]
                    val position = currentParsedPosition
                    launch(UI) {
                        item.description = holderText
                        descriptionUpdate.value = Pair(position, holderText)
                    }
                    directories.addAll(files)
                    foldersContentMap.put(item, ArrayList<MediaLibraryItem>(directories))
                }
                while (++currentParsedPosition < currentMediaList.size) { //skip media that are not browsable
                    val item = currentMediaList[currentParsedPosition]
                    if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
                        mw = item as MediaWrapper
                        if (mw.type == MediaWrapper.TYPE_DIR || mw.type == MediaWrapper.TYPE_PLAYLIST)
                            break
                    } else if (item.itemType == MediaLibraryItem.TYPE_STORAGE) {
                        mw = MediaWrapper((item as Storage).uri)
                        break
                    } else {
                        mw = null
                    }
                }

                if (mw != null) {
                    if (currentParsedPosition < currentMediaList.size) {
                        mediabrowser?.browse(mw.uri, 0)
                    } else {
                        currentParsedPosition = -1
                        currentMediaList.clear()
                        releaseBrowser()
                    }
                } else {
                    releaseBrowser()
                    currentMediaList.clear()
                }
                directories.clear()
                files.clear()
            }
        }


    } }

    private val sb = StringBuilder()
    private fun getDescription(folderCount: Int, mediaFileCount: Int): String {
        val res = VLCApplication.getAppResources()
        sb.setLength(0)
        if (folderCount > 0) {
            sb.append(res.getQuantityString(
                    R.plurals.subfolders_quantity, folderCount, folderCount
            ))
            if (mediaFileCount > 0) sb.append(", ")
        }
        if (mediaFileCount > 0) sb.append(res.getQuantityString(
                    R.plurals.mediafiles_quantity, mediaFileCount, mediaFileCount))
        else if (folderCount == 0 && mediaFileCount == 0) sb.append(res.getString(R.string.directory_empty))
        return sb.toString()
    }

    private fun getMediaWrapper(media: MediaWrapper): MediaWrapper {
        val uri = media.uri
        if ((media.type == MediaWrapper.TYPE_AUDIO || media.type == MediaWrapper.TYPE_VIDEO) && "file" == uri.scheme)
            medialibrary.getMedia(uri)?.apply { return this }
        return media
    }

    abstract fun browseRoot()
    open fun getFlags() = MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect

    fun browse(url: String) {
        launch(browserContext) {
            initBrowser()
            mediabrowser?.browse(Uri.parse(url), getFlags()) }
    }

    override fun onCleared() {
        super.onCleared()
        releaseBrowser()
    }

    fun saveList(media: MediaWrapper) {
        foldersContentMap[media]?.let { if (!it.isEmpty()) prefectLists[media.location] = it }
    }

    fun isFolderEmpty(mw: MediaWrapper) = foldersContentMap[mw]?.isEmpty() ?: true

    companion object {
        const val TAG = "VLC/BrowserProvider"
        val prefectLists = mutableMapOf<String, MutableList<MediaLibraryItem>>()

        val ascComp = Comparator<MediaLibraryItem> { item1, item2 ->
            if (item1?.itemType == MediaLibraryItem.TYPE_MEDIA) {
                val type1 = (item1 as MediaWrapper).type
                val type2 = (item2 as MediaWrapper).type
                if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR)
                    return@Comparator -1
                else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR)
                    return@Comparator 1
            }
            item1?.title?.toLowerCase()?.compareTo(item2?.title?.toLowerCase() ?: "") ?: -1
        }
        val descComp = Comparator<MediaLibraryItem> { item1, item2 ->
            if (item1?.itemType == MediaLibraryItem.TYPE_MEDIA) {
                val type1 = (item1 as MediaWrapper).type
                val type2 = (item2 as MediaWrapper).type
                if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR)
                    return@Comparator -1
                else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR)
                    return@Comparator 1
            }
            item2?.title?.toLowerCase()?.compareTo(item1?.title?.toLowerCase() ?: "") ?: -1
        }
    }
}