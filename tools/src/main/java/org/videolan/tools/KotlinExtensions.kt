package org.videolan.tools

import android.content.res.Resources
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job

fun LifecycleOwner.createJob(cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY): Job = Job().also { job ->
    lifecycle.addObserver(object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun clear() {
            lifecycle.removeObserver(this)
            job.cancel()
        }
    })
}

private val lifecycleCoroutineScopes = mutableMapOf<Lifecycle, CoroutineScope>()

@ExperimentalCoroutinesApi
val LifecycleOwner.coroutineScope: CoroutineScope
    get() = lifecycleCoroutineScopes[lifecycle] ?: createJob().let {
        val newScope = CoroutineScope(it + Dispatchers.Main.immediate)
        lifecycleCoroutineScopes[lifecycle] = newScope
        it.invokeOnCompletion { lifecycleCoroutineScopes -= lifecycle }
        newScope
    }

fun <T> List<T>.getposition(target: T): Int {
    for ((index, item) in withIndex()) if (item == target) return index
    return -1
}

fun LifecycleOwner.isStarted() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

fun View.isVisible() = visibility == View.VISIBLE
fun View.isInvisible() = visibility == View.INVISIBLE
fun View.isGone() = visibility == View.GONE

fun View?.setVisibility(visibility: Int) {
    this?.visibility = visibility
}

fun View?.setVisible() = setVisibility(View.VISIBLE)
fun View?.setInvisible() = setVisibility(View.INVISIBLE)
fun View?.setGone() = setVisibility(View.GONE)

val Int.dp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Int.px: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()