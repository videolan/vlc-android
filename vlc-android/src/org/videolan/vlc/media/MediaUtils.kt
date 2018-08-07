package org.videolan.vlc.media

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.SubtitlesDownloader
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.getFromMl

private const val TAG = "VLC/MediaUtils"

object MediaUtils {

    private val subtitlesDownloader by lazy { SubtitlesDownloader() }

    @JvmOverloads
    fun getSubs(activity: Activity, mediaList: List<MediaWrapper>, cb: SubtitlesDownloader.Callback? = null) {
        subtitlesDownloader.downloadSubs(activity, mediaList, cb)
    }

    fun loadlastPlaylist(context: Context?, type: Int) {
        if (context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.loadLastPlaylist(type)
            }
        })
    }

    @JvmOverloads
    fun getSubs(activity: Activity, media: MediaWrapper, cb: SubtitlesDownloader.Callback? = null) {
        getSubs(activity, mutableListOf(media), cb)
    }

    fun appendMedia(context: Context?, media: List<MediaWrapper>?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.append(media)
            }
        })
    }

    fun appendMedia(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.append(media)
            }
        })
    }

    fun appendMedia(context: Context, array: Array<MediaWrapper>) = appendMedia(context, array.asList())

    fun insertNext(context: Context?, media: Array<MediaWrapper>?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.insertNext(media)
            }
        })
    }

    fun insertNext(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.insertNext(media)
            }
        })
    }

    fun openMedia(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.load(media)
            }
        })
    }

    fun openMediaNoUi(ctx: Context, id: Long) = launch(UI, CoroutineStart.UNDISPATCHED) {
        val media = ctx.getFromMl { getMedia(id) }
        openMediaNoUi(ctx, media)
    }

    fun openMediaNoUi(uri: Uri) = openMediaNoUi(VLCApplication.getAppContext(), MediaWrapper(uri))

    fun openMediaNoUi(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        object : BaseCallBack(context) {
            override fun onConnected(service: PlaybackService) {
                service.load(media)
                client.disconnect()
            }
        }
    }

    fun openArray(context: Context, array: Array<MediaWrapper>, position: Int) = openList(context, array.toList(), position)

    @JvmOverloads
    fun openList(context: Context?, list: List<MediaWrapper>, position: Int, shuffle: Boolean = false) {
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

    fun getMediaArtist(ctx: Context, media: MediaWrapper?) = media?.artist ?: getMediaString(ctx, R.string.unknown_artist)

    fun getMediaReferenceArtist(ctx: Context, media: MediaWrapper?) = media?.artist ?: getMediaString(ctx, R.string.unknown_artist)

    fun getMediaAlbumArtist(ctx: Context, media: MediaWrapper?) = media?.albumArtist ?: getMediaString(ctx, R.string.unknown_artist)

    fun getMediaAlbum(ctx: Context, media: MediaWrapper?) = media?.album ?: getMediaString(ctx, R.string.unknown_album)

    fun getMediaGenre(ctx: Context, media: MediaWrapper?) = media?.genre ?: getMediaString(ctx, R.string.unknown_genre)

    fun getMediaSubtitle(media: MediaWrapper): String? {
        var subtitle = media.nowPlaying ?: media.artist
        if (media.length > 0L) {
            subtitle = if (TextUtils.isEmpty(subtitle)) Tools.millisToString(media.length)
            else "$subtitle  -  ${Tools.millisToString(media.length)}"
        }
        return subtitle
    }

    fun getMediaTitle(mediaWrapper: MediaWrapper) = mediaWrapper.title ?: FileUtils.getFileNameFromPath(mediaWrapper.location)!!

    fun getContentMediaUri(data: Uri): Uri {
        VLCApplication.getAppContext().contentResolver.query(data,
                arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            if (it.moveToFirst()) return AndroidUtil.PathToUri(it.getString(columnIndex)) ?: data
        }
        return data
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

    private abstract class BaseCallBack : PlaybackService.Client.Callback {
        protected lateinit var client: PlaybackService.Client

        internal constructor(context: Context) {
            client = PlaybackService.Client(context, this)
            client.connect()
        }

        protected constructor()


        override fun onDisconnected() {}
    }

    private class DialogCallback (context: Context, private val runnable: Runnable) : BaseCallBack() {
        private lateinit var dialog: ProgressDialog
        private val handler = Handler(Looper.getMainLooper())

        internal interface Runnable {
            fun run(service: PlaybackService)
        }

        init {
            client = PlaybackService.Client(context, this)
            handler.postDelayed({
                dialog = ProgressDialog.show(
                        context,
                        context.applicationContext.getString(R.string.loading) + "…",
                        context.applicationContext.getString(R.string.please_wait), true)
                dialog.setCancelable(true)
                dialog.setOnCancelListener(object : DialogInterface.OnCancelListener {
                    override fun onCancel(dialog: DialogInterface) {
                        synchronized(this) {
                            client.disconnect()
                        }
                    }
                })
            }, 300)
            synchronized(this) {
                client.connect()
            }

        }

        override fun onConnected(service: PlaybackService) {
            synchronized(this) {
                runnable.run(service)
            }
            handler.removeCallbacksAndMessages(null)
            if (this::dialog.isInitialized) dialog.cancel()
        }

        override fun onDisconnected() {
            if (this::dialog.isInitialized) dialog.dismiss()
        }
    }

    fun retrieveMediaTitle(mw: MediaWrapper) = try {
        VLCApplication.getAppContext().contentResolver.query(mw.uri, null, null, null, null)?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex > -1 && it.count > 0) {
                it.moveToFirst()
                if (!it.isNull(nameIndex)) mw.title = it.getString(nameIndex)
            }
        }
    } catch (ignored: UnsupportedOperationException) {}

    fun deletePlaylist(playlist: Playlist) = launch { playlist.delete() }
}
