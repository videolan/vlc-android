package org.videolan.vlc.viewmodels

import android.arch.lifecycle.ViewModel
import android.util.Log
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.SupervisorJob


open class ScopedModel : ViewModel(), CoroutineScope {
    protected val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main.immediate+job

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}