package org.videolan.vlc.util

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.icu.text.Transliterator
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.text.PrecomputedTextCompat
import androidx.core.text.toSpannable
import androidx.core.widget.TextViewCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.MediaWrapper.TYPE_ALL
import org.videolan.medialibrary.interfaces.media.MediaWrapper.TYPE_VIDEO
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.getFromMl
import org.videolan.tools.AppScope
import org.videolan.tools.isStarted
import org.videolan.tools.retrieveParent
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.KEY_MEDIA
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun String.validateLocation(): Boolean {
    var location = this
    /* Check if the MRL contains a scheme */
    if (!location.matches("\\w+://.+".toRegex())) location = "file://$location"
    if (location.lowercase(Locale.ENGLISH).startsWith("file://")) {
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

inline fun <reified T : ViewModel> Fragment.getModelWithActivity() = ViewModelProvider(requireActivity()).get(T::class.java)
inline fun <reified T : ViewModel> Fragment.getModel() = ViewModelProvider(this).get(T::class.java)
inline fun <reified T : ViewModel> FragmentActivity.getModel() = ViewModelProvider(this).get(T::class.java)

fun Media?.canExpand() = this != null && (type == IMedia.Type.Directory || type == IMedia.Type.Playlist)

fun FragmentActivity.share(file: File) {
    val intentShareFile = Intent(Intent.ACTION_SEND)
    val fileWithinMyDir = File(file.path)
    if (isStarted()) {
        intentShareFile.type = "*/*"
        intentShareFile.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "$packageName.provider", fileWithinMyDir))
        intentShareFile.putExtra(Intent.EXTRA_SUBJECT, file.name)
        intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, file.name))
        startActivity(Intent.createChooser(intentShareFile, getString(R.string.share_file,file.name)))
    }
}

fun FragmentActivity.share(title:String, content: String) {
    val intentShareFile = Intent(Intent.ACTION_SEND)
    if (isStarted()) {
        intentShareFile.type = "*/*"
        intentShareFile.putExtra(Intent.EXTRA_SUBJECT, title)
        intentShareFile.putExtra(Intent.EXTRA_TEXT, content)
        startActivity(Intent.createChooser(intentShareFile, getString(R.string.share_file,title)))
    }
}

suspend fun AppCompatActivity.share(media: MediaWrapper) {
    val intentShareFile = Intent(Intent.ACTION_SEND)
    val fileWithinMyDir = File(media.uri.path)
    val validFile = withContext(Dispatchers.IO) {
        fileWithinMyDir.exists()
    }

    if (isStarted())
        if (validFile) {
            intentShareFile.type = if (media.type == TYPE_VIDEO) "video/*" else "audio/*"
            intentShareFile.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "$packageName.provider", fileWithinMyDir))
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, media.title)
            intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, media.title))
            startActivity(Intent.createChooser(intentShareFile, getString(R.string.share_file, media.title)))
        } else Snackbar.make(findViewById(android.R.id.content), R.string.invalid_file, Snackbar.LENGTH_LONG).show()
}

fun FragmentActivity.share(medias: List<MediaWrapper>) = lifecycleScope.launch {
    val intentShareFile = Intent(Intent.ACTION_SEND_MULTIPLE)
    val uris = arrayListOf<Uri>()
    val title = if (medias.size == 1) medias[0].title else resources.getQuantityString(R.plurals.media_quantity, medias.size, medias.size)
    withContext(Dispatchers.IO) {
        medias.filter { it.uri.path != null && File(it.uri.path!!).exists() }.forEach {
            val file = File(it.uri.path!!)
            uris.add(FileProvider.getUriForFile(this@share, "$packageName.provider", file))
        }
    }

    if (isStarted())
        if (uris.isNotEmpty()) {
            intentShareFile.type = "*/*"
            intentShareFile.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, title)
            intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, title))
            startActivity(Intent.createChooser(intentShareFile, getString(R.string.share_file, title)))
        } else Snackbar.make(findViewById(android.R.id.content), R.string.invalid_file, Snackbar.LENGTH_LONG).show()
}

