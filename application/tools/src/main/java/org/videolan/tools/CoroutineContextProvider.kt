package org.videolan.tools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

open class CoroutineContextProvider {
    open val Default by lazy { Dispatchers.Default }
    open val IO by lazy { Dispatchers.IO }
    open val Main: CoroutineDispatcher by lazy { Dispatchers.Main }
}