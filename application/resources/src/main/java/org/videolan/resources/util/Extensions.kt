package org.videolan.resources.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.*
import org.videolan.tools.*
import java.io.File
import kotlin.coroutines.resume


/**
 * Allows getting a ready medialibrary to query it.
 * @param block: the unit to invoke when the medialibrary is ready
 */
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

/**
 * Blocks the current coroutine while the medialibrary is not ready.
 * Useful when we know the medialibrary init has been launched,
 * we already have an instance of it and we want to wait that it's ready to query it
 */
suspend inline fun waitForML() = withContext(Dispatchers.IO) {
    val ml = Medialibrary.getInstance()
    if (!ml.isStarted){
        suspendCancellableCoroutine<() -> Unit> { continuation ->
            val listener = object : Medialibrary.OnMedialibraryReadyListener {
                override fun onMedialibraryReady() {
                    val cb = this
                    if (!continuation.isCompleted) launch(start = CoroutineStart.UNDISPATCHED) {
                        continuation.resume {}
                        yield()
                        ml.removeOnMedialibraryReadyListener(cb)
                    }
                }
                override fun onMedialibraryIdle() {}
            }
            continuation.invokeOnCancellation { ml.removeOnMedialibraryReadyListener(listener) }
            ml.addOnMedialibraryReadyListener(listener)
        }
    }
}

fun Context.startMedialibrary(firstRun: Boolean = false, upgrade: Boolean = false, parse: Boolean = true, removeDevices:Boolean = false, coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) = AppScope.launch {
    if (Medialibrary.getInstance().isStarted) return@launch
    val prefs = withContext(coroutineContextProvider.IO) { Settings.getInstance(this@startMedialibrary) }
    val scanOpt = if (Settings.showTvUi) ML_SCAN_ON else prefs.getInt(KEY_MEDIALIBRARY_SCAN, -1)
    if (parse && scanOpt == -1) {
        if (dbExists(coroutineContextProvider)) prefs.putSingle(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON)
    }
    val intent = Intent(ACTION_INIT).setClassName(applicationContext, MEDIAPARSING_SERVICE)
    launchForeground(intent
            .putExtra(EXTRA_FIRST_RUN, firstRun)
            .putExtra(EXTRA_UPGRADE, upgrade)
            .putExtra(EXTRA_REMOVE_DEVICE, removeDevices)
            .putExtra(EXTRA_PARSE, parse && scanOpt != ML_SCAN_OFF && canReadStorage(this@startMedialibrary)))
}

suspend fun Context.dbExists(coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) = withContext(coroutineContextProvider.IO) {
    File(getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME).exists()
}

fun Context.launchForeground(intent: Intent) {
    val ctx = this
    AppScope.launch(Dispatchers.Main) {
        intent.putExtra("foreground", true)
        ContextCompat.startForegroundService(ctx, intent)
    }
}

/**
 * Use the new API to retrieve a parcelable extra on an [Intent]
 *
 * @param T the extra type
 * @param key the extra key
 * @return return the un-parceled result
 */
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

/**
 * Use the new API to retrieve a parcelable extra on an [Bundle]
 *
 * @param T the extra type
 * @param key the extra key
 * @return return the un-parceled result
 */
inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

/**
 * Use the new API to retrieve a parcelable array list extra on an [Intent]
 *
 * @param T the extra type
 * @param key the extra key
 * @return return the un-parceled list result
 */
inline fun <reified T : Parcelable> Intent.parcelableList(key: String): ArrayList<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayListExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
}

/**
 * Use the new API to retrieve a parcelable array list extra on an [Bundle]
 *
 * @param T the extra type
 * @param key the extra key
 * @return return the un-parceled result
 */
inline fun <reified T : Parcelable> Bundle.parcelableList(key: String): ArrayList<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayList(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayList(key)
}

/**
 * Use the new API to retrieve a parcelable array extra on an [Intent]
 *
 * @param T the extra type
 * @param key the extra key
 * @return return the un-parceled list result
 */
inline fun <reified T : Parcelable> Intent.parcelableArray(key: String): Array<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION", "UNCHECKED_CAST") (getParcelableArrayExtra(key) as Array<T>)
}

/**
 * Use the new API to retrieve a parcelable array extra on an [Bundle]
 *
 * @param T the extra type
 * @param key the extra key
 * @return return the un-parceled result
 */
inline fun <reified T : Parcelable> Bundle.parcelableArray(key: String): Array<T>? = when {
    SDK_INT >= 33 -> getParcelableArray(key, T::class.java)
    else -> @Suppress("DEPRECATION", "UNCHECKED_CAST") (getParcelableArray(key) as Array<T>)
}

/**
 * Use the new API to stop the foreground state of a service
 *
 * @param removeNotification Removes the notification if true
 */
fun Service.stopForegroundCompat(removeNotification:Boolean = true) = when {
    SDK_INT >= 24 -> stopForeground(if (removeNotification) Service.STOP_FOREGROUND_REMOVE else Service.STOP_FOREGROUND_DETACH)
    else -> @Suppress("DEPRECATION") stopForeground(removeNotification)
}

@Suppress("DEPRECATION")
fun PackageManager.getPackageInfoCompat(packageName: String, vararg flagArgs: Int): PackageInfo {
    var flags = 0
    flagArgs.forEach { flag -> flags = flags or flag }
    return if (SDK_INT >= 33) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }
}
