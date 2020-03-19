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

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AppContextProvider
import org.videolan.tools.BitmapCache
import org.videolan.vlc.R

object BitmapUtil {
    const val TAG = "VLC/UiTools/BitmapUtil"


    fun getPictureFromCache(media: MediaWrapper): Bitmap? {
        // mPicture is not null only if passed through
        // the ctor which is deprecated by now.
        val b = media.picture
        return b ?: BitmapCache.getBitmapFromMemCache(media.location)
    }

    private fun fetchPicture(media: MediaWrapper): Bitmap? {

        val picture = readCoverBitmap(media.artworkURL)
        if (picture != null) BitmapCache.addBitmapToMemCache(media.location, picture)
        return picture
    }

    fun getPicture(media: MediaWrapper): Bitmap? {
        val picture = getPictureFromCache(media)
        return picture ?: fetchPicture(media)
    }

    private fun readCoverBitmap(path: String?): Bitmap? {
        if (path == null) return null
        val ctx = AppContextProvider.appContext
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

    fun getBitmapFromVectorDrawable(context: Context, @DrawableRes drawableId: Int): Bitmap? {
        var drawable: Drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable).mutate()
        }
        return when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            is VectorDrawableCompat, is VectorDrawable -> {
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            else -> BitmapFactory.decodeResource(context.resources, drawableId)
        }
    }
}

fun Context.getBitmapFromDrawable(@DrawableRes drawableId: Int): Bitmap? {
    var drawable: Drawable = try {
        ContextCompat.getDrawable(this, drawableId) ?: return null
    } catch (e: Resources.NotFoundException) {
        VectorDrawableCompat.create(this.resources, drawableId, this.theme)!!
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        drawable = DrawableCompat.wrap(drawable).mutate()
    }
    return when {
        drawable is BitmapDrawable -> drawable.bitmap
        drawable is VectorDrawableCompat || (AndroidUtil.isLolliPopOrLater && drawable is VectorDrawable) -> {
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        else -> BitmapFactory.decodeResource(this.resources, drawableId)
    }
}