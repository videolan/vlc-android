package org.videolan.vlc.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.SharedPreferences
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v7.preference.PreferenceManager
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.Media
import org.videolan.medialibrary.Medialibrary
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.startMedialibrary
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine

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

fun Context.getAppSystemService(name: String) = applicationContext.getSystemService(name)!!

fun Long.random() = (Random().nextFloat() * this).toLong()

suspend inline fun <reified T> Context.getFromMl(crossinline block: Medialibrary.() -> T) = withContext(Dispatchers.IO) {
    val ml = Medialibrary.getInstance()
    if (ml.isInitiated) block.invoke(ml)
    else suspendCoroutine { continuation ->
        ml.addOnMedialibraryReadyListener(object : Medialibrary.OnMedialibraryReadyListener {
            override fun onMedialibraryReady() {
                ml.removeOnMedialibraryReadyListener(this)
                continuation.resume(block.invoke(ml))
            }
            override fun onMedialibraryIdle() {}
        })
        startMedialibrary(false, false, false)
    }
}
