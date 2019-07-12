/*****************************************************************************
 * BitmapUtil.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication

object BitmapUtil {
    const val TAG = "VLC/UiTools/BitmapUtil"


    fun getPictureFromCache(media: AbstractMediaWrapper): Bitmap? {
        // mPicture is not null only if passed through
        // the ctor which is deprecated by now.
        val b = media.picture
        return b ?: BitmapCache.getBitmapFromMemCache(media.location)
    }

    private fun fetchPicture(media: AbstractMediaWrapper): Bitmap? {

        val picture = readCoverBitmap(media.artworkURL)
        if (picture != null) BitmapCache.addBitmapToMemCache(media.location, picture)
        return picture
    }

    fun getPicture(media: AbstractMediaWrapper): Bitmap? {
        val picture = getPictureFromCache(media)
        return picture ?: fetchPicture(media)
    }

    private fun readCoverBitmap(path: String?): Bitmap? {
        if (path == null) return null
        val ctx = VLCApplication.appContext ?: return null
        val res = ctx.resources
        var uri = Uri.decode(path)
        if (uri.startsWith("file://")) uri = uri.substring(7)
        var cover: Bitmap? = null
        val options = BitmapFactory.Options()
        val height = res.getDimensionPixelSize(R.dimen.grid_card_thumb_height)
        val width = res.getDimensionPixelSize(R.dimen.grid_card_thumb_width)

        /* Get the resolution of the bitmap without allocating the memory */
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(uri, options)

        if (options.outWidth > 0 && options.outHeight > 0) {
            if (options.outWidth > width) {
                options.outWidth = width
                options.outHeight = height
            }
            options.inJustDecodeBounds = false

            // Decode the file (with memory allocation this time)
            try {
                cover = BitmapFactory.decodeFile(uri, options)
            } catch (e: OutOfMemoryError) {
                cover = null
            }

        }

        return cover
    }


    fun centerCrop(srcBmp: Bitmap, width: Int, height: Int): Bitmap {
        val widthDiff = srcBmp.width - width
        val heightDiff = srcBmp.height - height
        if (widthDiff <= 0 && heightDiff <= 0) return srcBmp
        return try {
            Bitmap.createBitmap(
                    srcBmp,
                    widthDiff / 2,
                    heightDiff / 2,
                    width,
                    height
            )
        } catch (ignored: Exception) {
            srcBmp
        }

    }
}
