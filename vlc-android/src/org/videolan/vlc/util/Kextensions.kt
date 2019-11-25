package org.videolan.vlc.util

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import androidx.annotation.WorkerThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.PrecomputedTextCompat
import androidx.core.widget.TextViewCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.*
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper.TYPE_ALL
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.startMedialibrary
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import kotlin.coroutines.resume

//object Settings : SingletonHolder<SharedPreferences, Context>({ PreferenceManager.getDefaultSharedPreferences(it) })

fun String.validateLocation(): Boolean {
    var location = this
    /* Check if the MRL contains a scheme */
    if (!location.matches("\\w+://.+".toRegex())) location = "file://$location"
    if (location.toLowerCase(Locale.ENGLISH).startsWith("file://")) {
        /* Ensure the file exists */
        val f: File
        try {
            f = File(URI(location))
        } catch (e: URISyntaxException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (!f.isFile) return false
    }
    return true
}

inline fun <reified T : ViewModel> Fragment.getModelWithActivity() = ViewModelProviders.of(requireActivity()).get(T::class.java)
inline fun <reified T : ViewModel> Fragment.getModel() = ViewModelProviders.of(this).get(T::class.java)
inline fun <reified T : ViewModel> FragmentActivity.getModel() = ViewModelProviders.of(this).get(T::class.java)

fun Media?.canExpand() = this != null && (type == Media.Type.Directory || type == Media.Type.Playlist)
fun AbstractMediaWrapper?.isMedia() = this != null && (type == AbstractMediaWrapper.TYPE_AUDIO || type == AbstractMediaWrapper.TYPE_VIDEO)
fun AbstractMediaWrapper?.isBrowserMedia() = this != null && (isMedia() || type == AbstractMediaWrapper.TYPE_DIR || type == AbstractMediaWrapper.TYPE_PLAYLIST)

fun Context.getAppSystemService(name: String) = applicationContext.getSystemService(name)!!

fun Long.random() = (Random().nextFloat() * this).toLong()

@ExperimentalCoroutinesApi
suspend inline fun <reified T> Context.getFromMl(crossinline block: AbstractMedialibrary.() -> T) = withContext(Dispatchers.IO) {
    val ml = AbstractMedialibrary.getInstance()
    if (ml.isStarted) block.invoke(ml)
    else {
        val scan = Settings.getInstance(this@getFromMl).getInt(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON) == ML_SCAN_ON
        suspendCancellableCoroutine { continuation ->
            val listener = object : AbstractMedialibrary.OnMedialibraryReadyListener {
                override fun onMedialibraryReady() {
                    val cb = this
                    if (!continuation.isCompleted) launch(start = CoroutineStart.UNDISPATCHED) {
                        continuation.resume(block.invoke(ml))
                        yield()
                        ml.removeOnMedialibraryReadyListener(cb)
                    }
                }
                override fun onMedialibraryIdle() {}
            }
            continuation.invokeOnCancellation { ml.removeOnMedialibraryReadyListener(listener) }
            ml.addOnMedialibraryReadyListener(listener)
            startMedialibrary(false, false, scan)
        }
    }
}

@WorkerThread
fun List<AbstractMediaWrapper>.updateWithMLMeta() : MutableList<AbstractMediaWrapper> {
    val ml = AbstractMedialibrary.getInstance()
    val list = mutableListOf<AbstractMediaWrapper>()
    for (media in this) {
        list.add(ml.findMedia(media).apply {
            if (type == TYPE_ALL) type = media.type
        })
    }
    return list
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
suspend fun String.scanAllowed() = withContext(Dispatchers.IO) {
    val file = File(Uri.parse(this@scanAllowed).path)
    if (!file.exists() || !file.canRead()) return@withContext false
    if (AndroidDevices.watchDevices && file.list()?.any { it == ".nomedia" } == true) return@withContext false
    true
}

fun <X, Y> CoroutineScope.map(
        source: LiveData<X>,
        f : suspend (value: X?) -> Y
): LiveData<Y> {
    return MediatorLiveData<Y>().apply {
        addSource(source) {
            launch { value = f(it) }
        }
    }
}

@BindingAdapter("app:asyncText", requireAll = false)
fun asyncText(view: TextView, text: CharSequence?) {
    if (text.isNullOrEmpty()) {
        view.visibility = View.GONE
        return
    }
    view.visibility = View.VISIBLE
    val params = TextViewCompat.getTextMetricsParams(view)
    (view as AppCompatTextView).setTextFuture(PrecomputedTextCompat.getTextFuture(text, params, null))
}

@BindingAdapter("app:asyncText", requireAll = false)
fun asyncTextItem(view: TextView, item: MediaLibraryItem?) {
    if (item == null) {
        view.visibility = View.GONE
        return
    }
    val text = if (item.itemType == MediaLibraryItem.TYPE_PLAYLIST) view.context.getString(R.string.track_number, item.tracksCount.toString()) else item.description
    if (text.isNullOrEmpty()) {
        view.visibility = View.GONE
        return
    }
    view.visibility = View.VISIBLE
    val params = TextViewCompat.getTextMetricsParams(view)
    (view as AppCompatTextView).setTextFuture(PrecomputedTextCompat.getTextFuture(text, params, null))
}

fun isAppStarted() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

fun Int.toPixel(): Int {
    val metrics = Resources.getSystem().displayMetrics
    val px = toFloat() * (metrics.densityDpi / 160f)
    return Math.round(px)
}

fun Activity.getScreenWidth() : Int {
    val dm = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
    return dm.widthPixels
}

fun Activity.getScreenHeight(): Int {
    val dm = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
    return dm.heightPixels
}

@TargetApi(Build.VERSION_CODES.O)
fun Context.getPendingIntent(iPlay: Intent): PendingIntent {
    return if (AndroidUtil.isOOrLater) PendingIntent.getForegroundService(applicationContext, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT)
    else PendingIntent.getService(applicationContext, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT)
}

fun generateResolutionClass(width: Int, height: Int) : String? = if (width <= 0 || height <= 0) {
    null
} else when {
    width >= 7680 -> "8K"
    width >= 3840 -> "4K"
    width >= 1920 -> "1080p"
    width >= 1280 -> "720p"
    else -> "SD"
}