package org.videolan.vlc.util


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.Medialibrary.MEDIALIB_FOLDER_NAME
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.tools.BitmapCache
import org.videolan.tools.sanitizePath
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.helpers.AudioUtil.readCoverBitmap
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.UiTools
import java.io.File
import kotlin.math.min

object ThumbnailsProvider {

    @Suppress("unused")
    private const val TAG = "VLC/ThumbnailsProvider"

    private var appDir: File? = null
    private var cacheDir: String? = null
    private const val MAX_IMAGES = 4
    private val lock = Any()

    @WorkerThread
    fun getFolderThumbnail(folder: Folder, width: Int): Bitmap? {
        val media = folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, true, true, false,4, 0).filterNotNull()
        return getComposedImage("folder:${folder.mMrl.sanitizePath()}", media, width)
    }

    @WorkerThread
    fun getVideoGroupThumbnail(group: VideoGroup, width: Int): Bitmap? {
        val media = group.media(Medialibrary.SORT_DEFAULT, true, true, false, 4, 0).filterNotNull()
        return getComposedImage("videogroup:${group.title}", media, width)
    }

    @WorkerThread
    fun getMediaThumbnail(item: MediaWrapper, width: Int): Bitmap? {
        return if (isMediaVideo(item))
            getVideoThumbnail(item, width)
        else
            readCoverBitmap(Uri.decode(item.artworkMrl), width)
    }

    fun isMediaVideo(item: MediaWrapper) = item.type == MediaWrapper.TYPE_VIDEO && item.artworkMrl.isNullOrEmpty()

    private fun getMediaThumbnailPath(isMedia: Boolean, item: MediaLibraryItem): String? {
        if (isMedia && isMediaVideo(item as MediaWrapper)) {
            if (item.id == 0L) return item.uri.toString()
            if (appDir == null) appDir = AppContextProvider.appContext.getExternalFilesDir(null)
            val hasCache = appDir != null && appDir!!.exists()
            if (hasCache && cacheDir == null) cacheDir = appDir!!.absolutePath + MEDIALIB_FOLDER_NAME
            return if (hasCache) StringBuilder(cacheDir!!).append('/').append(item.id).append(".jpg").toString() else null
        }
        return item.artworkMrl
    }

    fun getMediaCacheKey(isMedia: Boolean, item: MediaLibraryItem, width: String = "") = if (width.isEmpty()) getMediaThumbnailPath(isMedia, item) else "${getMediaThumbnailPath(isMedia, item)}_$width"

    @WorkerThread
    fun getVideoThumbnail(media: MediaWrapper, width: Int): Bitmap? {
        val filePath = media.uri.path ?: return null
        if (appDir == null) appDir = AppContextProvider.appContext.getExternalFilesDir(null)
        val hasCache = appDir?.exists() == true
        val thumbPath = getMediaThumbnailPath(true, media) ?: return null
        val cacheBM = if (hasCache) BitmapCache.getBitmapFromMemCache(getMediaCacheKey(true, media, width.toString())) else null
        if (cacheBM != null) return cacheBM
        if (hasCache && File(thumbPath).exists()) return readCoverBitmap(thumbPath, width)
        if (media.isThumbnailGenerated) return null
        var bitmap = synchronized(lock) {
            if (media.uri.scheme.isSchemeFile()) ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND) else null
        }
        bitmap?.let { bmp ->
            bmp.config?.let { config ->
                val emptyBitmap = Bitmap.createBitmap(bmp.width, bmp.height, config)
                if (bmp.sameAs(emptyBitmap)) { // myBitmap is empty/blank3
                    bitmap = null
                }
            }
        }
        if (bitmap != null) {
            BitmapCache.addBitmapToMemCache(getMediaCacheKey(true, media, width.toString()), bitmap)
            if (hasCache) {
                media.setThumbnail(thumbPath)
                if (media.id > 0) {
                    BitmapUtil.saveOnDisk(bitmap!!, thumbPath)
                    media.artworkURL = thumbPath
                }
            }
        } else if (media.id != 0L) {
            media.requestThumbnail(width, 0.4f)
        }
        return bitmap
    }

    suspend fun getPlaylistOrGenreImage(key: String, mediaList: List<MediaWrapper>, width: Int, iconAddition: Bitmap? = null): Bitmap? {
        // to force the thumbnail regeneration on change, we append the ids of the media that will be used to the cache key
        val saltedKey = key + getArtworkListForPlaylistOrGenre(mediaList).joinToString("_", ":") { it.id.toString() }
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Salted key from $key is $saltedKey")
        return (BitmapCache.getBitmapFromMemCache(saltedKey) ?: composePlaylistOrGenreImage(mediaList, width, iconAddition))?.also {
            BitmapCache.addBitmapToMemCache(saltedKey, it)
        }
    }

    /**
     * Retrieve the images to be used for the playlist/genre's thumbnail
     * @param mediaList the media list for the playlist or genre
     * @return a sanitied list of media to be used for the playlist thumbnail composition
     */
    private fun getArtworkListForPlaylistOrGenre(mediaList: List<MediaWrapper>):ArrayList<MediaWrapper> {
        if (mediaList.isEmpty()) return arrayListOf()
        val url = mediaList[0].artworkURL
        val isAllSameImage = !mediaList.any { it.artworkURL != url }
        if (isAllSameImage) return arrayListOf(mediaList[0])
        val artworks = ArrayList<MediaWrapper>()
        for (mediaWrapper in mediaList) {

            val artworkAlreadyHere = artworks.any { it.artworkURL == mediaWrapper.artworkURL }

            if (!artworkAlreadyHere && !mediaWrapper.artworkURL.isNullOrBlank()) {
                artworks.add(mediaWrapper)
            }
            if (artworks.size > 3) {
                break
            }
        }

        if (artworks.size == 2) {
            artworks.add(artworks[1])
            artworks.add(artworks[0])
        } else if (artworks.size == 3) {
            artworks.add(artworks[0])
        }
        return artworks
    }

    /**
     * Compose 1 image from tracks of a Playlist or a genre
     * @param mediaList The track list of the playlist or genre
     * @return a Bitmap object
     */
    private suspend fun composePlaylistOrGenreImage(mediaList: List<MediaWrapper>, width: Int, iconAddition: Bitmap?): Bitmap? {
        val artworks = getArtworkListForPlaylistOrGenre(mediaList)
        if (artworks.isEmpty()) return null

        val sameImage = if (artworks.size == 1) obtainBitmap(artworks[0], width)
                ?: return null else null

        val cs = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)
        val comboImage = Canvas(cs)

        if (sameImage != null) {
            /* Scale the cover art, as obtainBitmap may return a larger or smaller image size */
            comboImage.drawBitmap(sameImage, Rect(0, 0, sameImage.width, sameImage.height), Rect(0, 0, width, width), null)
        } else {
            val images = ArrayList<Bitmap>(4)
            artworks.forEach {
                val image = obtainBitmap(it, width / 2)
                if (image != null) {
                    images.add(image)
                }
                if (images.size >= 4) {
                    return@forEach
                }

            }


            for (i in 0..3) {
                if (images.size < i + 1) {
                    images.add(UiTools.getDefaultAudioDrawable(AppContextProvider.appContext).bitmap)
                    /* Place the first image on the diagonal */
                    if (images.size == 3) {
                        images.add(images[0])
                    }
                }
            }

            comboImage.drawBitmap(images[0], Rect(0, 0, images[0].width, images[0].height), Rect(0, 0, width / 2, width / 2), null)
            comboImage.drawBitmap(images[1], Rect(0, 0, images[1].width, images[1].height), Rect(width / 2, 0, width, width / 2), null)
            comboImage.drawBitmap(images[2], Rect(0, 0, images[2].width, images[2].height), Rect(0, width / 2, width / 2, width), null)
            comboImage.drawBitmap(images[3], Rect(0, 0, images[3].width, images[3].height), Rect(width / 2, width / 2, width, width), null)
        }

        iconAddition?.let {
            comboImage.drawBitmap(iconAddition, (comboImage.width.toFloat() - iconAddition.width) / 2, (comboImage.height.toFloat() - iconAddition.height) / 2, null)
        }

        return cs
    }

    suspend fun obtainBitmap(item: MediaLibraryItem, width: Int) = withContext(Dispatchers.IO) {
        when (item) {
            is MediaWrapper -> getMediaThumbnail(item, width)
            is Folder -> getFolderThumbnail(item, width)
            is VideoGroup -> getVideoGroupThumbnail(item, width)
            is Playlist -> getPlaylistOrGenreImage("playlist:${item.id}_$width", item.tracks.toList(), width)
            is Genre -> getPlaylistOrGenreImage("genre:${item.id}_$width", item.tracks.toList(), width)
            else -> readCoverBitmap(Uri.decode(item.artworkMrl), width)
        }
    }


    @WorkerThread
    fun getComposedImage(key: String, mediaList: List<MediaWrapper>, width: Int): Bitmap? {
        var composedImage = BitmapCache.getBitmapFromMemCache(key)
        if (composedImage == null) {
            composedImage = composeImage(mediaList, width)
            if (composedImage != null) BitmapCache.addBitmapToMemCache(key, composedImage)
        }
        return composedImage
    }

    /**
     * Compose 1 image from combined media thumbnails
     * @param mediaList The media list from which will extract thumbnails
     * @return a Bitmap object
     */
    private fun composeImage(mediaList: List<MediaWrapper>, imageWidth: Int): Bitmap? {
        val sourcesImages = arrayOfNulls<Bitmap>(min(MAX_IMAGES, mediaList.size))
        var count = 0
        var minWidth = Integer.MAX_VALUE
        var minHeight = Integer.MAX_VALUE
        for (media in mediaList) {
            val bm = getVideoThumbnail(media, imageWidth)
            if (bm != null) {
                val width = bm.width
                val height = bm.height
                sourcesImages[count++] = bm
                minWidth = min(minWidth, width)
                minHeight = min(minHeight, height)
                if (count == MAX_IMAGES) break
            }
        }
        if (count == 0) return null

        return if (count == 1) sourcesImages[0] else composeCanvas(sourcesImages.filterNotNull().toTypedArray(), count, minWidth, minHeight)
    }

    private fun composeCanvas(sourcesImages: Array<Bitmap>, count: Int, minWidth: Int, minHeight: Int): Bitmap {
        val overlayWidth: Int
        val overlayHeight: Int
        when (count) {
            4 -> {
                overlayWidth = 2 * minWidth
                overlayHeight = 2 * minHeight
            }
            else -> {
                overlayWidth = minWidth
                overlayHeight = minHeight
            }
        }
        val bmOverlay = Bitmap.createBitmap(overlayWidth, overlayHeight, sourcesImages[0].config!!)

        val canvas = Canvas(bmOverlay)
        when (count) {
            2 -> {
                for (i in 0 until count)
                    sourcesImages[i] = BitmapUtil.centerCrop(sourcesImages[i], minWidth / 2, minHeight)
                canvas.drawBitmap(sourcesImages[0], 0f, 0f, null)
                canvas.drawBitmap(sourcesImages[1], (minWidth / 2).toFloat(), 0f, null)
            }
            3 -> {
                sourcesImages[0] = BitmapUtil.centerCrop(sourcesImages[0], minWidth / 2, minHeight / 2)
                sourcesImages[1] = BitmapUtil.centerCrop(sourcesImages[1], minWidth / 2, minHeight / 2)
                sourcesImages[2] = BitmapUtil.centerCrop(sourcesImages[2], minWidth, minHeight / 2)
                canvas.drawBitmap(sourcesImages[0], 0f, 0f, null)
                canvas.drawBitmap(sourcesImages[1], (minWidth / 2).toFloat(), 0f, null)
                canvas.drawBitmap(sourcesImages[2], 0f, (minHeight / 2).toFloat(), null)
            }
            4 -> {
                for (i in 0 until count)
                    sourcesImages[i] = BitmapUtil.centerCrop(sourcesImages[i], minWidth, minHeight)
                canvas.drawBitmap(sourcesImages[0], 0f, 0f, null)
                canvas.drawBitmap(sourcesImages[1], minWidth.toFloat(), 0f, null)
                canvas.drawBitmap(sourcesImages[2], 0f, minHeight.toFloat(), null)
                canvas.drawBitmap(sourcesImages[3], minWidth.toFloat(), minHeight.toFloat(), null)
            }
        }
        return bmOverlay
    }
}
