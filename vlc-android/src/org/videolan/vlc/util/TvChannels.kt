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

import android.content.Context
import android.net.Uri
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.media.tv.TvContractCompat
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.startMedialibrary
import videolan.org.commontools.*

private const val TAG = "VLC/TvChannels"
private const val MAX_RECOMMENDATIONS = 3

@RequiresApi(Build.VERSION_CODES.O)
fun setChannel(context: Context) = launch(start = CoroutineStart.UNDISPATCHED) {
    val channelId = withContext(VLCIO) {
        val prefs = context.getPreferences()
        val name = context.getString(R.string.tv_my_new_videos)
        createOrUpdateChannel(prefs, context, name, R.drawable.icon, BuildConfig.APPLICATION_ID)
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
                launch { updatePrograms(context, channelId) }
            }

        })
        context.startMedialibrary(false, false, false)
        return
    }
    val programs = withContext(VLCIO) { existingPrograms(context, channelId) }
    val videoList = withContext(VLCIO) { VLCApplication.getMLInstance().recentVideos }
    if (Util.isArrayEmpty(videoList)) return
    for ((count, mw) in videoList.withIndex()) {
        if (mw == null) continue
        val index = programs.indexOfId(mw.id)
        if (index != -1) {
            programs.removeAt(index)
            continue
        }
        val desc = ProgramDesc(channelId, mw.id.toString(), mw.title, mw.description,
                mw.artUri(), mw.length.toInt(), mw.time.toInt(),
                mw.width, mw.height, BuildConfig.APPLICATION_ID)
        val program = buildProgram(desc)
        launch(VLCIO) { context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues()) }
        if (count - programs.size >= MAX_RECOMMENDATIONS) break
    }
    for (program in programs) {
        withContext(VLCIO) { context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(program.programId), null, null) }
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