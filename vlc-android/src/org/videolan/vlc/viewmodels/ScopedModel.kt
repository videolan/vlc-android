package org.videolan.vlc.viewmodels

import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main


open class ScopedModel : ViewModel(), CoroutineScope {
    protected val job = Job()
    override val coroutineContext = Dispatchers.Main.immediate+job

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}