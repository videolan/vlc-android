/*
 * ************************************************************************
 *  KExtensions.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.util

import android.content.Intent
import android.provider.MediaStore.Video.VideoColumns.CATEGORY
import androidx.fragment.app.FragmentActivity
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.CATEGORY_ALBUMS
import org.videolan.resources.HEADER_CATEGORIES
import org.videolan.television.ui.BrowserActivity
import org.videolan.television.ui.EXTRA_ITEM
import org.videolan.television.ui.MainTvActivity
import org.videolan.tools.Settings
import org.videolan.tools.retrieveParent

fun FragmentActivity.showParent(media: MediaWrapper) {
    val parent = MLServiceLocator.getAbstractMediaWrapper(media.uri.retrieveParent()).apply {
        type = MediaWrapper.TYPE_DIR
    }
    if (Settings.showTvUi) {
        val intent = Intent(this, BrowserActivity::class.java)
        intent.putExtra(EXTRA_ITEM, parent)
        intent.putExtra(CATEGORY, CATEGORY_ALBUMS)
        intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_CATEGORIES)
        startActivity(intent)
    }
}