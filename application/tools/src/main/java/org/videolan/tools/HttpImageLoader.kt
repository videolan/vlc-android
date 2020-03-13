/*
 * ************************************************************************
 *  HttpImageLoader.kt
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

package org.videolan.tools


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor
import kotlin.math.max

object HttpImageLoader {

    fun downloadBitmap(imageUrl: String): Bitmap? {
        var urlConnection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var icon = BitmapCache.getBitmapFromMemCache(imageUrl)
        if (icon != null) return icon
        try {
            val url = URL(imageUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            inputStream = BufferedInputStream(urlConnection.inputStream)

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, options)
            options.inJustDecodeBounds = false

            //limit image to 150dp for the larger size
            val ratio: Float = max(options.outHeight, options.outWidth).toFloat() / 150.dp.toFloat()
            if (ratio > 1) {
                options.inSampleSize = floor(ratio).toInt()
            }

            urlConnection = url.openConnection() as HttpURLConnection
            inputStream = BufferedInputStream(urlConnection.inputStream)
            icon = BitmapFactory.decodeStream(inputStream, null, options)
            BitmapCache.addBitmapToMemCache(imageUrl, icon)
        } catch (ignored: IOException) {
            Log.e("", ignored.message, ignored)
        } catch (ignored: IllegalArgumentException) {
            Log.e("", ignored.message, ignored)
        } finally {
            CloseableUtils.close(inputStream)
            urlConnection?.disconnect()
        }
        return icon
    }
}
