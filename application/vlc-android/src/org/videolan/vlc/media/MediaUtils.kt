package org.videolan.vlc.media

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SimpleArrayMap
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.resources.interfaces.IMediaContentResolver
import org.videolan.resources.interfaces.ResumableList
import org.videolan.resources.util.getFromMl
import org.videolan.tools.AppScope
import org.videolan.tools.localBroadcastManager
import org.videolan.tools.safeOffer
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.dialogs.SubtitleDownloaderDialogFragment
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

private const val TAG = "VLC/MediaUtils"

private typealias MediaContentResolver = SimpleArrayMap<String, IMediaContentResolver>

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object MediaUtils {
    fun getSubs(activity: FragmentActivity, mediaList: List<MediaWrapper>) {
        if (activity is AppCompatActivity) showSubtitleDownloaderDialogFragment(activity, mediaList.map { it.uri })
        else {
            val intent = Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SUBS_DL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putParcelableArrayListExtra(DialogActivity.EXTRA_MEDIALIST, mediaList as? ArrayList
                    ?: ArrayList(mediaList))
            ContextCompat.startActivity(activity, intent, null)
        }
    }

    fun getSubs(activity: FragmentActivity, media: MediaWrapper) = getSubs(activity, listOf(media))

    fun showSubtitleDownloaderDialogFragment(activity: FragmentActivity, mediaUris: List<Uri>) {
        val callBack = java.lang.Runnable {
            SubtitleDownloaderDialogFragment.newInstance(mediaUris).show(activity.supportFragmentManager, "Subtitle_downloader")
        }
        if (Permissions.canWriteStorage()) callBack.run()
        else Permissions.askWriteStoragePermission(activity, false, callBack)
    }

    suspend fun deleteMedia(mw: MediaLibraryItem, failCB: Runnable? = null) = withContext(Dispatchers.IO) {
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
            }
        }
        val mediaLibrary = Medialibrary.getInstance()
        for (folder in foldersToReload) mediaLibrary.reload(folder)
        if (mw is Album) {
            foldersToReload.forEach {
                if (File(it).list().isNullOrEmpty()) {
                    FileUtils.deleteFile(it)
                }
            }
        }
        if (mediaPaths.isEmpty()) {
            failCB?.run()
            false
        } else true
    }

    fun loadlastPlaylist(context: Context?, type: Int) {
        if (context == null) return
        SuspendDialogCallback(context) { service -> service.loadLastPlaylist(type) }
    }

    fun appendMedia(context: Context?, media: List<MediaWrapper>?) {
        if (media == null || media.isEmpty() || context == null) return
        SuspendDialogCallback(context) { service ->
            service.append(media)
            context.let {
                if (it is Activity) {
                    val text = context.resources.getQuantityString(R.plurals.tracks_appended, media.size, media.size)
                    Snackbar.make(it.findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    fun appendMedia(context: Context?, media: MediaWrapper?) {
        if (media != null) appendMedia(context, arrayListOf(media))
    }

    fun appendMedia(context: Context, array: Array<MediaWrapper>) = appendMedia(context, array.asList())

    fun insertNext(context: Context?, media: Array<MediaWrapper>?) {
        if (media == null || context == null) return
        SuspendDialogCallback(context) { service ->
            service.insertNext(media)
            context.let {
                if (it is Activity) {
                    val text = context.resources.getQuantityString(R.plurals.tracks_inserted, media.size, media.size)
                    Snackbar.make(it.findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    fun insertNext(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        insertNext(context, arrayOf(media))
    }

    fun openMedia(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        SuspendDialogCallback(context) { service -> service.load(media) }
    }

    fun openMediaNoUi(ctx: Context, id: Long) = AppScope.launch {
        val media = ctx.getFromMl { getMedia(id) }
        openMediaNoUi(ctx, media)
    }

    fun openMediaNoUi(uri: Uri) = openMediaNoUi(AppContextProvider.appContext, MLServiceLocator.getAbstractMediaWrapper(uri))

    fun openMediaNoUi(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        object : BaseCallBack(context) {
            override fun onServiceReady(service: PlaybackService) {
                service.load(media)
            }
        }
    }

    fun playTracks(context: Context, item: MediaLibraryItem, position: Int) = context.scope.launch {
        openList(context, withContext(Dispatchers.IO) { item.tracks }.toList(), position)
    }

    fun playAlbums(context: Context?, provider: MedialibraryProvider<Album>, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            when (count) {
                0 -> null
                in 1..MEDIALIBRARY_PAGE_SIZE -> withContext(Dispatchers.IO) {
                    mutableListOf<MediaWrapper>().apply {
                        for (album in provider.getAll()) album.tracks?.let { addAll(it) }
                    }
                }
                else -> withContext(Dispatchers.IO) {
                    mutableListOf<MediaWrapper>().apply {
                        var index = 0
                        while (index < count) {
                            val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                            val albums = withContext(Dispatchers.IO) { provider.getPage(pageCount, index) }
                            for (album in albums) addAll(album.tracks)
                            index += pageCount
                        }
                    }
                }
            }?.takeIf { it.isNotEmpty() }?.let { list ->
                service.load(list, if (shuffle) Random().nextInt(count) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
        }
    }

    fun playAll(context: Activity?, provider: MedialibraryProvider<MediaWrapper>, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            fun play(list: List<MediaWrapper>) {
                service.load(list, if (shuffle) Random().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) { provider.getAll().toList() })
                else -> {
                    var index = 0
                    val appendList = mutableListOf<MediaWrapper>()
                    while (index < count) {
                        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                        val list = withContext(Dispatchers.IO) { provider.getPage(pageCount, index).toList() }
                        if (index == 0) play(list)
                        else appendList.addAll(list)
                        index += pageCount
                    }
                    service.append(appendList)
                }
            }
        }
    }

    fun playAllTracks(context: Context?, provider: VideoGroupsProvider, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            fun play(list: List<MediaWrapper>) {
                service.load(list, if (shuffle) Random().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) {
                    provider.getAll().flatMap {
                        it.media(Medialibrary.SORT_DEFAULT, false, it.mediaCount(), 0).toList()
                    }
                })
                else -> {
                    var index = 0
                    while (index < count) {
                        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                        val list = withContext(Dispatchers.IO) {
                            provider.getPage(pageCount, index).toList()
                        }
                        if (index == 0) play(list)
                        else service.append(list)
                        index += pageCount
                    }
                }
            }
        }
    }

    fun playAllTracks(context: Context?, provider: FoldersProvider, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            fun play(list: List<MediaWrapper>) {
                service.load(list, if (shuffle) Random().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) {
                    provider.getAll().flatMap {
                        it.media(provider.type, Medialibrary.SORT_DEFAULT, false, it.mediaCount(provider.type), 0).toList()
                    }
                })
                else -> {
                    var index = 0
                    while (index < count) {
                        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                        val list = withContext(Dispatchers.IO) {
                            provider.getPage(pageCount, index).flatMap {
                                it.media(provider.type, Medialibrary.SORT_DEFAULT, false, it.mediaCount(provider.type), 0).toList()
                            }
                        }
                        if (index == 0) play(list)
                        else service.append(list)
                        index += pageCount
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun openList(context: Context?, list: List<MediaWrapper>, position: Int, shuffle: Boolean = false) {
        if (list.isNullOrEmpty() || context == null) return
        SuspendDialogCallback(context) { service ->
            service.load(list, position)
            if (shuffle && !service.isShuffling) service.shuffle()
        }
    }

    fun openUri(context: Context?, uri: Uri?) {
        if (uri == null || context == null) return
        SuspendDialogCallback(context) { service ->
            service.loadUri(uri)
        }
    }

    fun openStream(context: Context?, uri: String?) {
        if (uri == null || context == null) return
        SuspendDialogCallback(context) { service ->
            service.loadLocation(uri)
        }
    }

    fun getMediaArtist(ctx: Context, media: MediaWrapper?) = media?.artist
            ?: if (media?.nowPlaying != null) "" else getMediaString(ctx, R.string.unknown_artist)

    fun getMediaReferenceArtist(ctx: Context, media: MediaWrapper?) = getMediaArtist(ctx, media)

    fun getMediaAlbumArtist(ctx: Context, media: MediaWrapper?) = media?.albumArtist
            ?: getMediaString(ctx, R.string.unknown_artist)

    fun getMediaAlbum(ctx: Context, media: MediaWrapper?) = media?.album
            ?: if (media?.nowPlaying != null) "" else getMediaString(ctx, R.string.unknown_album)

    fun getMediaGenre(ctx: Context, media: MediaWrapper?) = media?.genre
            ?: getMediaString(ctx, R.string.unknown_genre)

    fun getMediaSubtitle(media: MediaWrapper): String? {
        var subtitle = media.nowPlaying ?: media.artist
        if (media.length > 0L) {
            subtitle = if (TextUtils.isEmpty(subtitle)) Tools.millisToString(media.length)
            else "$subtitle  •  ${Tools.millisToString(media.length)}"
        }
        return subtitle
    }

    fun getMediaTitle(mediaWrapper: MediaWrapper) = mediaWrapper.title
            ?: FileUtils.getFileNameFromPath(mediaWrapper.location)

    fun getContentMediaUri(data: Uri) = try {
        AppContextProvider.appContext.contentResolver.query(data,
                arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            if (it.moveToFirst()) AndroidUtil.PathToUri(it.getString(columnIndex)) ?: data else data
        }
    } catch (e: SecurityException) {
        data
    } catch (e: IllegalArgumentException) {
        data
    } catch (e: NullPointerException) {
        data
    }

    private fun getMediaString(ctx: Context?, id: Int): String {
        return ctx?.resources?.getString(id)
                ?: when (id) {
                    R.string.unknown_artist -> "Unknown Artist"
                    R.string.unknown_album -> "Unknown Album"
                    R.string.unknown_genre -> "Unknown Genre"
                    else -> ""
                }
    }

    @Suppress("LeakingThis")
    private abstract class BaseCallBack(context: Context) {

        init {
            AppScope.launch {
                onServiceReady(PlaybackService.serviceFlow.filterNotNull().first())
            }
            PlaybackService.start(context)
        }

        abstract fun onServiceReady(service: PlaybackService)
    }

    @ObsoleteCoroutinesApi
    private class SuspendDialogCallback(context: Context, private val task: suspend (service: PlaybackService) -> Unit) {
        private lateinit var dialog: ProgressDialog
        var job: Job = Job()
        val scope = context.scope
        val actor = scope.actor<Action>(capacity = Channel.UNLIMITED) {
            for (action in channel) when (action) {
                Connect -> {
                    val service = PlaybackService.instance
                    if (service != null) channel.offer(Task(service, task))
                    else {
                        PlaybackService.start(context)
                        PlaybackService.serviceFlow.filterNotNull().first().let {
                            channel.offer(Task(it, task))
                        }
                    }
                }
                Disconnect -> dismiss()
                is Task -> {
                    action.task.invoke(action.service)
                    job.cancel()
                    dismiss()
                }
            }
        }

        init {
            job = scope.launch {
                delay(300)
                dialog = ProgressDialog.show(
                        context,
                        "${context.applicationContext.getString(R.string.loading)}…",
                        context.applicationContext.getString(R.string.please_wait), true)
                dialog.setCancelable(true)
                dialog.setOnCancelListener { actor.offer(Disconnect) }
            }
            actor.safeOffer(Connect)
        }

        private fun dismiss() {
            try {
                if (this::dialog.isInitialized && dialog.isShowing) dialog.dismiss()
            } catch (ignored: IllegalArgumentException) {}
        }
    }

    fun retrieveMediaTitle(mw: MediaWrapper) = try {
        AppContextProvider.appContext.contentResolver.query(mw.uri, null, null, null, null)?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex > -1 && it.count > 0) {
                it.moveToFirst()
                if (!it.isNull(nameIndex)) mw.title = it.getString(nameIndex)
            }
        }
    } catch (ignored: UnsupportedOperationException) {
    } catch (ignored: IllegalArgumentException) {
    } catch (ignored: NullPointerException) {
    } catch (ignored: IllegalStateException) {
    } catch (ignored: SecurityException) {}

    fun deletePlaylist(playlist: Playlist) = AppScope.launch(Dispatchers.IO) { playlist.delete() }

    fun openMediaNoUiFromTvContent(context: Context, data: Uri?) = AppScope.launch {
        val id = data?.lastPathSegment ?: return@launch
        when {
            id.startsWith(CONTENT_PREFIX) -> {
                val intent = Intent(ACTION_OPEN_CONTENT).putExtra(EXTRA_CONTENT_ID, id)
                context.localBroadcastManager.sendBroadcast(intent)
            }
            else -> { //Media from medialib
                val mw = context.getFromMl {
                    val longId = id.substringAfter("_").toLong()
                    when {
                        id.startsWith("album_") -> getAlbum(longId)
                        id.startsWith("artist_") -> getArtist(longId)
                        else -> getMedia(longId)
                    }
                } ?: return@launch
                when (mw) {
                    is MediaWrapper -> openMediaNoUi(mw.uri)
                    is Album -> playAlbum(context, mw)
                    is Artist -> playArtist(context, mw)
                }
            }
        }
    }

    private fun playAlbum(context: Context?, album: Album) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            album.tracks?.takeIf { it.isNotEmpty() }?.let { list ->
                service.load(list, 0)
            }
        }
    }

    private fun playArtist(context: Context?, artist: Artist) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            artist.tracks?.takeIf { it.isNotEmpty() }?.let { list ->
                service.load(list, 0)
            }
        }
    }
}

@WorkerThread
fun Folder.getAll(type: Int = Folder.TYPE_FOLDER_VIDEO, sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false): List<MediaWrapper> {
    var index = 0
    val count = mediaCount(type)
    val all = mutableListOf<MediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = media(type, sort, desc, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

@WorkerThread
fun VideoGroup.getAll(sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false): List<MediaWrapper> {
    var index = 0
    val count = mediaCount()
    val all = mutableListOf<MediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = media(sort, desc, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

fun List<MediaLibraryItem>.getAll(sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false) = flatMap {
    when (it) {
        is VideoGroup -> it.getAll(sort, desc)
        is MediaWrapper -> listOf(it)
        else -> listOf()
    }
}

fun List<Folder>.getAll(type: Int = Folder.TYPE_FOLDER_VIDEO, sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false) = flatMap {
    it.getAll(type, sort, desc)
}

private fun Array<MediaLibraryItem>.toList() = flatMap {
    if (it is VideoGroup) {
        it.media(Medialibrary.SORT_DEFAULT, false, it.mediaCount(), 0).toList()
    } else listOf(this as MediaWrapper)
}

fun MediaContentResolver.canHandle(id: String) : Boolean {
    for (i in 0 until size()) if (id.startsWith(keyAt(i))) return true
    return false
}

suspend fun MediaContentResolver.getList(context: Context, id: String) : ResumableList {
    for ( i in 0 until size()) if (id.startsWith(keyAt(i))) return valueAt(i).getList(context, id)
    return null
}

private val Context.scope: CoroutineScope
    get() = (this as? CoroutineScope) ?: AppScope

private sealed class Action
private object Connect : Action()
private object Disconnect : Action()
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
private class Task(val service: PlaybackService, val task: suspend (service: PlaybackService) -> Unit) : Action()
