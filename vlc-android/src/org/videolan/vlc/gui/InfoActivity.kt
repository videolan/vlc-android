package org.videolan.vlc.gui


import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Message
import android.os.Parcelable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.Extensions
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.InfoActivityBinding
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.FloatingActionButtonBehavior
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.MediaInfoAdapter
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class InfoActivity : AudioPlayerContainerActivity(), View.OnClickListener {

    private var mItem: MediaLibraryItem? = null
    private var mAdapter: MediaInfoAdapter? = null
    private var mParseTracksTask: ParseTracksTask? = null
    private var mCheckFileTask: CheckFileTask? = null

    internal lateinit var mBinding: InfoActivityBinding

    private val mHandler = MediaInfoHandler(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = DataBindingUtil.setContentView(this, R.layout.info_activity)

        initAudioPlayerContainerActivity()
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        mItem = if (savedInstanceState != null)
            savedInstanceState.getParcelable<Parcelable>(TAG_ITEM) as MediaLibraryItem?
        else
            intent.getParcelableExtra<Parcelable>(TAG_ITEM) as MediaLibraryItem
        if (mItem == null) {
            finish()
            return
        }
        if (mItem!!.id == 0L) {
            val libraryItem = VLCApplication.mlInstance.getMedia((mItem as MediaWrapper).uri)
            if (libraryItem != null)
                mItem = libraryItem
        }
        mBinding.item = mItem
        val fabVisibility = savedInstanceState?.getInt(TAG_FAB_VISIBILITY) ?: -1

        if (!TextUtils.isEmpty(mItem!!.artworkMrl)) {
            runIO(Runnable {
                val cover = AudioUtil.readCoverBitmap(Uri.decode(mItem!!.artworkMrl), this@InfoActivity.getScreenWidth())
                if (cover != null) {
                    mBinding.cover = BitmapDrawable(this@InfoActivity.resources, cover)
                    runOnMainThread(Runnable {
                        ViewCompat.setNestedScrollingEnabled(mBinding.container, true)
                        mBinding.appbar.setExpanded(true, true)
                        if (fabVisibility != -1)
                            mBinding.fab.visibility = fabVisibility
                    })
                } else
                    noCoverFallback()
            })
        } else
            noCoverFallback()
        mBinding.fab.setOnClickListener(this)
        if (mItem!!.itemType == MediaLibraryItem.TYPE_MEDIA) {
            mAdapter = MediaInfoAdapter()
            mBinding.list.layoutManager = LinearLayoutManager(mBinding.root.context)
            mBinding.list.adapter = mAdapter
            mCheckFileTask = CheckFileTask().execute() as CheckFileTask
            mParseTracksTask = ParseTracksTask().execute() as ParseTracksTask
        }
        runBackground(Runnable { updateMeta() })
    }

    private fun updateMeta() {
        var length = 0L
        val tracks = mItem!!.tracks
        val nbTracks = tracks?.size ?: 0
        if (nbTracks > 0)
            for (media in tracks!!)
                length += media.length
        if (length > 0)
            mBinding.length = Tools.millisToText(length)

        when {
            mItem!!.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                val media = mItem as MediaWrapper?
                mBinding.path = Uri.decode(media!!.uri.path)
                mBinding.progress = if (media.length == 0L) 0 else (100.toLong() * media.time / length).toInt()
                mBinding.sizeTitleText = getString(R.string.file_size)
            }
            mItem!!.itemType == MediaLibraryItem.TYPE_ARTIST -> {
                val albums = (mItem as Artist).albums
                val nbAlbums = albums?.size ?: 0
                mBinding.sizeTitleText = getString(R.string.albums)
                mBinding.sizeValueText = nbAlbums.toString()
                mBinding.extraTitleText = getString(R.string.tracks)
                mBinding.extraValueText = nbTracks.toString()
            }
            else -> {
                mBinding.sizeTitleText = getString(R.string.tracks)
                mBinding.sizeValueText = nbTracks.toString()
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        fragmentContainer = mBinding.container
        super.onPostCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(TAG_ITEM, mItem)
        outState.putInt(TAG_FAB_VISIBILITY, mBinding.fab.visibility)
    }

    override fun onStop() {
        super.onStop()
        if (mCheckFileTask != null)
            mCheckFileTask!!.cancel(true)
        if (mParseTracksTask != null)
            mParseTracksTask!!.cancel(true)
    }

    private fun noCoverFallback() {
        mBinding.appbar.setExpanded(false)
        ViewCompat.setNestedScrollingEnabled(mBinding.list, false)
        val lp = mBinding.fab.layoutParams as CoordinatorLayout.LayoutParams
        lp.anchorId = mBinding.container.id
        lp.anchorGravity = Gravity.BOTTOM or Gravity.RIGHT or Gravity.END
        lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.default_margin)
        lp.behavior = FloatingActionButtonBehavior(this@InfoActivity, null)
        mBinding.fab.layoutParams = lp
        mBinding.fab.show()
    }

    override fun onClick(v: View) {
        MediaUtils.playTracks(this, mItem!!, 0)
        finish()
    }

    override fun onPlayerStateChanged(bottomSheet: View, newState: Int) {
        val visibility = mBinding.fab.visibility
        if (visibility == View.VISIBLE && newState != BottomSheetBehavior.STATE_COLLAPSED && newState != BottomSheetBehavior.STATE_HIDDEN)
            mBinding.fab.hide()
        else if (visibility == View.INVISIBLE && (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN))
            mBinding.fab.show()
    }

    private inner class CheckFileTask : AsyncTask<Void, Void, Void>() {

        private fun checkSubtitles(itemFile: File) {
            var extension: String
            var filename: String
            var videoName = Uri.decode(itemFile.name)
            val parentPath = Uri.decode(itemFile.parent)
            videoName = videoName.substring(0, videoName.lastIndexOf('.'))
            val subFolders = arrayOf("/Subtitles", "/subtitles", "/Subs", "/subs")
            var files: Array<String>? = itemFile.parentFile.list()
            var filesLength = files?.size ?: 0
            for (subFolderName in subFolders) {
                val subFolder = File(parentPath + subFolderName)
                if (!subFolder.exists())
                    continue
                val subFiles = subFolder.list()
                var subFilesLength = 0
                var newFiles = arrayOfNulls<String>(0)
                if (subFiles != null) {
                    subFilesLength = subFiles.size
                    newFiles = arrayOfNulls(filesLength + subFilesLength)
                    System.arraycopy(subFiles, 0, newFiles, 0, subFilesLength)
                }
                if (files != null)
                    System.arraycopy(files, 0, newFiles, subFilesLength, filesLength)



                files = newFiles.filterNotNull().toTypedArray()
                filesLength = files.size
            }
            if (files != null)
                for (i in 0 until filesLength) {
                    filename = Uri.decode(files[i])
                    val index = filename.lastIndexOf('.')
                    if (index <= 0)
                        continue
                    extension = filename.substring(index)
                    if (!Extensions.SUBTITLES.contains(extension))
                        continue

                    if (isCancelled)
                        return
                    if (filename.startsWith(videoName)) {
                        mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget()
                        return
                    }
                }
        }

        override fun doInBackground(vararg params: Void): Void? {
            val itemFile = File(Uri.decode((mItem as MediaWrapper).location.substring(5)))

            if (!itemFile.exists() || isCancelled)
                return null
            if ((mItem as MediaWrapper).type == MediaWrapper.TYPE_VIDEO)
                checkSubtitles(itemFile)
            mBinding.sizeValueText = itemFile.length().readableFileSize()
            return null
        }

        override fun onPostExecute(aVoid: Void) {
            mCheckFileTask = null
        }

        override fun onCancelled() {
            mCheckFileTask = null
        }
    }

    private inner class ParseTracksTask : AsyncTask<Void, Void, Media>() {
        internal val context = applicationContext

        override fun doInBackground(vararg params: Void): Media? {

            val libVlc = VLCInstance.get(context)
            if (libVlc == null || isCancelled) return null

            val media = Media(libVlc, (mItem as MediaWrapper).uri)
            media.parse()

            return media
        }

        override fun onPostExecute(media: Media?) {
            mParseTracksTask = null
            if (media == null || isCancelled) return
            var hasSubs = false
            val trackCount = media.trackCount
            val tracks = LinkedList<Media.Track>()
            for (i in 0 until trackCount) {
                val track = media.getTrack(i)
                tracks.add(track)
                hasSubs = hasSubs or (track.type == Media.Track.Type.Text)
            }
            media.release()
            mAdapter!!.setTracks(tracks)
            if (hasSubs) mBinding.infoSubtitles.visibility = View.VISIBLE
        }

        override fun onCancelled() {
            mParseTracksTask = null
        }
    }

    private class MediaInfoHandler internal constructor(owner: InfoActivity) : WeakHandler<InfoActivity>(owner) {

        override fun handleMessage(msg: Message) {
            val activity = owner ?: return

            when (msg.what) {
                EXIT -> {
                    activity.setResult(PreferencesActivity.RESULT_RESCAN)
                    activity.finish()
                }
                SHOW_SUBTITLES -> activity.mBinding.infoSubtitles.visibility = View.VISIBLE
            }
        }
    }

    companion object {

        const val TAG = "VLC/InfoActivity"
        const val TAG_ITEM = "ML_ITEM"
        const val TAG_FAB_VISIBILITY = "FAB"
        private const val EXIT = 2
        private const val SHOW_SUBTITLES = 3
    }
}
