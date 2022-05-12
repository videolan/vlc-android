/*
 * ************************************************************************
 *  WidgetCache.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.widget.utils

import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.mediadb.models.Widget

object WidgetCache {
    private val entries = mutableListOf<WidgetCacheEntry>()

    fun getEntry(widget: Widget): WidgetCacheEntry? {
        entries.forEach { if (it.widget.widgetId == widget.widgetId) return it }
        return null
    }

    fun addEntry(widget: Widget): WidgetCacheEntry {
        val widgetCacheEntry = WidgetCacheEntry(widget, currentCoverInvalidated = true)
        entries.add(widgetCacheEntry)
        return widgetCacheEntry
    }

    fun clear(widget: Widget) {
        var entry: WidgetCacheEntry? = null
        entries.forEach { if (it.widget.widgetId == widget.widgetId) entry = it }
        entry?.let {
            entries.remove(it)
        }
    }

}

data class WidgetCacheEntry(val widget: Widget, var currentMedia: MediaWrapper? = null, var currentCover: String? = null, var palette: Palette? = null, @ColorInt var foregroundColor: Int? = null, var playing: Boolean? = null, var currentCoverInvalidated:Boolean = false) {
    fun reset() {
        currentCover = null
        currentMedia = null
    }
}