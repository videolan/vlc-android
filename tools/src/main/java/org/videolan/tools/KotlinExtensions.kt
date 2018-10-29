package org.videolan.tools

import androidx.lifecycle.GenericLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job

fun LifecycleOwner.createJob(cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY): Job = Job().also { job ->
    lifecycle.addObserver(object : GenericLifecycleObserver {
        override fun onStateChanged(source: LifecycleOwner?, event: Lifecycle.Event) {
            if (event == cancelEvent) {
                lifecycle.removeObserver(this)
                job.cancel()
            }
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