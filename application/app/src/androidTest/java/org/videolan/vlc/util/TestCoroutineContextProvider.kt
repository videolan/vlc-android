package org.videolan.vlc.util

import kotlinx.coroutines.Dispatchers
import org.videolan.tools.CoroutineContextProvider

class TestCoroutineContextProvider : CoroutineContextProvider() {
    override val Default by lazy { Dispatchers.Unconfined }
    override val IO by lazy { Dispatchers.Unconfined }
    override val Main by lazy { Dispatchers.Unconfined }
}