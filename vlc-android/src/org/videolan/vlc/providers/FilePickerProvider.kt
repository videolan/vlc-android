/*****************************************************************************
 * FilePickerProvider.kt
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

package org.videolan.vlc.providers

import android.content.Context
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.util.LiveDataset


class FilePickerProvider(context: Context, dataset: LiveDataset<MediaLibraryItem>, url: String?) : FileBrowserProvider(context, dataset, url, true, false) {

    override fun getFlags(): Int {
        return MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect
    }

    override fun initBrowser() {
        super.initBrowser()
        mediabrowser?.setIgnoreFileTypes("db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv")
    }

    override fun addMedia(media: MediaLibraryItem) {
        if (media is MediaWrapper && (media.type == MediaWrapper.TYPE_SUBTITLE || media.type == MediaWrapper.TYPE_DIR)) super.addMedia(media)
    }

    override fun parseSubDirectories() {}
}