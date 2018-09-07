package org.videolan.vlc.util

import android.os.Looper
import android.os.Process
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import java.util.concurrent.ThreadFactory

fun runBackground(runnable: Runnable) {
    if (Looper.myLooper() != Looper.getMainLooper()) runnable.run()
    else launch { runnable.run() }
}

fun runOnMainThread(runnable: Runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) runnable.run()
    else launch(UI) { runnable.run() }
}
