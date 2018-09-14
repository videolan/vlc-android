package org.videolan.vlc.util

import android.os.Looper
import android.os.Process
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.android.UI
import java.util.concurrent.ThreadFactory

fun runBackground(runnable: Runnable) {
    if (Looper.myLooper() != Looper.getMainLooper()) runnable.run()
    else GlobalScope.launch(Dispatchers.Default) { runnable.run() }
}

fun runOnMainThread(runnable: Runnable) {
    GlobalScope.launch(Dispatchers.Main.immediate) { runnable.run() }
}

fun runIO(runnable: Runnable) {
    GlobalScope.launch(Dispatchers.IO) { runnable.run() }
}
