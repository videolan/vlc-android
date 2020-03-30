/*
 * ************************************************************************
 *  BrowserContainer.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.browser

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.videolan.vlc.interfaces.IEventsHandler

interface BrowserContainer<T> : IEventsHandler<T> {
    fun containerActivity(): Activity

    val scannedDirectory: Boolean
    val mrl: String?
    val isRootDirectory: Boolean
    val isNetwork: Boolean
    val isFile: Boolean
    val inCards: Boolean
}

class BrowserContainerImpl<T>(
        override val scannedDirectory: Boolean,
        override val mrl: String?,
        override val isRootDirectory: Boolean,
        override val isNetwork: Boolean,
        override val isFile: Boolean,
        override val inCards: Boolean
) : BrowserContainer<T> {
    override fun containerActivity() = throw NotImplementedError()
    override fun onClick(v: View, position: Int, item: T) {}
    override fun onLongClick(v: View, position: Int, item: T) = false
    override fun onImageClick(v: View, position: Int, item: T) {}
    override fun onCtxClick(v: View, position: Int, item: T) {}
    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}
    override fun onMainActionClick(v: View, position: Int, item: T) {}
    override fun onItemFocused(v: View, item: T) {}
}