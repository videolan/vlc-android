/*
 * ************************************************************************
 *  MediaSortedFragment.java
 * *************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.tv.browser

import android.annotation.TargetApi
import android.net.Uri
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface
import org.videolan.vlc.util.CURRENT_BROWSER_MAP
import org.videolan.vlc.util.KEY_URI
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.BaseModel

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class MediaSortedFragment<T : BaseModel<out MediaLibraryItem>> : CategoriesFragment<T>() {
    protected var uri: Uri? = null
    protected var showHiddenFiles = false


    protected val key: String
        get() = if (uri != null) CURRENT_BROWSER_MAP + uri!!.path!! else CURRENT_BROWSER_MAP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            uri = savedInstanceState.getParcelable(KEY_URI)
        } else {
            val intent = activity!!.intent
            if (intent != null)
                uri = intent.data
        }
        showHiddenFiles = Settings.getInstance(requireContext()).getBoolean("browser_show_hidden_files", false)
    }

    override fun onPause() {
        super.onPause()
        (activity as BrowserActivityInterface).updateEmptyView(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (uri != null) outState.putParcelable(KEY_URI, uri)
    }
}
