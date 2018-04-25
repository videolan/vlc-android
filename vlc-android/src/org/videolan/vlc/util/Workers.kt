package org.videolan.vlc.util

import android.os.Looper
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext

val VLCIO = newSingleThreadContext("vlc-io")

fun runBackground(runnable: Runnable) {
    if (Looper.myLooper() != Looper.getMainLooper()) runnable.run()
    else launch { runnable.run() }
}

fun runOnMainThread(runnable: Runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) runnable.run()
    else launch(UI) { runnable.run() }
}
