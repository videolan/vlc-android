/*
 * ************************************************************************
 *  TVSearchProvider.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.util.ThumbnailsProvider

class TVSearchProvider : ContentProvider() {
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
            throw UnsupportedOperationException("Requested operation not supported")

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        return if (uri.pathSegments.firstOrNull() == "search") {
            selectionArgs?.firstOrNull()?.let { query ->
                if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Voice search for ${query.replace(Regex("[^A-Za-z0-9 ]"), "")}")
                val medialibrary = AbstractMedialibrary.getInstance()
                val columns = arrayOf(BaseColumns._ID, SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE, SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR, SearchManager.SUGGEST_COLUMN_DURATION)

                val matrixCursor = MatrixCursor(columns)

                val searchAggregate = medialibrary.search(query.replace(Regex("[^A-Za-z0-9 ]"), ""))
                        ?: return null
                searchAggregate.artists?.filterNotNull()?.let {
                    it.forEach { media ->
                        val thumbnail = if (media.artworkMrl != null) getFileUri(media.artworkMrl) else ""
                        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Adding artist ${media.title}")
                        matrixCursor.addRow(arrayOf(media.id, "artist_${media.id}", media.title, media.description, thumbnail, "", -1))
                    }

                }
                searchAggregate.albums?.filterNotNull()?.let {
                    it.forEach { media ->
                        val thumbnail = if (media.artworkMrl != null) getFileUri(media.artworkMrl) else ""
                        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Adding album ${media.title}")
                        matrixCursor.addRow(arrayOf(media.id, "album_${media.id}", media.title, media.description, thumbnail, media.releaseYear, media.duration))
                    }

                }
                searchAggregate.videos?.filterNotNull()?.let {
                    it.forEach { media ->
                        val thumbnail = if (media.artworkURL != null) getFileUri(media.artworkURL) else media.getThumb()
                        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Adding video ${media.title}")
                        matrixCursor.addRow(arrayOf(media.id, "media_${media.id}", media.title, media.description, thumbnail, media.date, media.length))
                    }

                }
                searchAggregate.tracks?.filterNotNull()?.let {
                    it.forEach { media ->
                        val thumbnail = if (media.artworkURL != null) getFileUri(media.artworkURL) else media.getThumb()
                        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Adding track ${media.title}")
                        matrixCursor.addRow(arrayOf(media.id, "media_${media.id}", media.title, media.description, thumbnail, media.releaseYear, media.length))
                    }

                }
                if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Found ${matrixCursor.count} results")
                matrixCursor

            }
        } else {
            throw IllegalArgumentException("Invalid URI: $uri")
        }
    }

    override fun onCreate() = true

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
            throw UnsupportedOperationException("Requested operation not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
            throw UnsupportedOperationException("Requested operation not supported")

    override fun getType(uri: Uri): String? = null
}

private fun AbstractMediaWrapper.getThumb(): Uri {
    if (!isThumbnailGenerated) {
        ThumbnailsProvider.getVideoThumbnail(this@getThumb, 512)
    }
    val mrl = artworkMrl
            ?: return Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.ic_browser_video_big_normal}")
    return try {
        getFileUri(mrl)
    } catch (ex: IllegalArgumentException) {
        Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.ic_browser_video_big_normal}")
    }
}