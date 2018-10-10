package org.videolan.vlc.util

import android.os.Looper
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.launch
import kotlin.coroutines.experimental.CoroutineContext

fun runBackground(runnable: Runnable) {
    if (Looper.myLooper() != Looper.getMainLooper()) runnable.run()
    else AppScope.launch(Dispatchers.Default) { runnable.run() }
}

fun runOnMainThread(runnable: Runnable) {
    AppScope.launch { runnable.run() }
}

fun runIO(runnable: Runnable) {
    AppScope.launch(Dispatchers.IO) { runnable.run() }
}

object AppScope : CoroutineScope {
    /**
     * @suppress **Deprecated**: Deprecated in favor of top-level extension property
     */
    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Deprecated in favor of top-level extension property")
    override val isActive: Boolean
        get() = true

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main.immediate
}