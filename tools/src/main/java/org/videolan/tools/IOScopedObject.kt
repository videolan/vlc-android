package org.videolan.tools

import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO


open class IOScopedObject : CoroutineScope {
    override val coroutineContext = Dispatchers.IO
}