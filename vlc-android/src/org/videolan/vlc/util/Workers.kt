package org.videolan.vlc.util

import android.os.Looper
import android.os.Process
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.timeunit.TimeUnit
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor


//val VLCIO = newSingleThreadContext("vlc-io")
private val THREAD_FACTORY: ThreadFactory = ThreadFactory { runnable ->
    Thread(runnable).apply { priority = Thread.NORM_PRIORITY + Process.THREAD_PRIORITY_LESS_FAVORABLE }
}

val VLCIO = ThreadPoolExecutor(1, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, SynchronousQueue<Runnable>(), THREAD_FACTORY).asCoroutineDispatcher()
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