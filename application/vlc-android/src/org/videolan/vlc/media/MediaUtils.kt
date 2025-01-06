package org.videolan.vlc.media

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SimpleArrayMap
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.security.SecureRandom
import java.util.LinkedList
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.ACTION_OPEN_CONTENT
import org.videolan.resources.AppContextProvider
import org.videolan.resources.CONTENT_PREFIX
import org.videolan.resources.EXTRA_CONTENT_ID
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.VLCOptions
import org.videolan.resources.interfaces.IMediaContentResolver
import org.videolan.resources.interfaces.ResumableList
import org.videolan.resources.util.getFromMl
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.tools.localBroadcastManager
import org.videolan.tools.markBidi
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.dialogs.SubtitleDownloaderDialogFragment
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.generateResolutionClass
import org.videolan.vlc.util.isOTG
import org.videolan.vlc.util.isSD
import org.videolan.vlc.util.isSchemeStreaming

private const val TAG = "VLC/MediaUtils"

private typealias MediaContentResolver = SimpleArrayMap<String, IMediaContentResolver>

object MediaUtils {
    fun getSubs(activity: FragmentActivity, media: MediaWrapper) {
        if (activity is AppCompatActivity) showSubtitleDownloaderDialogFragment(activity, media.uri, media.title)
        else {
            val intent = Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SUBS_DL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(DialogActivity.EXTRA_MEDIA, media)
            ContextCompat.startActivity(activity, intent, null)
        }
    }


    fun showSubtitleDownloaderDialogFragment(activity: FragmentActivity, mediaUris: Uri, mediaTitles:String) {
        SubtitleDownloaderDialogFragment.newInstance(mediaUris, mediaTitles).show(activity.supportFragmentManager, "Subtitle_downloader")
    }


