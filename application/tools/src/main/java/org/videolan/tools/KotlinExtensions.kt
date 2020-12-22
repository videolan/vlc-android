package org.videolan.tools

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.yield
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun <T> List<T>.getposition(target: T): Int {
    for ((index, item) in withIndex()) if (item == target) return index
    return -1
}

fun isAppStarted() = ProcessLifecycleOwner.get().isStarted()

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

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.px: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

fun CoroutineScope.conflatedActor(time: Long = 2000L, action: suspend () -> Unit) = actor<Unit>(capacity = Channel.CONFLATED) {
    for (evt in channel) {
        action()
        if (time > 0L) delay(time)
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
    applicationContext.getSystemService<ClipboardManager>()?.run {
        setPrimaryClip(ClipData.newPlainText(label, text))
    }
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
    val activityManager = applicationContext.getSystemService<ActivityManager>() ?: return false
    repeat(times = 2) {
        if (activityManager.isAppForeground()) return true
        else yield() //dispatch next try
    }
    return activityManager.isAppForeground()
}

private fun ActivityManager.isAppForeground() = runningAppProcesses[0].importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND

@UseExperimental(ExperimentalContracts::class)
fun String?.isValidUrl(): Boolean {
    contract {
        returns(true) implies (this@isValidUrl != null)
    }
    return !isNullOrEmpty() && Patterns.WEB_URL.matcher(this).matches()
}

fun View.clicks(): Flow<Unit> = callbackFlow {
    setOnClickListener { safeOffer(Unit) }
    awaitClose { setOnClickListener(null) }
}

fun <E> SendChannel<E>.safeOffer(value: E) = !isClosedForSend && try {
    offer(value)
} catch (e: CancellationException) {
    false
}

@SuppressLint("MissingPermission")
fun Context.isConnected(): Boolean {
    return getSystemService<ConnectivityManager>()?.activeNetworkInfo?.isConnected == true
}

val Context.localBroadcastManager: LocalBroadcastManager
    get() = LocalBroadcastManager.getInstance(this)

fun Uri?.retrieveParent(): Uri? {
    try {
        if (this == null) return null
        val builder = Uri.Builder().scheme(scheme).authority(authority)
        pathSegments.dropLast(1).forEach {
            if (it != lastPathSegment) builder.appendPath(it)
        }
        return builder.build()
    } catch (e: Exception) {
    }
    return null
}
