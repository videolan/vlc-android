/*
 * ************************************************************************
 *  HttpImageLoader.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import org.videolan.vlc.gui.helpers.BitmapCache

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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
            icon = BitmapFactory.decodeStream(inputStream)
            BitmapCache.addBitmapToMemCache(imageUrl, icon)
        } catch (ignored: IOException) {
        } catch (ignored: IllegalArgumentException) {
        } finally {
            Util.close(inputStream)
            urlConnection?.disconnect()
        }
        return icon
    }
}
