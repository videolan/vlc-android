package org.videolan.vlc.gui.helpers

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.OnRebindCallback
import androidx.databinding.ViewDataBinding
import androidx.leanback.widget.ImageCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.Folder
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BR
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.HttpImageLoader
import org.videolan.vlc.util.ThumbnailsProvider

private val sBitmapCache = BitmapCache.getInstance()
private val sMedialibrary = VLCApplication.getMLInstance()
private const val TAG = "ImageLoader"

@MainThread
@BindingAdapter("media")
fun loadImage(v: View, item: MediaLibraryItem?) {
    if (item === null
            || item.itemType == MediaLibraryItem.TYPE_GENRE
            || item.itemType == MediaLibraryItem.TYPE_PLAYLIST)
        return
    val binding = DataBindingUtil.findBinding<ViewDataBinding>(v)
    val isMedia = item.itemType == MediaLibraryItem.TYPE_MEDIA
    val isGroup = isMedia && (item as MediaWrapper).type == MediaWrapper.TYPE_GROUP
    val isFolder = !isMedia && item.itemType == MediaLibraryItem.TYPE_FOLDER;
    val cacheKey = when {
        isGroup -> "group:${item.title}"
        isFolder -> "folder:${item.title}"
        else -> ThumbnailsProvider.getMediaCacheKey(isMedia, item)
    }
    val bitmap = if (cacheKey !== null) sBitmapCache.getBitmapFromMemCache(cacheKey) else null
    if (bitmap !== null) updateImageView(bitmap, v, binding)
    else AppScope.launch { getImage(v, findInLibrary(item, isMedia, isGroup), binding) }
}

@BindingAdapter("imageUri")
fun downloadIcon(v: View, imageUri: Uri?) {
    if (imageUri != null && imageUri.scheme == "http") AppScope.launch {
        val image = withContext(Dispatchers.IO) { HttpImageLoader.downloadBitmap(imageUri.toString()) }
        updateImageView(image, v, DataBindingUtil.findBinding(v))
    }
}

private suspend fun getImage(v: View, item: MediaLibraryItem, binding: ViewDataBinding?) {
    var bindChanged = false
    val rebindCallbacks = if (binding !== null) object : OnRebindCallback<ViewDataBinding>() {
        override fun onPreBind(binding: ViewDataBinding): Boolean {
            bindChanged = true
            return super.onPreBind(binding)
        }
    } else null
    if (binding !== null) {
        binding.executePendingBindings()
        binding.addOnRebindCallback(rebindCallbacks!!)
    }
    val image = if (!bindChanged) obtainBitmap(item, v.width) else null
    if (!bindChanged) updateImageView(image, v, binding)
    binding?.removeOnRebindCallback(rebindCallbacks!!)
}

private suspend fun obtainBitmap(item: MediaLibraryItem, width: Int) = withContext(Dispatchers.IO) {
    when {
        item is MediaWrapper -> ThumbnailsProvider.getMediaThumbnail(item, width)
        item is Folder -> ThumbnailsProvider.getFolderThumbnail(item, width)
        else -> AudioUtil.readCoverBitmap(Uri.decode(item.artworkMrl), width)
    }
}

@MainThread
fun updateImageView(bitmap: Bitmap?, target: View, vdb: ViewDataBinding?) {
    if (bitmap === null || bitmap.width <= 1 || bitmap.height <= 1) return
    if (vdb !== null) {
        vdb.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER)
        vdb.setVariable(BR.cover, BitmapDrawable(target.resources, bitmap))
        vdb.setVariable(BR.protocol, null)
    } else when (target) {
        is ImageView -> {
            target.scaleType = ImageView.ScaleType.FIT_CENTER
            target.setImageBitmap(bitmap)
            target.visibility = View.VISIBLE
        }
        is TextView -> {
            ViewCompat.setBackground(target, BitmapDrawable(target.context.resources, bitmap))
            target.text = null
        }
        is ImageCardView -> {
            target.mainImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            target.mainImage = BitmapDrawable(target.getResources(), bitmap)
        }
    }
}

private suspend fun findInLibrary(item: MediaLibraryItem, isMedia: Boolean, isGroup: Boolean) : MediaLibraryItem {
    if (isMedia && !isGroup && item.id == 0L) {
        val mw = item as MediaWrapper
        val type = mw.type
        val isMediaFile = type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO
        val uri = mw.uri
        if (!isMediaFile && !(type == MediaWrapper.TYPE_DIR && "upnp" == uri.scheme)) return item
        if (isMediaFile && "file" == uri.scheme) return withContext(Dispatchers.IO) { sMedialibrary.getMedia(uri) } ?: item
    }
    return item
}