fun MediaWrapper?.isMedia() = this != null && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO)
fun MediaWrapper?.isBrowserMedia() = this != null && (isMedia() || type == MediaWrapper.TYPE_DIR || type == MediaWrapper.TYPE_PLAYLIST)

fun Context.getAppSystemService(name: String) = applicationContext.getSystemService(name)!!

fun Long.random() = (SecureRandom().nextFloat() * this).toLong()

suspend fun Context.awaitMedialibraryStarted() = getFromMl { isStarted }

@WorkerThread
fun List<MediaWrapper>.updateWithMLMeta() : MutableList<MediaWrapper> {
    val ml = Medialibrary.getInstance()
    val list = mutableListOf<MediaWrapper>()
    for (media in this) {
        list.add(ml.findMedia(media).apply {
            if (type == TYPE_ALL) type = media.type
        })
    }
    return list
}

suspend fun String.scanAllowed() = withContext(Dispatchers.IO) {
    val file = File(toUri().path ?: return@withContext false)
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
    setTextAsync(view, text, params)
}

@BindingAdapter("app:asyncText", requireAll = false)
fun asyncTextItem(view: TextView, item: MediaLibraryItem?) {
    if (item == null) {
        view.visibility = View.GONE
        return
    }
    val text = if (item is Playlist) {
        if (item.duration != 0L) {
            val duration = Tools.millisToString(item.duration)
            TextUtils.separatedString(view.context.getString(R.string.track_number, item.tracksCount), if (item.nbDurationUnknown > 0) "$duration+" else duration)
        } else view.context.getString(R.string.track_number, item.tracksCount)
    } else item.description
    if (text.isNullOrEmpty()) {
        view.visibility = View.GONE
        return
    }
    view.visibility = View.VISIBLE
    val params = TextViewCompat.getTextMetricsParams(view)
    setTextAsync(view, text, params)
}

@BindingAdapter("layoutMarginTop")
fun setLayoutMarginTop(view: View, dimen: Int) {
    val layoutParams = view.layoutParams as MarginLayoutParams
    layoutParams.topMargin = dimen
    view.layoutParams = layoutParams
}

private fun setTextAsync(view: TextView, text: CharSequence, params: PrecomputedTextCompat.Params) {
    val ref = WeakReference(view)
    AppScope.launch(Dispatchers.Default) {
        val pText = PrecomputedTextCompat.create(text, params)
        val result = pText.toSpannable()
        withContext(Dispatchers.Main) {
            ref.get()?.let { textView ->
                textView.text = result
            }
        }
    }
}

const val folderReplacementMarker = "§*§"
const val fileReplacementMarker = "*§*"

@BindingAdapter("app:browserDescription", requireAll = false)
fun browserDescription(view: TextView, description: String?) {
    (view as AppCompatTextView).text = description?.getDescriptionSpan(view.context)
}

