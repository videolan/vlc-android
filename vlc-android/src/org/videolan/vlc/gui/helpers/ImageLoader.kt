package org.videolan.vlc.gui.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.OnRebindCallback
import androidx.databinding.ViewDataBinding
import androidx.leanback.widget.ImageCardView
import kotlinx.coroutines.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BR
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.AudioBrowserTvItemBinding
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.HttpImageLoader
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.ThumbnailsProvider.obtainBitmap

private val sMedialibrary = VLCApplication.mlInstance
@Volatile
private var defaultImageWidth = 0
private const val TAG = "ImageLoader"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@MainThread
@BindingAdapter("media")
fun loadImage(v: View, item: MediaLibraryItem?) {
    if (item === null
            || item.itemType == MediaLibraryItem.TYPE_PLAYLIST)
        return
    val binding = DataBindingUtil.findBinding<ViewDataBinding>(v)
    if (item.itemType == MediaLibraryItem.TYPE_GENRE && !isForTV(binding)) {
        return
    }
    val isMedia = item.itemType == MediaLibraryItem.TYPE_MEDIA
    if (isMedia && (item as MediaWrapper).type == MediaWrapper.TYPE_VIDEO && !VLCApplication.showVideoThumbs) {
        updateImageView(UiTools.getDefaultVideoDrawable(v.context).bitmap, v, binding)
        return
    }
    val isGroup = isMedia && (item as MediaWrapper).type == MediaWrapper.TYPE_GROUP
    val isFolder = !isMedia && item.itemType == MediaLibraryItem.TYPE_FOLDER;
    val cacheKey = when {
        isGroup -> "group:${item.title}"
        isFolder -> "folder:${item.title}"
        else -> ThumbnailsProvider.getMediaCacheKey(isMedia, item)
    }
    val bitmap = if (cacheKey !== null) BitmapCache.getBitmapFromMemCache(cacheKey) else null
    if (bitmap !== null) updateImageView(bitmap, v, binding)
    else AppScope.launch { getImage(v, findInLibrary(item, isMedia, isGroup), binding) }
}

@MainThread
@BindingAdapter(value = ["bind:mediaWithWidth", "bind:imageWidth"], requireAll = true)
fun loadPlaylistImageWithWidth(v: ImageView, item: MediaLibraryItem?, imageWidth: Int) {
    if (imageWidth == 0) return
    if (item == null) return
    val binding = DataBindingUtil.findBinding<ViewDataBinding>(v)
    AppScope.launch { getPlaylistImage(v, item, binding, imageWidth.toInt()) }
}

fun getAudioIconDrawable(context: Context?, type: Int): BitmapDrawable? = context?.let {
    when (type) {
        MediaLibraryItem.TYPE_ALBUM -> UiTools.getDefaultAlbumDrawable(it)
        MediaLibraryItem.TYPE_ARTIST -> UiTools.getDefaultArtistDrawable(it)
        MediaLibraryItem.TYPE_MEDIA -> UiTools.getDefaultAudioDrawable(it)
        else -> null
    }
}

fun getMediaIconDrawable(context: Context, type: Int): BitmapDrawable? = when (type) {
    MediaWrapper.TYPE_VIDEO -> UiTools.getDefaultVideoDrawable(context)
    else -> UiTools.getDefaultAudioDrawable(context)
}

private var placeholderTvBg: Drawable? = null
@MainThread
@BindingAdapter("placeholder")
fun placeHolderView(v: View, item: MediaLibraryItem?) {
    if (item == null) {
        if (placeholderTvBg === null) placeholderTvBg = ContextCompat.getDrawable(v.context, R.drawable.rounded_corners_grey)
        v.background = placeholderTvBg
    } else {
        v.background = null
    }

}

fun isForTV(binding: ViewDataBinding?) = (binding is AudioBrowserTvItemBinding)

@MainThread
@BindingAdapter("placeholderImage")
fun placeHolderImageView(v: View, item: MediaLibraryItem?) {
    if (item == null) {
        v.background = ContextCompat.getDrawable(v.context, R.drawable.rounded_corners_grey)
    } else {
        v.background = UiTools.getDefaultAudioDrawable(v.context)
    }

}

@BindingAdapter("icvTitle")
fun imageCardViewTitle(v: View, title: String?) {
    if (v is ImageCardView) {
        v.titleText = title
    }
}

@BindingAdapter("icvContent")
fun imageCardViewContent(v: View, content: String?) {
    if (v is ImageCardView) {
        v.contentText = content
    }
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
    val width = when {
        v.width > 0 -> v.width
        defaultImageWidth > 0 -> defaultImageWidth
        else -> {
            defaultImageWidth = v.context.resources.getDimensionPixelSize(if (v is ImageCardView) R.dimen.tv_grid_card_thumb_width else R.dimen.audio_browser_item_size)
            defaultImageWidth
        }
    }
    val image = if (!bindChanged) obtainBitmap(item, width) else null
    if (image == null && isForTV(binding)) {
        val imageTV = BitmapFactory.decodeResource(v.resources, TvUtil.getIconRes(item))
        // binding is set to null to be sure to set the src and not the cover (background)
        if (!bindChanged) updateImageView(imageTV, v, null)
        binding?.removeOnRebindCallback(rebindCallbacks!!)
        return
    }
    if (!bindChanged) updateImageView(image, v, binding)
    binding?.removeOnRebindCallback(rebindCallbacks!!)
}

private suspend fun getPlaylistImage(v: View, item: MediaLibraryItem, binding: ViewDataBinding?, width: Int) {
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


    var playlistImage = if (!bindChanged) ThumbnailsProvider.getPlaylistImage("playlist:${item.id}", item.tracks.toList(), width) else null
    if (!bindChanged && playlistImage == null) playlistImage = UiTools.getDefaultAudioDrawable(VLCApplication.appContext).bitmap
    if (!bindChanged) updateImageView(playlistImage, v, binding)

    binding?.removeOnRebindCallback(rebindCallbacks!!)
}


@MainThread
fun updateImageView(bitmap: Bitmap?, target: View, vdb: ViewDataBinding?) {
    if (bitmap === null || bitmap.width <= 1 || bitmap.height <= 1) return
    if (vdb !== null && !isForTV(vdb)) {
        vdb.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER)
        vdb.setVariable(BR.cover, BitmapDrawable(target.resources, bitmap))
        vdb.setVariable(BR.protocol, null)
    } else when (target) {
        is ImageView -> {
            target.scaleType = if (isForTV(vdb)) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
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

private suspend fun findInLibrary(item: MediaLibraryItem, isMedia: Boolean, isGroup: Boolean): MediaLibraryItem {
    if (isMedia && !isGroup && item.id == 0L) {
        val mw = item as MediaWrapper
        val type = mw.type
        val isMediaFile = type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO
        val uri = mw.uri
        if (!isMediaFile && !(type == MediaWrapper.TYPE_DIR && "upnp" == uri.scheme)) return item
        if (isMediaFile && "file" == uri.scheme) return withContext(Dispatchers.IO) { sMedialibrary.getMedia(uri) }
                ?: item
    }
    return item
}