    fun deleteItem(activity:FragmentActivity, item: MediaLibraryItem, onDeleteFailed:(MediaLibraryItem)->Unit) {
        val deletionAction = when (item) {
            is MediaWrapper, is Album -> Runnable {
                activity.lifecycleScope.launchWhenStarted {
                    if (!deleteMedia(item, null)) onDeleteFailed.invoke(item)
                }
            }
            is Playlist -> Runnable { deletePlaylist(item) }
            else -> Runnable { onDeleteFailed.invoke(item) }
        }

        if (item is MediaWrapper) {
            if (Permissions.checkWritePermission(activity, item, deletionAction)) deletionAction.run()
        } else {
            deletionAction.run()
        }
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
                    if (it is AudioPlayerContainerActivity) {
                        Snackbar.make(it.appBarLayout, text, Snackbar.LENGTH_LONG).show()
                    } else
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

    fun openMediaNoUi(ctx: Context, uri: Uri) = AppScope.launch {
        var media = ctx.getFromMl { getMedia(uri) }
        if (media == null)
            media = MLServiceLocator.getAbstractMediaWrapper(uri)
        openMediaNoUi(ctx, media)
    }
    fun openMediaNoUi(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        object : BaseCallBack(context) {
            override fun onServiceReady(service: PlaybackService) {
                service.load(media)
            }
        }
    }

    fun playTracks(context: Context, item: MediaLibraryItem, position: Int, shuffle:Boolean = false) = context.scope.launch {
        openList(context, withContext(Dispatchers.IO) { item.tracks }.toList(), position, shuffle)
    }

    fun playTracks(context: Context, provider: MedialibraryProvider<MediaWrapper>, position: Int, shuffle: Boolean = false) = context.scope.launch {
        provider.loadPagedList(context, {
                provider.pagedList.value ?: listOf()
            }, { list, _ ->
           openList(context, list, position, shuffle)
        })
    }

    fun playAlbums(context: Context?, provider: MedialibraryProvider<Album>, position: Int, shuffle: Boolean) = context?.scope?.launch {
        provider.loadPagedList(context, {
            mutableListOf<MediaWrapper>().apply {
                for (album in provider.getAll()) album.tracks?.let { addAll(it) }
            }
        }, { l, service ->
            l.takeIf { l.isNotEmpty() }?.let { list ->
                service.load(list, if (shuffle) SecureRandom().nextInt(list.size) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
        })
    }

    fun playAll(context: Activity?, provider: MedialibraryProvider<MediaWrapper>, position: Int, shuffle: Boolean) = context?.scope?.launch {
        provider.loadPagedList(context, {
            provider.getAll().toList()
        }, { l, service ->
            l.takeIf { l.isNotEmpty() }?.let { list ->
                service.load(list, if (shuffle) SecureRandom().nextInt(min(list.size, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
        })
    }

    fun playAllTracks(context: Context?, provider: VideoGroupsProvider, mediaToPlay: MediaWrapper?, shuffle: Boolean) = context?.scope?.launch {
        provider.loadPagedList(context, {
            provider.getAll().flatMap {
                it.media(Medialibrary.SORT_DEFAULT, false, Settings.includeMissing, false, it.mediaCount(), 0).toList()
            }
        }, {l, service ->
            l.takeIf { l.isNotEmpty() }?.let { list ->
                service.load(list, if (shuffle) SecureRandom().nextInt(min(list.size, MEDIALIBRARY_PAGE_SIZE)) else list.indexOf(mediaToPlay))
                if (shuffle && !service.isShuffling) service.shuffle()
            }
        })
    }

    fun playAllTracks(context: Context?, provider: FoldersProvider, position: Int, shuffle: Boolean) = context?.scope?.launch {
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            fun play(list: List<MediaWrapper>) {
                service.load(list, if (shuffle) SecureRandom().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) {
                    provider.getAll().flatMap {
                        it.media(provider.type, Medialibrary.SORT_DEFAULT, false, Settings.includeMissing, false, it.mediaCount(provider.type), 0).toList()
                    }
                })
                else -> {
                    var index = 0
                    while (index < count) {
                        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                        val list = withContext(Dispatchers.IO) {
                            provider.getPage(pageCount, index).flatMap {
                                it.media(provider.type, Medialibrary.SORT_DEFAULT, false, Settings.includeMissing, false, it.mediaCount(provider.type), 0).toList()
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
        if (list.isEmpty() || context == null) return
        SuspendDialogCallback(context) { service -> // todo
            service.load(list, position)
            if (shuffle && !service.isShuffling) service.shuffle()
        }
    }

    @JvmOverloads
    fun openPlaylist(context: Context?, playlistId: Long, position: Int = 0, shuffle: Boolean = false) {
        if (playlistId == -1L || context == null) return
        SuspendDialogCallback(context) { service ->
           val playlist =  context.getFromMl { getPlaylist(playlistId, Settings.includeMissing, false) }
            service.load(playlist.getPagedTracks(playlist.getRealTracksCount(Settings.includeMissing, false), 0, Settings.includeMissing, false), position)
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

    fun getMediaArtist(ctx: Context, media: MediaWrapper?): String = when {
        media == null -> getMediaString(ctx, R.string.unknown_artist)
        media.type == MediaWrapper.TYPE_VIDEO -> ""
        media.artistName != null -> media.artistName
        media.nowPlaying != null -> media.title
        isSchemeStreaming(media.uri.scheme) -> ""
        else -> getMediaString(ctx, R.string.unknown_artist)
    }

    fun getMediaReferenceArtist(ctx: Context, media: MediaWrapper?) = getMediaArtist(ctx, media)

    fun getMediaAlbumArtist(ctx: Context, media: MediaWrapper?) = media?.albumArtistName
            ?: getMediaString(ctx, R.string.unknown_artist)

    fun getMediaAlbum(ctx: Context, media: MediaWrapper?): String = when {
        media == null -> getMediaString(ctx, R.string.unknown_album)
        media.albumName != null -> media.albumName
        media.nowPlaying != null -> ""
        isSchemeStreaming(media.uri.scheme) -> ""
        else -> getMediaString(ctx, R.string.unknown_album)
    }

    fun getMediaGenre(ctx: Context, media: MediaWrapper?) = media?.genre
            ?: getMediaString(ctx, R.string.unknown_genre)

    fun getMediaSubtitle(media: MediaWrapper): String {
        val prefix = when {
            media.length <= 0L -> null
            media.type == MediaWrapper.TYPE_VIDEO -> Tools.millisToText(media.length)
            else -> Tools.millisToString(media.length)
        }
        val suffix = when {
            media.type == MediaWrapper.TYPE_VIDEO -> generateResolutionClass(media.width, media.height)
            media.length > 0L -> media.artistName
            isSchemeStreaming(media.uri.scheme) -> media.uri.toString()
            else -> media.artistName
        }
        return TextUtils.separatedString(prefix, suffix)
    }

    fun getDisplaySubtitle(ctx: Context, media: MediaWrapper): String? {
        val album = getMediaAlbum(ctx, media)
        val artist = getMediaArtist(ctx, media)
        val isAlbumUnknown = album == getMediaString(ctx, R.string.unknown_album)
        val isArtistUnknown = artist == getMediaString(ctx, R.string.unknown_artist)
        return if (!isArtistUnknown && !isAlbumUnknown) TextUtils.separatedString(artist.markBidi(), album.markBidi()) else null
    }

    fun getQueuePosition(mediaPosition: Int, mediaSize: Int, shortQueue: Boolean = false): String? {
        return when {
            shortQueue && mediaSize > 1 -> "${mediaPosition + 1}"
            mediaSize > 1 -> "${mediaPosition + 1} / $mediaSize"
            else -> null
        }
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

    class SuspendDialogCallback(context: Context, private val task: suspend (service: PlaybackService) -> Unit) {
        private lateinit var dialog: ProgressDialog
        var job: Job = Job()
        val scope = context.scope
        @OptIn(ObsoleteCoroutinesApi::class)
        val actor = scope.actor<Action>(capacity = Channel.UNLIMITED) {
            for (action in channel) when (action) {
                Connect -> {
                    val service = PlaybackService.instance
                    if (service != null) channel.trySend(Task(service, task))
                    else {
                        PlaybackService.start(context)
                        PlaybackService.serviceFlow.filterNotNull().first().let {
                            channel.trySend(Task(it, task))
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
                dialog.setOnCancelListener { actor.trySend(Disconnect) }
            }
            actor.trySend(Connect)
        }

        private fun dismiss() {
            try {
                if (this::dialog.isInitialized && dialog.isShowing) dialog.dismiss()
                actor.close()
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
                    is MediaWrapper -> openMediaNoUi(context, mw.uri)
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

    suspend fun useAsSoundFont(context: Context, uri:Uri) {
        withContext(Dispatchers.IO) {
            FileUtils.copyFile(File(uri.path), VLCOptions.getSoundFontFile(context))
        }
    }
}

fun Folder.isOTG(): Boolean {
    try {
        return Uri.parse(mMrl).isOTG()
    } catch (_: Exception) {
    }
    return false
}
fun Folder.isSD(): Boolean {
    try {
        return Uri.parse(mMrl).isSD()
    } catch (_: Exception) {
    }
    return false
}

@WorkerThread
fun Folder.getAll(type: Int = Folder.TYPE_FOLDER_VIDEO, sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false, includeMissing:Boolean = true, onlyFavorites:Boolean = false): List<MediaWrapper> {
    var index = 0
    val count = mediaCount(type)
    val all = mutableListOf<MediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = media(type, sort, desc, includeMissing, onlyFavorites, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

@WorkerThread
fun VideoGroup.getAll(sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false,  includeMissing:Boolean = true, onlyFavorites:Boolean = false): List<MediaWrapper> {
    var index = 0
    val count = mediaCount()
    val all = mutableListOf<MediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = media(sort, desc, includeMissing, onlyFavorites, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

@WorkerThread
fun Album.getAll(sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false,  includeMissing:Boolean = true, onlyFavorites:Boolean = false): List<MediaWrapper> {
    var index = 0
    val count = realTracksCount
    val all = mutableListOf<MediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = getPagedTracks(sort, desc, includeMissing, onlyFavorites, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

@WorkerThread
fun Artist.getAll(sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false,  includeMissing:Boolean = true, onlyFavorites:Boolean = false): List<MediaWrapper> {
    var index = 0
    val count = tracksCount
    val all = mutableListOf<MediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = getPagedTracks(sort, desc, includeMissing, onlyFavorites, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

fun List<MediaLibraryItem>.getAll(sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false, onlyFavorites:Boolean = false) = flatMap {
    when (it) {
        is VideoGroup -> it.getAll(sort, desc, onlyFavorites)
        is MediaWrapper -> listOf(it)
        else -> listOf()
    }
}

fun List<Folder>.getAll(type: Int = Folder.TYPE_FOLDER_VIDEO, sort: Int = Medialibrary.SORT_DEFAULT, desc: Boolean = false, onlyFavorites:Boolean = false) = flatMap {
    it.getAll(type, sort, desc, onlyFavorites)
}

private fun Array<MediaLibraryItem>.toList() = flatMap {
    if (it is VideoGroup) {
        it.media(Medialibrary.SORT_DEFAULT, false, true, false, it.mediaCount(), 0).toList()
    } else listOf(it as MediaWrapper)
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

open class Action
private object Connect : Action()
private object Disconnect : Action()
private class Task(val service: PlaybackService, val task: suspend (service: PlaybackService) -> Unit) : Action()
