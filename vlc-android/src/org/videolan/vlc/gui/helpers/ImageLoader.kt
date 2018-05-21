package org.videolan.vlc.gui.helpers

import android.databinding.BindingAdapter
import android.databinding.DataBindingUtil
import android.databinding.OnRebindCallback
import android.databinding.ViewDataBinding
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.support.annotation.MainThread
import android.support.v17.leanback.widget.ImageCardView
import android.support.v4.view.ViewCompat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BR
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.HttpImageLoader
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.VLCIO

private val sBitmapCache = BitmapCache.getInstance()
private val sMedialibrary = VLCApplication.getMLInstance()

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
    val cacheKey = if (isGroup) "group: ${item.title}" else ThumbnailsProvider.getMediaCacheKey(isMedia, item)
    val bitmap = if (cacheKey !== null) sBitmapCache.getBitmapFromMemCache(cacheKey) else null
    if (bitmap !== null) updateImageView(bitmap, v, binding)
    else launch(UI, CoroutineStart.UNDISPATCHED) { getImage(v, findInLibrary(item, isMedia, isGroup), binding) }
}

@BindingAdapter("imageUri")
fun downloadIcon(v: View, imageUri: Uri?) {
    if (imageUri != null && imageUri.scheme == "http") launch(UI, CoroutineStart.UNDISPATCHED) {
        val image = withContext(VLCIO) { HttpImageLoader.downloadBitmap(imageUri.toString()) }
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

private suspend fun obtainBitmap(item: MediaLibraryItem, width: Int) = withContext(VLCIO) {
    if (item.itemType == MediaLibraryItem.TYPE_MEDIA) ThumbnailsProvider.getMediaThumbnail(item as MediaWrapper, width)
    else AudioUtil.readCoverBitmap(Uri.decode(item.artworkMrl), width)
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
            ViewCompat.setBackground(target, BitmapDrawable(VLCApplication.getAppResources(), bitmap))
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
        if (isMediaFile && "file" == uri.scheme) return withContext(VLCIO) { sMedialibrary.getMedia(uri) } ?: item
    }
    return item
}