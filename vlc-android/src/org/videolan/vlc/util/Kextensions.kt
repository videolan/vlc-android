package org.videolan.vlc.util

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.PrecomputedTextCompat
import androidx.core.widget.TextViewCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.videolan.libvlc.Media
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.startMedialibrary
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import kotlin.coroutines.resume



object Settings : SingletonHolder<SharedPreferences, Context>({ PreferenceManager.getDefaultSharedPreferences(it) })

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

suspend fun retry (
        times: Int = 3,
        delayTime: Long = 500L,
        block: suspend () -> Boolean): Boolean
{
    repeat(times - 1) {
        if (block()) return true
        delay(delayTime)
    }
    return block() // last attempt
}

fun Media?.canExpand() = this != null && (type == Media.Type.Directory || type == Media.Type.Playlist)
fun MediaWrapper?.isMedia() = this != null && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO)
fun MediaWrapper?.isBrowserMedia() = this != null && (isMedia() || type == MediaWrapper.TYPE_DIR || type == MediaWrapper.TYPE_PLAYLIST)

fun Context.getAppSystemService(name: String) = applicationContext.getSystemService(name)!!

fun Long.random() = (Random().nextFloat() * this).toLong()

@ExperimentalCoroutinesApi
suspend inline fun <reified T> Context.getFromMl(crossinline block: Medialibrary.() -> T) = withContext(Dispatchers.IO) {
    val ml = Medialibrary.getInstance()
    if (ml.isStarted) block.invoke(ml)
    else {
        val scan = Settings.getInstance(this@getFromMl).getInt(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON) == ML_SCAN_ON
        suspendCancellableCoroutine { continuation ->
            val listener = object : Medialibrary.OnMedialibraryReadyListener {
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


fun List<MediaWrapper>.getWithMLMeta() : List<MediaWrapper> {
    if (this is MutableList<MediaWrapper>) return updateWithMLMeta()
    val list = mutableListOf<MediaWrapper>()
    val ml = VLCApplication.getMLInstance()
    for (media in this) {
        if (media.id == 0L) {
            val mw = ml.findMedia(media)
            if (mw.id != 0L) if (mw.type == MediaWrapper.TYPE_ALL) mw.type = media.type
            list.add(mw)
        }
    }
    return list
}


fun MutableList<MediaWrapper>.updateWithMLMeta() : MutableList<MediaWrapper> {
    val iter = listIterator()
    val ml = VLCApplication.getMLInstance()
    try {
        while (iter.hasNext()) {
            val media = iter.next()
            if (media.id == 0L) {
                val mw = ml.findMedia(media)
                if (mw!!.id != 0L) {
                    if (mw.type == MediaWrapper.TYPE_ALL) mw.type = media.getType()
                    iter.set(mw)
                }
            }
        }
    } catch (ignored: Exception) {}
    return this
}

suspend fun String.scanAllowed() = withContext(Dispatchers.IO) {
    val file = File(Uri.parse(this@scanAllowed).path)
    if (!file.exists() || !file.canRead()) return@withContext false
    val children = file.list() ?: return@withContext true
    for (child in children) if (child == ".nomedia") return@withContext false
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
