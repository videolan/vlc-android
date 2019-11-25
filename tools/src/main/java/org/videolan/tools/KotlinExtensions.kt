package org.videolan.tools

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor

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

fun CoroutineScope.conflatedActor(time: Long = 2000L, action: () -> Unit) = actor<Unit>(capacity = Channel.CONFLATED) {
    for (evt in channel) {
        action()
        delay(time)
    }
}

fun Context.getColorFromAttr(
        @AttrRes attrColor: Int,
        typedValue: TypedValue = TypedValue(),
        resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun Context.copy(label: String, text: String) {
    val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.primaryClip = ClipData.newPlainText(label, text)
}

suspend fun retry(
        times: Int = 3,
        delayTime: Long = 500L,
        block: suspend () -> Boolean): Boolean {
    repeat(times - 1) {
        if (block()) return true
        if (delayTime > 0L) delay(delayTime)
    }
    return block() // last attempt
}

suspend fun Context.awaitAppIsForegroung(): Boolean {
    val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
    repeat(times = 2) {
        if (activityManager.isAppForeground()) return true
        else yield() //dispatch next try
    }
    return activityManager.isAppForeground()
}

private fun ActivityManager.isAppForeground() = runningAppProcesses[0].importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND