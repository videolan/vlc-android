package org.videolan.vlc.util


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Medialibrary.THUMBS_FOLDER_NAME
import org.videolan.medialibrary.media.Folder
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.AudioUtil.readCoverBitmap
import org.videolan.vlc.gui.helpers.BitmapCache
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaGroup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList

object ThumbnailsProvider {

    private val TAG = "VLC/ThumbnailsProvider"

    private var appDir: File? = null
    private var cacheDir: String? = null
    private const val MAX_IMAGES = 4
    private val lock = Any()

    @WorkerThread
    fun getFolderThumbnail(folder: Folder, width: Int): Bitmap {
        val media = Arrays.asList(*folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, true, 4, 0))
        return getComposedImage("folder:" + folder.title, media, width)
    }

    @WorkerThread
    fun getMediaThumbnail(item: MediaWrapper, width: Int): Bitmap? {
        if (item.type == MediaWrapper.TYPE_GROUP) return ThumbnailsProvider.getComposedImage("group:" + item.title, (item as MediaGroup).all, width)
        return if (item.type == MediaWrapper.TYPE_VIDEO && TextUtils.isEmpty(item.artworkMrl))
            getVideoThumbnail(item, width)
        else
            AudioUtil.readCoverBitmap(Uri.decode(item.artworkMrl), width)
    }

    fun getMediaCacheKey(isMedia: Boolean, item: MediaLibraryItem): String? {
        if (isMedia && (item as MediaWrapper).type == MediaWrapper.TYPE_VIDEO && TextUtils.isEmpty(item.getArtworkMrl())) {
            if (appDir == null) appDir = VLCApplication.getAppContext().getExternalFilesDir(null)
            val hasCache = appDir != null && appDir!!.exists()
            if (hasCache && cacheDir == null) cacheDir = appDir!!.absolutePath + THUMBS_FOLDER_NAME
            return if (hasCache) StringBuilder(cacheDir!!).append('/').append(item.getTitle()).append(".jpg").toString() else null
        }
        return item.artworkMrl
    }

    @WorkerThread
    private fun getVideoThumbnail(media: MediaWrapper, width: Int): Bitmap? {
        val filePath = media.uri.path
        if (appDir == null) appDir = VLCApplication.getAppContext().getExternalFilesDir(null)
        val hasCache = appDir != null && appDir!!.exists()
        val thumbPath = getMediaCacheKey(true, media)
        val cacheBM = if (hasCache) BitmapCache.getInstance().getBitmapFromMemCache(thumbPath) else null
        if (cacheBM != null) return cacheBM
        if (hasCache && File(thumbPath).exists()) return readCoverBitmap(thumbPath, width)
        if (media.isThumbnailGenerated) return null
        val bitmap: Bitmap?
        synchronized(lock) {
            bitmap = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND)
        }
        if (bitmap != null) {
            BitmapCache.getInstance().addBitmapToMemCache(thumbPath, bitmap)
            if (hasCache) {
                media.setThumbnail(thumbPath)
                saveOnDisk(bitmap, thumbPath)
                media.artworkURL = thumbPath
            }
        } else if (media.id != 0L) {
            Medialibrary.getInstance().requestThumbnail(media.id)
        }
        return bitmap
    }

    suspend fun getPlaylistImage(key: String, mediaList: List<MediaWrapper>, width: Int): Bitmap? {
        val bmc = BitmapCache.getInstance()
        var composedImage = bmc.getBitmapFromMemCache(key)
        if (composedImage == null) {
            composedImage = composePlaylistImage(mediaList, width)
            if (composedImage != null) bmc.addBitmapToMemCache(key, composedImage)
        }
        return composedImage
    }

    /**
     * Compose 1 image from tracks of a Playlist
     * @param mediaList The track list of the playlist
     * @return a Bitmap object
     */
    private suspend fun composePlaylistImage(mediaList: List<MediaWrapper>, width: Int): Bitmap? {
        val url = mediaList[0].artworkURL
        val isAllSameImage = !mediaList.any { it.artworkURL != url }

        if (isAllSameImage) {

            return obtainBitmap(mediaList[0], width)
        }

        val artworks = ArrayList<MediaWrapper>()
        for (mediaWrapper in mediaList) {

            val artworkAlreadyHere = artworks.any { it.artworkURL == mediaWrapper.artworkURL }

            if (mediaWrapper.artworkURL != null && mediaWrapper.artworkURL.isNotBlank() && !artworkAlreadyHere) {
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
                images.add(UiTools.getDefaultAudioDrawable(VLCApplication.getAppContext()).bitmap)
            }
        }

        val cs = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)
        val comboImage = Canvas(cs)
        comboImage.drawBitmap(images[0], Rect(0, 0, images[0].width, images[0].height), Rect(0, 0, width / 2, width / 2), null)
        comboImage.drawBitmap(images[1], Rect(0, 0, images[1].width, images[1].height), Rect(width / 2, 0, width, width / 2), null)
        comboImage.drawBitmap(images[2], Rect(0, 0, images[2].width, images[2].height), Rect(0, width / 2, width / 2, width), null)
        comboImage.drawBitmap(images[3], Rect(0, 0, images[3].width, images[3].height), Rect(width / 2, width / 2, width, width), null)

        return cs
    }

    suspend fun obtainBitmap(item: MediaLibraryItem, width: Int) = withContext(Dispatchers.IO) {
        when (item) {
            is MediaWrapper -> getMediaThumbnail(item, width)
            is Folder -> getFolderThumbnail(item, width)
            else -> readCoverBitmap(Uri.decode(item.artworkMrl), width)
        }
    }


    @WorkerThread
    fun getComposedImage(key: String, mediaList: List<MediaWrapper>, width: Int): Bitmap {
        val bmc = BitmapCache.getInstance()
        var composedImage = bmc.getBitmapFromMemCache(key)
        if (composedImage == null) {
            composedImage = composeImage(mediaList, width)
            if (composedImage != null) bmc.addBitmapToMemCache(key, composedImage)
        }
        return composedImage
    }

    /**
     * Compose 1 image from combined media thumbnails
     * @param mediaList The media list from which will extract thumbnails
     * @return a Bitmap object
     */
    private fun composeImage(mediaList: List<MediaWrapper>, imageWidth: Int): Bitmap? {
        val sourcesImages = arrayOfNulls<Bitmap>(Math.min(MAX_IMAGES, mediaList.size))
        var count = 0
        var minWidth = Integer.MAX_VALUE
        var minHeight = Integer.MAX_VALUE
        for (media in mediaList) {
            val bm = getVideoThumbnail(media, imageWidth)
            if (bm != null) {
                val width = bm.width
                val height = bm.height
                sourcesImages[count++] = bm
                minWidth = Math.min(minWidth, width)
                minHeight = Math.min(minHeight, height)
                if (count == MAX_IMAGES) break
            }
        }
        if (count == 0) return null
        val sources = Array(sourcesImages.size) {
            sourcesImages[it]!!
        }
        return if (count == 1) sourcesImages[0] else composeCanvas(sources, count, minWidth, minHeight)
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
        val bmOverlay = Bitmap.createBitmap(overlayWidth, overlayHeight, sourcesImages[0].config)

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

    private fun saveOnDisk(bitmap: Bitmap, destPath: String?) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(destPath)
            fos.write(byteArray)
        } catch (e: java.io.IOException) {
            e.printStackTrace()
        } finally {
            Util.close(fos)
            Util.close(stream)
        }
    }
}
