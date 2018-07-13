package org.videolan.vlc.media

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.*
import java.util.*

private const val TAG = "VLC/MediaUtils"

object MediaUtils {

    private var sSubtitlesDownloader: SubtitlesDownloader? = null

    @JvmOverloads
    fun getSubs(activity: Activity, mediaList: List<MediaWrapper>, cb: SubtitlesDownloader.Callback? = null) {
        if (sSubtitlesDownloader == null)
            sSubtitlesDownloader = SubtitlesDownloader()
        sSubtitlesDownloader!!.downloadSubs(activity, mediaList, cb)
    }

    fun loadlastPlaylist(context: Context?, type: Int) {
        if (context == null) return
        DialogCallback(context, object : DialogCallback.Runnable {
            override fun run(service: PlaybackService) {
                service.loadLastPlaylist(type)
            }
        })
    }

    fun getSubs(activity: Activity, media: MediaWrapper, cb: SubtitlesDownloader.Callback) {
        val mediaList = ArrayList<MediaWrapper>()
        mediaList.add(media)
        getSubs(activity, mediaList, cb)
    }

    fun getSubs(activity: Activity, media: MediaWrapper) {
        val mediaList = ArrayList<MediaWrapper>()
        mediaList.add(media)
        getSubs(activity, mediaList)
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

    fun appendMedia(context: Context, array: Array<MediaWrapper>) {
        appendMedia(context, Arrays.asList(*array))
    }

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

    fun openMediaNoUi(id: Long) = launch(UI, CoroutineStart.UNDISPATCHED) {
        val media = withContext(VLCIO) {Medialibrary.getInstance().getMedia(id) }
        openMediaNoUi(VLCApplication.getAppContext(), media)
    }

    fun openMediaNoUi(uri: Uri) {
        val media = MediaWrapper(uri)
        openMediaNoUi(VLCApplication.getAppContext(), media)
    }

    fun openMediaNoUi(context: Context?, media: MediaWrapper?) {
        if (media == null || context == null) return
        object : BaseCallBack(context) {
            override fun onConnected(service: PlaybackService) {
                service.load(media)
                mClient.disconnect()
            }
        }
    }

    fun openArray(context: Context, array: Array<MediaWrapper>, position: Int) {
        openList(context, Arrays.asList(*array), position)
    }

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

    fun getMediaArtist(ctx: Context, media: MediaWrapper?): String {
        val artist = media?.artist
        return artist ?: getMediaString(ctx, R.string.unknown_artist)
    }

    fun getMediaReferenceArtist(ctx: Context, media: MediaWrapper?): String {
        val artist = media?.referenceArtist
        return artist ?: getMediaString(ctx, R.string.unknown_artist)
    }

    fun getMediaAlbumArtist(ctx: Context, media: MediaWrapper?): String {
        val albumArtist = media?.albumArtist
        return albumArtist ?: getMediaString(ctx, R.string.unknown_artist)
    }

    fun getMediaAlbum(ctx: Context, media: MediaWrapper?): String {
        val album = media?.album
        return album ?: getMediaString(ctx, R.string.unknown_album)

    }

    fun getMediaGenre(ctx: Context, media: MediaWrapper?): String {
        val genre = media?.genre
        return genre ?: getMediaString(ctx, R.string.unknown_genre)
    }

    fun getMediaSubtitle(media: MediaWrapper): String? {
        var subtitle = if (media.nowPlaying != null)
            media.nowPlaying
        else
            media.artist
        if (media.length > 0L) {
            subtitle = if (TextUtils.isEmpty(subtitle))
                Tools.millisToString(media.length)
            else
                subtitle + "  -  " + Tools.millisToString(media.length)
        }
        return subtitle
    }

    fun getMediaTitle(mediaWrapper: MediaWrapper): String {
        return mediaWrapper.title ?: FileUtils.getFileNameFromPath(mediaWrapper.location)
    }

    fun getContentMediaUri(data: Uri): Uri {
        var uri: Uri? = null
        try {
            val cursor = VLCApplication.getAppContext().contentResolver.query(data,
                    arrayOf(MediaStore.Video.Media.DATA), null, null, null)
            if (cursor != null) {
                val column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                if (cursor.moveToFirst())
                    uri = AndroidUtil.PathToUri(cursor.getString(column_index))
                cursor.close()
            } else
            // other content-based URI (probably file pickers)
                uri = data
        } catch (e: Exception) {
            uri = data
            if (uri.scheme == null)
                uri = AndroidUtil.PathToUri(uri.path)
        }

        return uri ?: data
    }

    private fun getMediaString(ctx: Context?, id: Int): String {
        return if (ctx != null)
            ctx.resources.getString(id)
        else {
            when (id) {
                R.string.unknown_artist -> "Unknown Artist"
                R.string.unknown_album -> "Unknown Album"
                R.string.unknown_genre -> "Unknown Genre"
                else -> ""
            }
        }
    }

    private abstract class BaseCallBack : PlaybackService.Client.Callback {
        protected lateinit var mClient: PlaybackService.Client

        internal constructor(context: Context) {
            mClient = PlaybackService.Client(context, this)
            mClient.connect()
        }

        protected constructor()

        override fun onDisconnected() {}
    }

    private class DialogCallback internal constructor(context: Context, private val mRunnable: Runnable) : BaseCallBack() {
        private var dialog: ProgressDialog? = null
        private val handler = Handler(Looper.getMainLooper())

        internal interface Runnable {
            fun run(service: PlaybackService)
        }

        init {
            mClient = PlaybackService.Client(context, this)
            handler.postDelayed({
                dialog = ProgressDialog.show(
                        context,
                        context.applicationContext.getString(R.string.loading) + "…",
                        context.applicationContext.getString(R.string.please_wait), true)
                dialog!!.setCancelable(true)
                dialog!!.setOnCancelListener(object : DialogInterface.OnCancelListener {
                    override fun onCancel(dialog: DialogInterface) {
                        synchronized(this) {
                            mClient.disconnect()
                        }
                    }
                })
            }, 300)
            synchronized(this) {
                mClient.connect()
            }
        }

        override fun onConnected(service: PlaybackService) {
            synchronized(this) {
                mRunnable.run(service)
            }
            handler.removeCallbacksAndMessages(null)
            if (dialog != null) dialog!!.cancel()
        }

        override fun onDisconnected() {
            dialog!!.dismiss()
        }
    }

    fun retrieveMediaTitle(mw: MediaWrapper) {
        var cursor: Cursor? = null
        try {
            cursor = VLCApplication.getAppContext().contentResolver.query(mw.uri, null, null, null, null)
            if (cursor == null) return
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex > -1 && cursor.count > 0) {
                cursor.moveToFirst()
                if (!cursor.isNull(nameIndex)) mw.title = cursor.getString(nameIndex)
            }
        } catch (e: SecurityException) { // We may not have storage access permission yet
            Log.w(TAG, "retrieveMediaTitle: fail to resolve file from " + mw.uri, e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "retrieveMediaTitle: fail to resolve file from " + mw.uri, e)
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "retrieveMediaTitle: fail to resolve file from " + mw.uri, e)
        } finally {
            if (cursor != null && !cursor.isClosed) cursor.close()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        runBackground(Runnable { playlist.delete() })
    }
}