fun CharSequence.getDescriptionSpan(context: Context):SpannableString {
    val string = SpannableString(this)
    if (this.contains(folderReplacementMarker)) {
        string.setSpan(ImageSpan(context, R.drawable.ic_emoji_folder, DynamicDrawableSpan.ALIGN_BASELINE), this.indexOf(folderReplacementMarker), this.indexOf(folderReplacementMarker)+3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (this.contains(fileReplacementMarker)) {
        string.setSpan(ImageSpan(context, R.drawable.ic_emoji_file, DynamicDrawableSpan.ALIGN_BASELINE), this.indexOf(fileReplacementMarker), this.indexOf(fileReplacementMarker)+3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return string
}

/**
 * Get the folder number from the formatted string
 *
 * @return the folder number
 */
fun CharSequence?.getFolderNumber():Int {
    if (isNullOrBlank()) return 0
    if (!contains(folderReplacementMarker)) return 0
    val cutString = replace(Regex("[^0-9 ]"), "")
    return cutString.trim().split(" ")[0].toInt()
}

/**
 * Get the file number from the formatted string
 *
 * @return the file number
 */
fun CharSequence?.getFilesNumber():Int {
    if (isNullOrBlank()) return 0
    if (!contains(fileReplacementMarker)) return 0
    val cutString = replace(Regex("[^0-9 ]"), "").trim().split(" ")
    return cutString[cutString.size -1].toInt()
}

/**
 * Slugify a string. Use the Unicode Transliterator to convert
 * non-ASCII characters into a latin representation.
 *
 * @param replacement the replacement char
 * @return the slugified string
 */
fun String.slugify(replacement: String = "-"): String {
    val s = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // NFD is included in Latin-ASCII
        Transliterator.getInstance("Any-Latin; Lower; Latin-ASCII").transliterate(this)
    } else {
        Normalizer.normalize(this, Normalizer.Form.NFD)
    }
    return s.replace("[^a-zA-Z0-9\\s]+".toRegex(), "").trim()
            .replace("\\s+".toRegex(), replacement)
}

const val presentReplacementMarker = "§*§"
const val missingReplacementMarker = "*§*"

fun MediaLibraryItem.getPresenceDescription() = when (this) {
    is VideoGroup -> TextUtils.separatedString("${this.presentCount} §*§", "${this.mediaCount() - this.presentCount} *§*")
    else -> ""
}

@BindingAdapter("app:presenceDescription", requireAll = false)
fun presenceDescription(view: TextView, description: String?) {
    (view as AppCompatTextView).text = description?.getPresenceDescriptionSpan(view.context)
}

fun CharSequence.getPresenceDescriptionSpan(context: Context):SpannableString {
    val string = SpannableString(this)
    if (this.contains(presentReplacementMarker)) {
        string.setSpan(ImageSpan(context, R.drawable.ic_emoji_media_present, DynamicDrawableSpan.ALIGN_CENTER), this.indexOf(folderReplacementMarker), this.indexOf(folderReplacementMarker)+3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (this.contains(missingReplacementMarker)) {
        string.setSpan(ImageSpan(context, R.drawable.ic_emoji_media_absent, DynamicDrawableSpan.ALIGN_CENTER), this.indexOf(fileReplacementMarker), this.indexOf(fileReplacementMarker)+3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return string
}

fun Int.toPixel(): Int {
    val metrics = Resources.getSystem().displayMetrics
    val px = toFloat() * (metrics.densityDpi / 160f)
    return px.roundToInt()
}

fun Activity.getScreenWidth() : Int {
    val dm = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
    return dm.widthPixels
}

fun Activity.getScreenHeight(): Int {
    val dm = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
    return dm.heightPixels
}

/**
 * Detect if the device has a notch.
 * @return true if the device has a notch
 * @throws NullPointerException if the window is not attached yet
 */
fun Activity.hasNotch() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window.decorView.rootWindowInsets.displayCutout != null

@TargetApi(Build.VERSION_CODES.O)
fun Context.getPendingIntent(iPlay: Intent): PendingIntent {
    return if (AndroidUtil.isOOrLater) PendingIntent.getForegroundService(applicationContext, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    else PendingIntent.getService(applicationContext, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/**
 * Register an [RecyclerView.AdapterDataObserver] for the adapter.
 *
 * [listener] is called each time a change occurs in the adapter
 *
 * return the registered [RecyclerView.AdapterDataObserver]
 *
 * /!\ Make sure to unregister [RecyclerView.AdapterDataObserver]
 */
fun RecyclerView.Adapter<*>.onAnyChange(listener: ()->Unit): RecyclerView.AdapterDataObserver {
    val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            super.onChanged()
            listener.invoke()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            super.onItemRangeChanged(positionStart, itemCount)
            listener.invoke()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            super.onItemRangeChanged(positionStart, itemCount, payload)
            listener.invoke()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            super.onItemRangeInserted(positionStart, itemCount)
            listener.invoke()
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            super.onItemRangeMoved(fromPosition, toPosition, itemCount)
            listener.invoke()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            super.onItemRangeRemoved(positionStart, itemCount)
            listener.invoke()
        }
    }
    registerAdapterDataObserver(dataObserver)
    return dataObserver
}

/**
 * Generate a string containing the commercial denomination of the video resolution
 *
 * @param width the video width
 * @param height the video height
 * @return the commercial resolution (SD, HD, 4K, ...)
 */
fun generateResolutionClass(width: Int, height: Int): String? = if (width <= 0 || height <= 0) {
    null
} else {
    val realHeight = min(height, width)
    val realWidth = max(height, width)
    when {
        realHeight >= 4320 || realWidth >= 4320.0 * (16.0 / 9.0) -> "8K"
        realHeight >= 2160 || realWidth >= 2160.0 * (16.0 / 9.0) -> "4K"
        realHeight >= 1440 || realWidth >= 1440.0 * (16.0 / 9.0) -> "1440p"
        realHeight >= 1080 || realWidth >= 1080.0 * (16.0 / 9.0) -> "1080p"
        realHeight >= 720 || realWidth >= 720.0 * (16.0 / 9.0) -> "720p"
        else -> "SD"
    }
}

val View.scope : CoroutineScope
    get() = when(val ctx = context) {
        is CoroutineScope -> ctx
        is LifecycleOwner -> ctx.lifecycleScope
        else -> AppScope
    }

fun <T> Flow<T>.launchWhenStarted(scope: LifecycleCoroutineScope): Job = scope.launchWhenStarted {
    collect() // tail-call
}

/**
 * Sanitize a string by adding enough "0" at the start
 * to make a "natural" alphanumeric comparison (1, 2, 10, 11, 20) instead of a strict one (1, 10, 11, 21, 20)
 *
 * @param nbOfDigits the number of digits to reach
 * @return a string having exactly [nbOfDigits] digits at the start
 */
fun String?.sanitizeStringForAlphaCompare(nbOfDigits: Int): String? {
    if (this == null) return null
    if (first().isDigit()) return buildString {
        var numberOfPrependingZeros =0
        for (c in this@sanitizeStringForAlphaCompare) {
            if (c.isDigit() && c.digitToInt() == 0) numberOfPrependingZeros++ else break
        }
        for (i in 0 until (nbOfDigits - numberOfPrependingZeros - (getStartingNumber()?.numberOfDigits() ?: 0))) {
            append("0")
        }
        append(this@sanitizeStringForAlphaCompare)
    }
    return this
}

/**
 * Calculate the number of digits of an Int
 *
 * @return the number of digits of this Int
 */
fun Int.numberOfDigits(): Int = when (this) {
    in -9..9 -> 1
    else -> 1 + (this / 10).numberOfDigits()
}

/**
 * Get the number described at the start of this String if any
 *
 * @return the starting number of this String, null if no number found
 */
fun String.getStartingNumber(): Int? {
    return try {
        buildString {
            for (c in this@getStartingNumber)
                //we exclude starting "0" to prevent bad sorts
                if (c.isDigit()) {
                    if (!(this.isEmpty() && c.digitToInt() == 0)) append(c)
                } else break
        }.toInt()
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Determine the max number of digits iat the start of
 * this lit items' filename
 *
 * @return a max number of digits
 */
fun List<MediaLibraryItem>.determineMaxNbOfDigits(): Int {
    var numberOfPrepending = 0
    forEach {
        numberOfPrepending = max((it as? MediaWrapper)?.fileName?.getStartingNumber()?.numberOfDigits()
                ?: 0, numberOfPrepending)
    }
    return numberOfPrepending
}

fun Fragment.showParentFolder(media: MediaWrapper) {
    val parent = MLServiceLocator.getAbstractMediaWrapper(media.uri.retrieveParent()).apply {
        type = MediaWrapper.TYPE_DIR
    }
    val intent = Intent(requireActivity().applicationContext, SecondaryActivity::class.java)
    intent.putExtra(KEY_MEDIA, parent)
    intent.putExtra("fragment", SecondaryActivity.FILE_BROWSER)
    startActivity(intent)
}

/**
 * Finds the [ViewPager2] current fragment
 * @param fragmentManager: The used [FragmentManager]
 *
 * @return the current fragment if found
 */
fun ViewPager2.findCurrentFragment(fragmentManager: FragmentManager): Fragment? {
    return fragmentManager.findFragmentByTag("f$currentItem")
}

/**
 * Finds the [ViewPager2] fragment at a specified position
 * @param fragmentManager: The used [FragmentManager]
 * @param position: The position to look at
 *
 * @return the fragment if found
 */
fun ViewPager2.findFragmentAt(fragmentManager: FragmentManager, position: Int): Fragment? {
    return fragmentManager.findFragmentByTag("f$position")
}
