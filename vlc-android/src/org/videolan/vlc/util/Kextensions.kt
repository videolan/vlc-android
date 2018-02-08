package org.videolan.vlc.util

import kotlinx.coroutines.experimental.delay
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*


fun String.validateLocation(): Boolean {
    var location = this
    /* Check if the MRL contains a scheme */
    if (!location.matches("\\w+://.+".toRegex())) location = "file://$location"
    if (location.toLowerCase(Locale.ENGLISH).startsWith("file://")) {
        /* Ensure the file exists */
        val f: File
        try {
            f = File(URI(location))
        } catch (e: URISyntaxException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (!f.isFile) return false
    }
    return true
}

suspend fun retry (
        times: Int = 3,
        delayTime: Long = 500L,
        block: suspend () -> Boolean): Boolean
{
    repeat(times - 1) {
        if (block()) return true
        delay(delayTime)
    }
    return block() // last attempt
}