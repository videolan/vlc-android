package org.videolan.vlc.media

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.dialogs.SubtitleDownloaderDialogFragment
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.util.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

private const val TAG = "VLC/MediaUtils"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object MediaUtils : CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate

    fun getSubs(activity: FragmentActivity, mediaList: List<AbstractMediaWrapper>) {
        if (activity is AppCompatActivity) showSubtitleDownloaderDialogFragment(activity, mediaList.map { it.uri })
        else {
            val intent = Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SUBS_DL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putParcelableArrayListExtra(DialogActivity.EXTRA_MEDIALIST, mediaList as? ArrayList
                    ?: ArrayList(mediaList))
            ContextCompat.startActivity(activity, intent, null)
        }
    }

    fun getSubs(activity: FragmentActivity, media: AbstractMediaWrapper) {
        getSubs(activity, listOf(media))
    }

    fun showSubtitleDownloaderDialogFragment(activity: FragmentActivity, mediaUris: List<Uri>) {
        val callBack = java.lang.Runnable {
            SubtitleDownloaderDialogFragment.newInstance(mediaUris).show(activity.supportFragmentManager, "Subtitle_downloader")
        }
        if (Permissions.canWriteStorage()) callBack.run()
        else Permissions.askWriteStoragePermission(activity, false, callBack)
    }

    fun loadlastPlaylist(context: Context?, type: Int) {
        if (context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.loadLastPlaylist(type)
            }
        })
    }

    fun appendMedia(context: Context?, media: List<AbstractMediaWrapper>?) {
        if (media == null || media.isEmpty() || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.append(media)
                context.let {
                    if (it is Activity) {
                        Snackbar.make(it.findViewById(android.R.id.content), R.string.appended, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    fun appendMedia(context: Context?, media: AbstractMediaWrapper?) {
        if (media != null) appendMedia(context, arrayListOf(media))
    }

    fun appendMedia(context: Context, array: Array<AbstractMediaWrapper>) = appendMedia(context, array.asList())

    fun insertNext(context: Context?, media: Array<AbstractMediaWrapper>?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.insertNext(media)
                context.let {
                    if (it is Activity) {
                        Snackbar.make(it.findViewById(android.R.id.content), R.string.inserted, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    fun insertNext(context: Context?, media: AbstractMediaWrapper?) {
        if (media == null || context == null) return
        insertNext(context, arrayOf(media))
    }

    fun openMedia(context: Context?, media: AbstractMediaWrapper?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.load(media)
            }
        })
    }

    fun openMediaNoUi(ctx: Context, id: Long) = launch {
        val media = ctx.getFromMl { getMedia(id) }
        openMediaNoUi(ctx, media)
    }

    fun openMediaNoUi(uri: Uri) = openMediaNoUi(VLCApplication.appContext, MLServiceLocator.getAbstractMediaWrapper(uri))

    fun openMediaNoUi(context: Context?, media: AbstractMediaWrapper?) {
        if (media == null || context == null) return
        object : BaseCallBack(context) {
            override fun onChanged(service: PlaybackService?) {
                if (service != null) {
                    service.load(media)
                    disconnect()
                }
            }
        }
    }

    fun playTracks(context: Context, item: MediaLibraryItem, position: Int) = launch {
        openList(context, withContext(Dispatchers.IO) { item.tracks }.toList(), position)
    }

    fun playAlbums(context: Context?, provider: MedialibraryProvider<AbstractAlbum>, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            when (count) {
                0 -> null
                in 1..MEDIALIBRARY_PAGE_SIZE -> withContext(Dispatchers.IO) {
                    mutableListOf<AbstractMediaWrapper>().apply {
                        for (album in provider.getAll()) album.tracks?.let { addAll(it) }
                    }
                }
                else -> withContext(Dispatchers.IO) {
                    mutableListOf<AbstractMediaWrapper>().apply {
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

    fun playAll(context: Context?, provider: MedialibraryProvider<AbstractMediaWrapper>, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            fun play(list: List<AbstractMediaWrapper>) {
                service.load(list, if (shuffle) Random().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) { provider.getAll().toList() })
                else -> {
                    var index = 0
                    val appendList = mutableListOf<AbstractMediaWrapper>()
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
            fun play(list: List<AbstractMediaWrapper>) {
                service.load(list, if (shuffle) Random().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) {
                    provider.getAll().flatMap {
                        it.media(AbstractMedialibrary.SORT_DEFAULT, false, it.mediaCount(), 0).toList()
                    }
                })
                else -> {
                    var index = 0
                    while (index < count) {
                        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                        val list = withContext(Dispatchers.IO) {
                            provider.getPage(pageCount, index).flatMap {
                                it.media(AbstractMedialibrary.SORT_DEFAULT, false, it.mediaCount(), 0).toList()
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

    fun playAllTracks(context: Context?, provider: FoldersProvider, position: Int, shuffle: Boolean) {
        if (context == null) return
        SuspendDialogCallback(context) { service ->
            val count = withContext(Dispatchers.IO) { provider.getTotalCount() }
            fun play(list: List<AbstractMediaWrapper>) {
                service.load(list, if (shuffle) Random().nextInt(min(count, MEDIALIBRARY_PAGE_SIZE)) else position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
            when (count) {
                0 -> return@SuspendDialogCallback
                in 1..MEDIALIBRARY_PAGE_SIZE -> play(withContext(Dispatchers.IO) {
                    provider.getAll().flatMap {
                        it.media(provider.type, AbstractMedialibrary.SORT_DEFAULT, false, it.mediaCount(provider.type), 0).toList()
                    }
                })
                else -> {
                    var index = 0
                    while (index < count) {
                        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
                        val list = withContext(Dispatchers.IO) {
                            provider.getPage(pageCount, index).flatMap {
                                it.media(provider.type, AbstractMedialibrary.SORT_DEFAULT, false, it.mediaCount(provider.type), 0).toList()
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
    fun openList(context: Context?, list: List<AbstractMediaWrapper>, position: Int, shuffle: Boolean = false) {
        if (Util.isListEmpty(list) || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.load(list, position)
                if (shuffle && !service.isShuffling) service.shuffle()
            }
        })
    }

    fun openUri(context: Context?, uri: Uri?) {
        if (uri == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.loadUri(uri)
            }
        })
    }

    fun openStream(context: Context?, uri: String?) {
        if (uri == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.loadLocation(uri)
            }
        })
    }

    fun getMediaArtist(ctx: Context, media: AbstractMediaWrapper?) = media?.artist
            ?: if (media?.nowPlaying != null) "" else getMediaString(ctx, R.string.unknown_artist)

    fun getMediaReferenceArtist(ctx: Context, media: AbstractMediaWrapper?) = getMediaArtist(ctx, media)

    fun getMediaAlbumArtist(ctx: Context, media: AbstractMediaWrapper?) = media?.albumArtist
            ?: getMediaString(ctx, R.string.unknown_artist)

    fun getMediaAlbum(ctx: Context, media: AbstractMediaWrapper?) = media?.album
            ?: if (media?.nowPlaying != null) "" else getMediaString(ctx, R.string.unknown_album)

    fun getMediaGenre(ctx: Context, media: AbstractMediaWrapper?) = media?.genre
            ?: getMediaString(ctx, R.string.unknown_genre)

    fun getMediaSubtitle(media: AbstractMediaWrapper): String? {
        var subtitle = media.nowPlaying ?: media.artist
        if (media.length > 0L) {
            subtitle = if (TextUtils.isEmpty(subtitle)) Tools.millisToString(media.length)
            else "$subtitle  •  ${Tools.millisToString(media.length)}"
        }
        return subtitle
    }

    fun getMediaTitle(mediaWrapper: AbstractMediaWrapper) = mediaWrapper.title
            ?: FileUtils.getFileNameFromPath(mediaWrapper.location)

    fun getContentMediaUri(data: Uri) = try {
        VLCApplication.appContext.contentResolver.query(data,
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
        return if (ctx != null) ctx.resources.getString(id)
        else when (id) {
            R.string.unknown_artist -> "Unknown Artist"
            R.string.unknown_album -> "Unknown Album"
            R.string.unknown_genre -> "Unknown Genre"
            else -> ""
        }
    }

    @Suppress("LeakingThis")
    private abstract class BaseCallBack : androidx.lifecycle.Observer<PlaybackService> {

        internal constructor(context: Context) {
            PlaybackService.service.observeForever(this)
            PlaybackService.start(context)
        }

        protected constructor()


        fun disconnect() {
            PlaybackService.service.removeObserver(this)
        }
    }

    private class DialogCallback(context: Context, private val runnable: Runnable) : BaseCallBack() {
        private lateinit var dialog: ProgressDialog
        private val handler = Handler(Looper.getMainLooper())

        internal interface Runnable {
            fun run(service: PlaybackService)
        }

        init {
            handler.postDelayed({
                dialog = ProgressDialog.show(
                        context,
                        "${context.applicationContext.getString(R.string.loading)} … ",
                        context.applicationContext.getString(R.string.please_wait),
                        true)
                dialog.setCancelable(true)
                dialog.setOnCancelListener(object : DialogInterface.OnCancelListener {
                    override fun onCancel(dialog: DialogInterface) {
                        synchronized(this) {
                            disconnect()
                        }
                    }
                })
            }, 300)
            synchronized(this) {
                PlaybackService.service.observeForever(this)
                PlaybackService.start(context)
            }
        }

        override fun onChanged(service: PlaybackService?) {
            if (service != null) {
                synchronized(this) {
                    runnable.run(service)
                    disconnect()
                }
                handler.removeCallbacksAndMessages(null)
                if (this::dialog.isInitialized) dialog.cancel()
            } else if (this::dialog.isInitialized && dialog.isShowing) dialog.dismiss()
        }
    }

    @ObsoleteCoroutinesApi
    private class SuspendDialogCallback(context: Context, private val task: suspend (service: PlaybackService) -> Unit) : BaseCallBack() {
        private lateinit var dialog: ProgressDialog
        var job: Job = Job()
        val actor = actor<Action>(capacity = Channel.UNLIMITED) {
            for (action in channel) when (action) {
                Connect -> {
                    PlaybackService.service.observeForever(this@SuspendDialogCallback)
                    PlaybackService.start(context)
                }
                Disconnect -> disconnect()
                is Task -> {
                    action.task.invoke(action.service)
                    job.cancel()
                    dismiss()
                    disconnect()
                }
            }
        }

        init {
            job = launch {
                delay(300)
                dialog = ProgressDialog.show(
                        context,
                        "${context.applicationContext.getString(R.string.loading)}…",
                        context.applicationContext.getString(R.string.please_wait), true)
                dialog.setCancelable(true)
                dialog.setOnCancelListener { actor.offer(Disconnect) }
            }
            actor.offer(Connect)
        }

        override fun onChanged(service: PlaybackService?) {
            service?.let { actor.offer(Task(it, task)) }
            dismiss()
        }

        private fun dismiss() {
            try {
                if (this::dialog.isInitialized && dialog.isShowing) dialog.dismiss()
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    fun retrieveMediaTitle(mw: AbstractMediaWrapper) = try {
        VLCApplication.appContext.contentResolver.query(mw.uri, null, null, null, null)?.use {
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
    } catch (ignored: SecurityException) {
    }

    fun deletePlaylist(playlist: AbstractPlaylist) = launch(Dispatchers.IO) { playlist.delete() }
}

@WorkerThread
fun AbstractFolder.getAll(type: Int = AbstractFolder.TYPE_FOLDER_VIDEO, sort: Int = AbstractMedialibrary.SORT_DEFAULT, desc: Boolean = false) : List<AbstractMediaWrapper> {
    var index = 0
    val count = mediaCount(type)
    val all = mutableListOf<AbstractMediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = media(type, sort, desc, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

@WorkerThread
fun AbstractVideoGroup.getAll(sort: Int = AbstractMedialibrary.SORT_DEFAULT, desc: Boolean = false) : List<AbstractMediaWrapper> {
    var index = 0
    val count = mediaCount()
    val all = mutableListOf<AbstractMediaWrapper>()
    while (index < count) {
        val pageCount = min(MEDIALIBRARY_PAGE_SIZE, count - index)
        val list = media(sort, desc, pageCount, index)
        all.addAll(list)
        index += pageCount
    }
    return all
}

fun List<AbstractVideoGroup>.getAll(sort: Int = AbstractMedialibrary.SORT_DEFAULT, desc: Boolean = false) : List<AbstractMediaWrapper> {
return flatMap { it.getAll(sort, desc) }
}

fun List<AbstractFolder>.getAll(type: Int = AbstractFolder.TYPE_FOLDER_VIDEO, sort: Int = AbstractMedialibrary.SORT_DEFAULT, desc: Boolean = false) : List<AbstractMediaWrapper> {
    return flatMap { it.getAll(type, sort, desc) }
}

private sealed class Action
private object Connect : Action()
private object Disconnect : Action()
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
private class Task(val service: PlaybackService, val task: suspend (service: PlaybackService) -> Unit) : Action()
