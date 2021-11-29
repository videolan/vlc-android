package org.videolan.resources.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
@ObsoleteCoroutinesApi
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

/**
 * Blocks the current coroutine while the medialibrary is not ready.
 * Useful when we know the medialibrary init has been launched,
 * we already have an instance of it and we want to wait that it's ready to query it
 */
@ExperimentalCoroutinesApi
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
    if (Medialibrary.getInstance().isStarted || !canReadStorage(this@startMedialibrary)) return@launch
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
            .putExtra(EXTRA_PARSE, parse && scanOpt != ML_SCAN_OFF))
}

suspend fun Context.dbExists(coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) = withContext(coroutineContextProvider.IO) {
    File(getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME).exists()
}

fun Context.launchForeground(intent: Intent) {
    try {
        startService(intent)
    } catch (e: IllegalStateException) {
        //wait for the UI thread to be ready
        val ctx = this
        AppScope.launch(Dispatchers.Main) {
            intent.putExtra("foreground", true)
            ContextCompat.startForegroundService(ctx, intent)
        }
    }
}