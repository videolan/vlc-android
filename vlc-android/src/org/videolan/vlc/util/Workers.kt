package org.videolan.vlc.util

import android.os.Looper
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI

val VLCIO = newSingleThreadContext("vlc-io")

fun runBackground(runnable: Runnable) {
    if (Looper.myLooper() != Looper.getMainLooper()) runnable.run()
    else launch { runnable.run() }
}

fun runOnMainThread(runnable: Runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) runnable.run()
    else launch(UI) { runnable.run() }
}

fun uiJob(block: suspend CoroutineScope.() -> Unit) : Job {
    val dispatch = Looper.getMainLooper() != Looper.myLooper()
    return launch(UI, if (dispatch) CoroutineStart.DEFAULT else CoroutineStart.UNDISPATCHED, block = block)
}