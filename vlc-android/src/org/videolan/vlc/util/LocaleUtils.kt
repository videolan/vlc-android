package org.videolan.vlc.util

import android.content.Context
import android.content.ContextWrapper
import org.videolan.tools.wrap
import org.videolan.vlc.VLCApplication

fun Context.getContextWithLocale(): Context {
    VLCApplication.locale.takeIf { !it.isNullOrEmpty() }?.let {
        return ContextWrapper(this).wrap(it)
    }
    return this
}