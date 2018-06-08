package org.videolan.vlc.util

import android.os.Looper
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI

val VLCIO = newSingleThreadContext("vlc-io")
private val mainThread = Looper.getMainLooper().thread!!
private val uiDispatch inline get() = mainThread != Thread.currentThread()

fun runBackground(runnable: Runnable) {
    if (Looper.myLooper() != Looper.getMainLooper()) runnable.run()
    else launch { runnable.run() }
}

fun runOnMainThread(runnable: Runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) runnable.run()
    else launch(UI) { runnable.run() }
}

fun uiJob(dispatch: Boolean = uiDispatch, block: suspend CoroutineScope.() -> Unit) : Job {
    return launch(UI, if (dispatch) CoroutineStart.DEFAULT else CoroutineStart.UNDISPATCHED, block = block)
}