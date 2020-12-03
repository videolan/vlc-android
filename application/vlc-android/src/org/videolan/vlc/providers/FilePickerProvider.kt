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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.livedata.LiveDataset

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class FilePickerProvider(context: Context, dataset: LiveDataset<MediaLibraryItem>, url: String?, showDummyCategory: Boolean = false) : FileBrowserProvider(context, dataset, url, true, false, showDummyCategory) {

    override fun getFlags(interact : Boolean) = if (interact) MediaBrowser.Flag.NoSlavesAutodetect
    else MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect

    override fun initBrowser() {
        super.initBrowser()
        mediabrowser?.setIgnoreFileTypes("db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv")
    }

    override suspend fun findMedia(media: IMedia) = MLServiceLocator.getAbstractMediaWrapper(media)?.takeIf { mw ->
        mw.type == MediaWrapper.TYPE_DIR || mw.type == MediaWrapper.TYPE_SUBTITLE
    }

    override fun computeHeaders(value: List<MediaLibraryItem>) {}

    override fun parseSubDirectories(list : List<MediaLibraryItem>?) {}
}
