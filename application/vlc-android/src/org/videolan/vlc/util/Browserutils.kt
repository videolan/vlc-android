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
import org.videolan.resources.AndroidDevices
import org.videolan.vlc.mediadb.models.BrowserFav
import java.io.File

fun isSchemeStreaming(scheme: String?): Boolean = when {
    scheme.isNullOrEmpty() -> false
    isSchemeHttpOrHttps(scheme) -> true
    scheme.startsWith("mms") -> true
    scheme.startsWith("rtsp") -> true
    else -> false
}

fun isSchemeHttpOrHttps(scheme: String?): Boolean = scheme?.startsWith("http") == true

fun isSchemeSupported(scheme: String?) = when(scheme) {
    "file", "smb", "ssh", "nfs", "ftp", "ftps", "ftpes", "content" -> true
    else -> false
}
fun String?.isSchemeNetwork() = when(this) {
    "smb", "ssh", "nfs", "ftp", "ftps", "ftpes", "upnp" -> true
    else -> false
}

fun String?.isSchemeFavoriteEditable() = this in arrayOf("ftp", "ftps", "ftpes", "sftp", "smb", "nfs")

fun String?.isSchemeFile() = when(this) {
    "file", null -> true
    else -> false
}

fun Uri.isOTG() = this.path?.startsWith("/mnt") == true
fun Uri.isSD() = this.path != null && this.path?.startsWith("/storage") == true && this.path?.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY) == false
fun String?.isSchemeSMB() = this == "smb"
fun String?.isSchemeFD() = this == "fd"

fun String?.isSchemeDistant() = !this.isSchemeFile()

fun String.isMissing() = this == "missing://"

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

/**
 * Converts a [BrowserFav] to a [MediaWrapper]
 * @return a [MediaWrapper]
 */
fun convertFavorite(browserFav: BrowserFav): MediaWrapper? {
    return  MLServiceLocator.getAbstractMediaWrapper(browserFav.uri).apply {
        setDisplayTitle(Uri.decode(browserFav.title))
        type = MediaWrapper.TYPE_DIR
        browserFav.iconUrl?.let { artworkURL = Uri.decode(it) }
        setStateFlags(MediaLibraryItem.FLAG_FAVORITE)
    }
}
