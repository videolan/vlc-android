/*****************************************************************************
 * browserutils.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
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
 *****************************************************************************/

package org.videolan.vlc.util

import android.net.Uri
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.mediadb.models.BrowserFav
import java.io.File

fun isSchemeSupported(scheme: String?) = when(scheme) {
    "file", "smb", "ssh", "nfs", "ftp", "ftps", "content" -> true
    else -> false
}
fun String?.isSchemeNetwork() = when(this) {
    "smb", "ssh", "nfs", "ftp", "ftps" -> true
    else -> false
}

fun convertFavorites(browserFavs: List<BrowserFav>?) = browserFavs?.filter {
    it.uri.scheme != "file" || File(it.uri.path).exists()
}?.map { (uri, _, title, iconUrl) ->
    MLServiceLocator.getAbstractMediaWrapper(uri).apply {
        setDisplayTitle(Uri.decode(title))
        type = MediaWrapper.TYPE_DIR
        iconUrl?.let { artworkURL = Uri.decode(it) }
        setStateFlags(MediaLibraryItem.FLAG_FAVORITE)
    }
} ?: emptyList()
