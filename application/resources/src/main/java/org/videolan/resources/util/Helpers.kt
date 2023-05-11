package org.videolan.resources.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.view.View
import androidx.core.content.ContextCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.R
import java.util.*

const val LENGTH_DAY = 24 * 60 * 60
const val LENGTH_WEEK = 7 * LENGTH_DAY
const val LENGTH_MONTH = 30 * LENGTH_DAY
const val LENGTH_YEAR = 52 * LENGTH_WEEK
const val LENGTH_2_YEAR = 2 * LENGTH_YEAR

fun getTimeCategory(timestamp: Long): Int {
    val delta = (System.currentTimeMillis() / 1000L) - timestamp
    return when {
        delta < LENGTH_DAY -> 0
        delta < 2 * LENGTH_DAY -> 1
        delta < LENGTH_WEEK -> 2
        delta < 2 * LENGTH_WEEK -> 3
        delta < LENGTH_MONTH -> 4
        delta < LENGTH_YEAR -> 5
        delta < LENGTH_2_YEAR -> 6
        else -> 7
    }
}

fun getTimeCategoryString(context: Context, cat: Int) = when (cat) {
    0 -> context.getString(R.string.time_category_today)
    1 -> context.getString(R.string.time_category_yesterday)
    2 -> context.getString(R.string.time_category_current_week)
    3 -> context.getString(R.string.time_category_last_week)
    4 -> context.getString(R.string.time_category_current_month)
    5 -> context.getString(R.string.time_category_current_year)
    6 -> context.getString(R.string.time_category_last_year)
    else -> context.getString(R.string.time_category_older)
}

fun MediaLibraryItem.isSpecialItem() = itemType == MediaLibraryItem.TYPE_ARTIST
        && (id == 1L || id == 2L) || itemType == MediaLibraryItem.TYPE_ALBUM
        && title == Album.SpecialRes.UNKNOWN_ALBUM

fun MediaLibraryItem.getLength() = when {
    itemType == MediaLibraryItem.TYPE_ALBUM -> (this as Album).duration
    itemType == MediaLibraryItem.TYPE_MEDIA -> (this as MediaWrapper).length
    itemType == MediaLibraryItem.TYPE_VIDEO_GROUP -> (this as VideoGroup).duration()
    else -> 0L
}

// Albums and MediaWrappers use MedialibraryItem's releaseDate as releaseYear
// Though when indexed an album's releaseDate saves only the year,
// whereas MediaWrappers' releaseDate save the whole date
fun MediaLibraryItem.getYear() = when (itemType) {
    MediaLibraryItem.TYPE_ALBUM -> {
        if ((this as Album).releaseYear <= 0) "-" else releaseYear.toString()
    }
    MediaLibraryItem.TYPE_MEDIA ->{
        if ((this as MediaWrapper).releaseYear <= 0)
            "-"
        else {
            val calendar = Calendar.getInstance()
            calendar.time = Date(releaseYear.toLong() * 1000)
            calendar.get(Calendar.YEAR).toString()
        }
    }
    else -> "-"
}

fun canReadStorage(context: Context): Boolean {
    return !AndroidUtil.isMarshMallowOrLater || ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || isExternalStorageManager()
}

/**
 * Check if the app has the [Manifest.permission.MANAGE_EXTERNAL_STORAGE] granted
 */
fun isExternalStorageManager(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

@JvmOverloads
fun canWriteStorage(context: Context = AppContextProvider.appContext): Boolean {
    return ContextCompat.checkSelfPermission(context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}



fun applyOverscanMargin(activity: Activity) {
    val hm = activity.resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal)
    val vm = activity.resources.getDimensionPixelSize(R.dimen.tv_overscan_vertical)
    activity.findViewById<View>(android.R.id.content).setPadding(hm, vm, hm, vm)
}

fun applyOverscanMargin(view: View) {
    val hm = view.resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal)
    val vm = view.resources.getDimensionPixelSize(R.dimen.tv_overscan_vertical)
    view.setPadding(hm + view.paddingLeft, vm + view.paddingTop, hm + view.paddingRight, vm + view.paddingBottom)
}
