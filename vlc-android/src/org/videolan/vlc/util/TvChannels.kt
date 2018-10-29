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
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.*
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import videolan.org.commontools.*


private const val TAG = "VLC/TvChannels"
private const val MAX_RECOMMENDATIONS = 3

@RequiresApi(Build.VERSION_CODES.O)
fun setChannel(context: Context) = GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
    val channelId = withContext(Dispatchers.IO) {
        val prefs = Settings.getInstance(context)
        val name = context.getString(R.string.tv_my_new_videos)
        createOrUpdateChannel(prefs, context, name, R.drawable.ic_channel_icon, BuildConfig.APPLICATION_ID)
    }
    if (Permissions.canReadStorage(context)) updatePrograms(context, channelId)
}

suspend fun updatePrograms(context: Context, channelId: Long) {
    if (channelId == -1L) return
    val ml = Medialibrary.getInstance()
    if (!ml.isStarted) {
        ml.addOnMedialibraryReadyListener(object : Medialibrary.OnMedialibraryReadyListener {
            override fun onMedialibraryIdle() {}
            override fun onMedialibraryReady() {
                ml.removeOnMedialibraryReadyListener(this)
                GlobalScope.launch { updatePrograms(context, channelId) }
            }

        })
        context.startMedialibrary(false, false, false)
        return
    }
    val programs = withContext(Dispatchers.IO) { existingPrograms(context, channelId) }
    val videoList = withContext(Dispatchers.IO) { VLCApplication.getMLInstance().recentVideos }
    if (Util.isArrayEmpty(videoList)) return
    val cn = ComponentName(context, PreviewVideoInputService::class.java)
    for ((count, mw) in videoList.withIndex()) {
        if (mw == null) continue
        val index = programs.indexOfId(mw.id)
        if (index != -1) {
            programs.removeAt(index)
            continue
        }
        val desc = ProgramDesc(channelId, mw.id, mw.title, mw.description,
                mw.artUri(), mw.length.toInt(), mw.time.toInt(),
                mw.width, mw.height, BuildConfig.APPLICATION_ID)
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

fun setResumeProgram(context: Context, mw: MediaWrapper) {
    var cursor: Cursor? = null
    var isProgramPresent = false
    try {
        cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
                null, null)
        cursor?.let {
            while (it.moveToNext()) {
                if (!it.isNull(1) && TextUtils.equals(mw.id.toString(), cursor.getString(1))) {
                    // Found a row that contains the matching ID
                    val watchNextProgramId = cursor.getLong(0)
                    if (it.getInt(2) == 0 || mw.time == 0L) { //Row removed by user or progress null
                        if (deleteWatchNext(context, watchNextProgramId) < 1) {
                            Log.e(TAG, "Delete program failed")
                            return
                        }
                    } else { // Update the program
                        val existingProgram = WatchNextProgram.fromCursor(cursor)
                        updateWatchNext(context, existingProgram, mw.time, watchNextProgramId)
                        isProgramPresent = true
                    }
                    break
                }
            }
        }
        if (!isProgramPresent && mw.time != 0L) {
            val desc = ProgramDesc(0L, mw.id, mw.title, mw.description,
                    mw.artUri(), mw.length.toInt(), mw.time.toInt(),
                    mw.width, mw.height, BuildConfig.APPLICATION_ID)
            val cn = ComponentName(context, PreviewVideoInputService::class.java)
            val program = buildWatchNextProgram(cn, desc)
            val watchNextProgramUri = context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            if (watchNextProgramUri == null || watchNextProgramUri == Uri.EMPTY) Log.e(TAG, "Insert watch next program failed")
        }
    } finally {
        cursor?.close()
    }

}

private fun MediaWrapper.artUri() : Uri {
    val mrl = artworkMrl ?: return Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.ic_browser_video_big_normal}")
    return try {
        getFileUri(mrl)
    } catch (ex: IllegalArgumentException) {
        Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.ic_browser_video_big_normal}")
    }
}