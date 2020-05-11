package org.videolan.vlc.gui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.Extensions
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.TAG_ITEM
import org.videolan.resources.VLCInstance
import org.videolan.tools.readableFileSize
import org.videolan.vlc.R
import org.videolan.vlc.databinding.InfoActivityBinding
import org.videolan.vlc.gui.browser.PathAdapter
import org.videolan.vlc.gui.browser.PathAdapterListener
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.FloatingActionButtonBehavior
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.video.MediaInfoAdapter
import org.videolan.vlc.gui.view.VLCDividerItemDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.browser.IPathOperationDelegate
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate
import java.io.File
import java.util.*

private const val TAG = "VLC/InfoActivity"
private const val TAG_FAB_VISIBILITY = "FAB"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class InfoActivity : AudioPlayerContainerActivity(), View.OnClickListener, PathAdapterListener,
        IPathOperationDelegate by PathOperationDelegate()
{

    private lateinit var item: MediaLibraryItem
    private lateinit var adapter: MediaInfoAdapter
    private lateinit var model: InfoModel

    internal lateinit var binding: InfoActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.info_activity)

        initAudioPlayerContainerActivity()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val item = if (savedInstanceState != null)
            savedInstanceState.getParcelable<Parcelable>(TAG_ITEM) as MediaLibraryItem?
        else
            intent.getParcelableExtra<Parcelable>(TAG_ITEM) as MediaLibraryItem
        if (item == null) {
            finish()
            return
        }
        this.item = item
        if (item.id == 0L) {
            val libraryItem = Medialibrary.getInstance().getMedia((item as MediaWrapper).uri)
            if (libraryItem != null)
                this.item = libraryItem
        }
        binding.item = item
        val fabVisibility = savedInstanceState?.getInt(TAG_FAB_VISIBILITY) ?: -1

        model = getModel()

        binding.fab.setOnClickListener(this)
        if (item is MediaWrapper) {
            adapter = MediaInfoAdapter()
            binding.list.layoutManager = LinearLayoutManager(binding.root.context)
            binding.list.adapter = adapter
            if (model.sizeText.value === null) model.checkFile(item)
            if (model.mediaTracks.value === null) model.parseTracks(this, item)
        }
        model.hasSubs.observe(this, Observer { if (it) binding.infoSubtitles.visibility = View.VISIBLE })
        model.mediaTracks.observe(this, Observer { adapter.setTracks(it) })
        model.sizeText.observe(this, Observer { binding.sizeValueText = it })
        model.cover.observe(this, Observer {
            if (it != null) {
                binding.cover = BitmapDrawable(this@InfoActivity.resources, it)
                lifecycleScope.launch {
                    ViewCompat.setNestedScrollingEnabled(binding.container, true)
                    binding.appbar.setExpanded(true, true)
                    if (fabVisibility != -1) binding.fab.visibility = fabVisibility
                }
            } else noCoverFallback()
        })
        if (model.cover.value === null) model.getCover(item.artworkMrl, getScreenWidth())
        updateMeta()
        binding.directoryNotScannedButton.setOnClickListener {
            val media = item as MediaWrapper
            val parent = media.uri.toString().substring(0, media.uri.toString().lastIndexOf("/"))
            MedialibraryUtils.addDir(parent, applicationContext)
            Snackbar.make(binding.root, getString(R.string.scanned_directory_added, Uri.parse(parent).lastPathSegment), Snackbar.LENGTH_LONG).show()
            binding.scanned = true
        }
    }

    private fun updateMeta() = lifecycleScope.launchWhenStarted {
        var length = 0L
        val tracks = item.tracks
        val nbTracks = tracks?.size ?: 0
        if (nbTracks > 0) for (media in tracks!!) length += media.length
        if (length > 0)
            binding.length = Tools.millisToTextLarge(length)

        if (item is MediaWrapper) {
            val media = item as MediaWrapper
            val resolution = generateResolutionClass(media.width, media.height)
            binding.resolution = resolution
        }

        binding.scanned = true
        when {
            item.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                val media = item as MediaWrapper
                binding.progress = if (media.length == 0L) 0 else (100.toLong() * media.time / length).toInt()
                binding.sizeTitleText = getString(R.string.file_size)



                if (isSchemeSupported(media.uri?.scheme)) {
                    binding.ariane.visibility = View.VISIBLE
                    binding.ariane.layoutManager = LinearLayoutManager(this@InfoActivity, LinearLayoutManager.HORIZONTAL, false)
                    binding.ariane.adapter = PathAdapter(this@InfoActivity, media)
                    if (binding.ariane.itemDecorationCount == 0) {
                        binding.ariane.addItemDecoration(VLCDividerItemDecoration(this@InfoActivity, DividerItemDecoration.HORIZONTAL, ContextCompat.getDrawable(this@InfoActivity, R.drawable.ic_divider)!!))
                    }
                    //scheme is supported => test if the parent is scanned
                    var isScanned = false
                    Medialibrary.getInstance().foldersList.forEach search@{
                        if (media.uri.toString().startsWith(Uri.parse(it).toString())) {
                            isScanned = true
                            return@search
                        }
                    }
                    binding.scanned = isScanned
                } else binding.ariane.visibility = View.GONE
            }
            item.itemType == MediaLibraryItem.TYPE_ARTIST -> {
                val albums = (item as Artist).albums
                val nbAlbums = albums?.size ?: 0
                binding.sizeTitleText = getString(R.string.albums)
                binding.sizeValueText = nbAlbums.toString()
                binding.sizeIcon.setImageDrawable(ContextCompat.getDrawable(this@InfoActivity, R.drawable.ic_album))
                binding.extraTitleText = getString(R.string.tracks)
                binding.extraValueText = nbTracks.toString()
                binding.extraIcon.setImageDrawable(ContextCompat.getDrawable(this@InfoActivity, R.drawable.ic_song))
            }
            else -> {
                binding.sizeTitleText = getString(R.string.tracks)
                binding.sizeValueText = nbTracks.toString()
                binding.sizeIcon.setImageDrawable(ContextCompat.getDrawable(this@InfoActivity, R.drawable.ic_song))
            }
        }
    }

    override fun backTo(tag: String) {
    }

    override fun currentContext() = this

    override fun showRoot() = false

    override fun getPathOperationDelegate() = this

    override fun onPostCreate(savedInstanceState: Bundle?) {
        fragmentContainer = binding.container
        super.onPostCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(TAG_ITEM, item)
        outState.putInt(TAG_FAB_VISIBILITY, binding.fab.visibility)
    }

    private fun noCoverFallback() {
        binding.appbar.setExpanded(false)
        ViewCompat.setNestedScrollingEnabled(binding.list, false)
        val lp = binding.fab.layoutParams as CoordinatorLayout.LayoutParams
        lp.anchorId = binding.container.id
        lp.anchorGravity = Gravity.BOTTOM or Gravity.END
        lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.default_margin)
        lp.behavior = FloatingActionButtonBehavior(this@InfoActivity, null)
        binding.fab.layoutParams = lp
        binding.fab.show()
    }

    override fun onClick(v: View) {
        MediaUtils.playTracks(this, item, 0)
        finish()
    }

    override fun onPlayerStateChanged(bottomSheet: View, newState: Int) {
        val visibility = binding.fab.visibility
        if (visibility == View.VISIBLE && newState != BottomSheetBehavior.STATE_COLLAPSED && newState != BottomSheetBehavior.STATE_HIDDEN)
            binding.fab.hide()
        else if (visibility == View.INVISIBLE && (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN))
            binding.fab.show()
    }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class InfoModel : ViewModel() {

    internal val hasSubs = MutableLiveData<Boolean>()
    internal val mediaTracks = MutableLiveData<List<IMedia.Track>>()
    internal val sizeText = MutableLiveData<String>()
    internal val cover = MutableLiveData<Bitmap>()
    internal val mMediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory

    internal fun getCover(mrl: String?, width: Int) = viewModelScope.launch {
        cover.value = mrl?.let { withContext(Dispatchers.IO) { AudioUtil.fetchCoverBitmap(Uri.decode(it), width) } }
    }

    internal fun parseTracks(context: Context, mw: MediaWrapper) = viewModelScope.launch {
        val media = withContext(Dispatchers.IO) {
            val libVlc = VLCInstance.getInstance(context)
            mMediaFactory.getFromUri(libVlc, mw.uri).apply { parse() }
        }
        if (!isActive) return@launch
        var subs = false
        val trackCount = media.trackCount
        val tracks = LinkedList<IMedia.Track>()
        for (i in 0 until trackCount) {
            val track = media.getTrack(i)
            tracks.add(track)
            subs = subs or (track.type == IMedia.Track.Type.Text)
        }
        media.release()
        hasSubs.value = subs
        mediaTracks.value = tracks.toList()
    }

    internal fun checkFile(mw: MediaWrapper) = viewModelScope.launch {
        val itemFile = withContext(Dispatchers.IO) { File(Uri.decode(mw.location.substring(5))) }

        if (!withContext(Dispatchers.IO) { itemFile.exists() } || !isActive) return@launch
        if (mw.type == MediaWrapper.TYPE_VIDEO) checkSubtitles(itemFile)
        sizeText.value = itemFile.length().readableFileSize()
    }

    private suspend fun checkSubtitles(itemFile: File) = withContext(Dispatchers.IO) {
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
            if (!subFolder.exists()) continue
            val subFiles = subFolder.list()
            var subFilesLength = 0
            var newFiles = arrayOfNulls<String>(0)
            if (subFiles != null) {
                subFilesLength = subFiles.size
                newFiles = arrayOfNulls(filesLength + subFilesLength)
                System.arraycopy(subFiles, 0, newFiles, 0, subFilesLength)
            }
            files?.let { System.arraycopy(it, 0, newFiles, subFilesLength, filesLength) }
            files = newFiles.filterNotNull().toTypedArray()
            filesLength = files.size
        }
        if (files != null) for (i in 0 until filesLength) {
            filename = Uri.decode(files[i])
            val index = filename.lastIndexOf('.')
            if (index <= 0) continue
            extension = filename.substring(index)
            if (!Extensions.SUBTITLES.contains(extension)) continue
            if (!isActive) return@withContext
            if (filename.startsWith(videoName)) {
                hasSubs.postValue(true)
                return@withContext
            }
        }
    }
}
