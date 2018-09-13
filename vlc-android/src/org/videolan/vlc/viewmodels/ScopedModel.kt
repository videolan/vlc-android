package org.videolan.vlc.viewmodels

import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.android.Main


open class ScopedModel : ViewModel(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate
}