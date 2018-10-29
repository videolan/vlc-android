package org.videolan.vlc.viewmodels

import androidx.lifecycle.ViewModel
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


open class ScopedModel : ViewModel(), CoroutineScope {
    protected val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main.immediate+job

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}