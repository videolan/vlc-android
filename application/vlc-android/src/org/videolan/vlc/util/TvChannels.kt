/*****************************************************************************
 * TvChannels.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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

import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.*
import org.videolan.medialibrary.EventTools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PreviewVideoInputService
import org.videolan.vlc.R
import org.videolan.vlc.getFileUri
import videolan.org.commontools.*

private const val TAG = "VLC/TvChannels"
private const val MAX_RECOMMENDATIONS = 3

@RequiresApi(Build.VERSION_CODES.O)
fun setChannel(context: Context) = GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
    val channelId = withContext(Dispatchers.IO) {
        val prefs = Settings.getInstance(context)
        val name = context.getString(R.string.tv_my_new_videos)
        createOrUpdateChannel(prefs, context, name, R.drawable.ic_channel_icon)
    }
    if (Permissions.canReadStorage(context)) updatePrograms(context, channelId)
}

private suspend fun updatePrograms(context: Context, channelId: Long) {
    if (channelId == -1L) return
    val videoList = context.getFromMl { recentVideos }
    val programs = withContext(Dispatchers.IO) { existingPrograms(context, channelId) }
    if (videoList.isNullOrEmpty()) return
    val cn = ComponentName(context, PreviewVideoInputService::class.java)
    for ((count, mw) in videoList.withIndex()) {
        if (mw == null) continue
        val index = programs.indexOfId(mw.id)
        if (index != -1) {
            programs.removeAt(index)
            continue
        }
        if (mw.isThumbnailGenerated) {
            if (mw.artworkMrl === null) continue
        } else if (withContext(Dispatchers.IO) { ThumbnailsProvider.getMediaThumbnail(mw, 272.toPixel()) } === null
                || mw.artworkMrl === null) {
            continue
        }
        val desc = ProgramDesc(channelId, mw.id, mw.title, mw.description,
                mw.artUri(), mw.length.toInt(), mw.time.toInt(),
                mw.width, mw.height, BuildConfig.APP_ID, mw.uri.toString())
        val program = buildProgram(cn, desc)
        GlobalScope.launch(Dispatchers.IO) {
            context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
        }
        if (count - programs.size >= MAX_RECOMMENDATIONS) break
    }
    for (program in programs) {
        withContext(Dispatchers.IO) { context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(program.programId), null, null) }
    }
}

fun Context.launchChannelUpdate() = AppScope.launch {
    val id = withContext(Dispatchers.IO) { Settings.getInstance(this@launchChannelUpdate).getLong(KEY_TV_CHANNEL_ID, -1L) }
    updatePrograms(this@launchChannelUpdate, id)
}

suspend fun insertWatchNext(context: Context, mw: MediaWrapper) {
    val desc = ProgramDesc(0L, mw.id, mw.title, mw.description,
        mw.artUri(), mw.length.toInt(), mw.time.toInt(),
        mw.width, mw.height, BuildConfig.APP_ID, mw.uri.toString())
    val cn = ComponentName(context, PreviewVideoInputService::class.java)
    val program = buildWatchNextProgram(cn, desc)
    val watchNextProgramUri = context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
    if (watchNextProgramUri == null || watchNextProgramUri == Uri.EMPTY) Log.e(TAG, "Insert watch next program failed")
}

suspend fun updateNextProgramAfterThumbnailGeneration(lifecycleOwner: LifecycleOwner, context: Context, mw: MediaWrapper) {
    EventTools.getInstance().lastThumb.observe(lifecycleOwner) { media ->
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
                    null, null)
                cursor?.let {
                    while (it.moveToNext()) {
                        val wnp = WatchNextProgram.fromCursor(it)
                        val existingProgram = WatchNextProgram.fromCursor(cursor)
                        val watchNextProgramId = cursor.getLong(0)
                        val content = wnp.toContentValues()
                        val contentId = content.getAsString(TvContractCompat.PreviewPrograms.COLUMN_CONTENT_ID)
                        if (contentId.toUri() == mw.uri && mw.uri == media.uri) {
                            val desc = ProgramDesc(
                                0L, media.id, media.title, media.description,
                                media.artUri(), media.length.toInt(), media.time.toInt(),
                                media.width, media.height, BuildConfig.APP_ID, media.uri.toString()
                            )
                            updateWatchNext(context, existingProgram, desc, watchNextProgramId)
                        }
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        EventTools.getInstance().lastThumb.removeObservers(lifecycleOwner)
    }
}

suspend fun setResumeProgram(context: Context, media: MediaWrapper) {
    var cursor: Cursor? = null
    var isProgramPresent =  false
    val mw = context.getFromMl { findMedia(media) }
    try {
        cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
                null, null)
        cursor?.let {
            while (it.moveToNext()) {
                if (!it.isNull(1) && mw.id.toString() == cursor.getString(1)) {
                    // Found a row that contains the matching ID
                    isProgramPresent = true
                    val watchNextProgramId = cursor.getLong(0)
                    if (it.getInt(2) == 0 || mw.time == 0L ||
                        (mw.time != 0L &&  mw.time.toDouble() / mw.length.toDouble() >= 0.95) ||
                            mw.length < 20000) { //Row removed by user or progress null
                        if (deleteWatchNext(context, watchNextProgramId) < 1) {
                            Log.e(TAG, "Delete program failed")
                            return
                        }
                    } else { // Update the program
                        val existingProgram = WatchNextProgram.fromCursor(cursor)
                        val desc = ProgramDesc(0L, mw.id, mw.title, mw.description,
                            mw.artUri(), mw.length.toInt(), mw.time.toInt(),
                            mw.width, mw.height, BuildConfig.APP_ID, mw.uri.toString())
                        updateWatchNext(context, existingProgram, desc, watchNextProgramId)
                    }
                    break
                }
            }
        }
        if (!isProgramPresent && mw.time != 0L && (mw.time.toDouble() / mw.length.toDouble() < 0.95) && mw.length > 20000) {
            insertWatchNext(context, mw)
        }
    } finally {
        cursor?.close()
    }

}

suspend fun cleanupWatchNextList(context: Context) {
    var cursor: Cursor?
    try {
        cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
            null, null
        )
        cursor?.let {
            while (it.moveToNext()) {
                val wnp = WatchNextProgram.fromCursor(it)
                val watchNextProgramId = cursor.getLong(0)
                val content = wnp.toContentValues()
                val mediaId = content.getAsString(TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID).toLong()
                val contentId = content.getAsString(TvContractCompat.PreviewPrograms.COLUMN_CONTENT_ID)
                if (contentId != null && contentId == "") {
                    if (deleteWatchNext(context, watchNextProgramId) < 1) {
                        Log.e(TAG, "Delete program failed")
                        continue
                    }
                    val mw = context.getFromMl { getMedia(mediaId) }
                    insertWatchNext(context, mw)
                } else if (contentId != null) {
                    var mw = context.getFromMl { getMedia(contentId.toUri()) }
                    if (mw == null) {
                        if (deleteWatchNext(context, watchNextProgramId) < 1) {
                            Log.e(TAG, "Delete program failed")
                            continue
                        }
                    } else {
                        // Valid content id
                        mw = context.getFromMl { getMedia(mediaId) }
                        if (mw == null || mw.uri.toString() != contentId) {
                            // The path from the media with mediaId and the program differ
                            // this may be due to a medialibrary reindexing. Then delete the
                            // program, then return the valid id for that path. A valid
                            // program will be recreated later if needed.
                            if (deleteWatchNext(context, watchNextProgramId) < 1) {
                                Log.e(TAG, "checkWatchNextId: Delete program failed")
                                continue
                            }
                            mw = context.getFromMl { getMedia(contentId.toUri()) }
                            insertWatchNext(context, mw)
                        }
                        cursor.close()
                    }
                }
            }
            cursor.close()
        }
    } catch (e: Exception) {
        Log.e(TAG, "cleanupWatchNextList: $e")
    }
}

fun deleteAllWatchNext(context: Context) {
    val cursor: Cursor?
    try {
        cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
            null, null
        )
        cursor?.let {
            while (it.moveToNext()) {
                deleteWatchNext(context, cursor.getLong(0))
            }
            cursor.close()
        }
    } catch (e: Exception) {
        Log.e(TAG, "deleteAllWatchNext: $e")
    }
}

// Checks wether the program has contentId (media path), and wether the media id is the right one
// comparing paths from the media from the medialibrary and from the path from the program
suspend fun checkWatchNextId(context: Context, id: Long): Long {
    val cursor: Cursor?
    try {
        cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
            null, null
        )
        cursor?.let {
            while (it.moveToNext()) {
                val wnp = WatchNextProgram.fromCursor(it)
                val content = wnp.toContentValues()
                val watchNextProgramId = cursor.getLong(0)
                val mediaId =
                    content.getAsString(TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID)
                        .toLong()
                if (mediaId == id) {
                    val contentId =
                        content.getAsString(TvContractCompat.PreviewPrograms.COLUMN_CONTENT_ID)
                    if (contentId != null && contentId == "") {
                        // delete the program if there is no content id. If the id is valid and the
                        // media is played, then a new program with correct parameters will be
                        // created if playback is exited before the end.
                        if (deleteWatchNext(context, watchNextProgramId) < 1) {
                            Log.e(TAG, "checkWatchNextId: Delete program failed")
                        }
                        cursor.close()
                        return id
                    } else {
                        var mw = context.getFromMl { getMedia(contentId.toUri()) }
                        if (mw == null) {
                            // There is a content id but it points to a file that was removed
                            if (deleteWatchNext(context, watchNextProgramId) < 1) {
                                Log.e(TAG, "checkWatchNextId: Delete program failed")
                                cursor.close()
                                return id
                            }
                        } else {
                            // Valid content id
                            mw = context.getFromMl { getMedia(mediaId) }
                            var newId = id
                            if (mw == null || mw.uri.toString() != contentId) {
                                // The path from the media with mediaId and the program differ
                                // this may be due to a medialibrary reindexing. Then delete the
                                // program, then return the valid id for that path. A valid
                                // program will be recreated later if needed.
                                if (deleteWatchNext(context, watchNextProgramId) < 1) {
                                    Log.e(TAG, "checkWatchNextId: Delete program failed")
                                }
                                mw = context.getFromMl { getMedia(contentId.toUri()) }
                                newId = mw.id
                            }
                            cursor.close()
                            return newId
                        }
                    }
                }
            }
            cursor.close()
        }
    } catch (e: Exception) {
        Log.e(TAG, "checkWatchNextList: $e")
    }
    return id
}

private suspend fun MediaWrapper.artUri() : Uri {
    if (!isThumbnailGenerated) {
        withContext(Dispatchers.IO) { ThumbnailsProvider.getVideoThumbnail(this@artUri, 512) }
    }

    val resourceUri = "android.resource://${BuildConfig.APP_ID}/${R.drawable.tv_channel_default}".toUri()

    val mrl = artworkMrl ?: return resourceUri
    return try {
        getFileUri(mrl)
    } catch (ex: IllegalArgumentException) {
        resourceUri
    }
}