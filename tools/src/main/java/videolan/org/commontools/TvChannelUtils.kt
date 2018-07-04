/*****************************************************************************
 * TvChannelUtils.kt
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
package videolan.org.commontools

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.annotation.WorkerThread
import android.support.media.tv.Channel
import android.support.media.tv.ChannelLogoUtils
import android.support.media.tv.PreviewProgram
import android.support.media.tv.TvContractCompat
import android.util.Log

typealias ProgramsList = MutableList<TvPreviewProgram>

private const val TAG = "VLC/TvChannelUtils"
const val TV_CHANNEL_SCHEME = "vlclauncher"
const val TV_CHANNEL_PATH_APP = "startapp"
const val TV_CHANNEL_PATH_VIDEO = "video"
const val TV_CHANNEL_QUERY_VIDEO_ID = "contentId"
const val KEY_TV_CHANNEL_ID = "tv_channel_id"
val TV_PROGRAMS_MAP_PROJECTION = arrayOf(
        TvContractCompat.PreviewPrograms._ID,
        TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        TvContractCompat.PreviewPrograms.COLUMN_TITLE)

@RequiresApi(Build.VERSION_CODES.O)
fun createOrUpdateChannel(prefs: SharedPreferences, context: Context, name: String, icon: Int, appId: String): Long {
    var channelId = prefs.getLong(KEY_TV_CHANNEL_ID, -1L)
    val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(name)
            .setAppLinkIntentUri(createUri(appId))
    if (channelId == -1L) {
        val channelUri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues())
        channelId = ContentUris.parseId(channelUri)
        prefs.edit().putLong(KEY_TV_CHANNEL_ID, channelId).apply()
        TvContractCompat.requestChannelBrowsable(context, channelId)
        val uri = Uri.parse("android.resource://$appId/$icon")
        ChannelLogoUtils.storeChannelLogo(context, channelId, uri)
    } else {
        context.contentResolver.update(TvContractCompat.buildChannelUri(channelId),
                builder.build().toContentValues(), null, null)
    }
    return channelId
}

@WorkerThread
fun deleteChannel(context: Context, id: Long) = context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null)

@WorkerThread
fun existingPrograms(context: Context, channelId: Long) : ProgramsList {
    var cursor: Cursor? = null
    val list = mutableListOf<TvPreviewProgram>()
    try {
        val programUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
        cursor = context.contentResolver.query(
                programUri, TV_PROGRAMS_MAP_PROJECTION, null,
                null, null)
        while (cursor?.moveToNext() == true) {
            val id = cursor.getLong(0)
            val internalId = cursor.getLong(1)
            val title = cursor.getString(2)
            list.add(TvPreviewProgram(internalId, id, title))
        }
        return list
    } catch (e: Exception) {
        Log.e(TAG, "fail", e)
        return list
    } finally {
        cursor?.close()
    }
}

fun createUri(appId: String, id: String? = null) : Uri {
    val builder = Uri.Builder()
            .scheme(TV_CHANNEL_SCHEME)
            .authority(appId)
    if (id != null) builder.appendPath(TV_CHANNEL_PATH_VIDEO)
            .appendQueryParameter(TV_CHANNEL_QUERY_VIDEO_ID, id)
    else builder.appendPath(TV_CHANNEL_PATH_APP)
    return builder.build()
}

fun buildProgram(program: ProgramDesc) : PreviewProgram {
    return PreviewProgram.Builder()
            .setChannelId(program.channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
            .setTitle(program.title)
            .setDurationMillis(program.duration)
            .setLastPlaybackPositionMillis(program.time)
            .setVideoHeight(program.height)
            .setVideoWidth(program.width)
            .setDescription(program.description)
            .setPosterArtUri(program.artUri)
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            .setIntentUri(createUri(program.appId, program.id))
            .setInternalProviderId(program.id)
            .build()
}

class TvPreviewProgram(val internalId: Long, val programId: Long, val title: String)

fun ProgramsList.indexOfId(id: Long) : Int {
    for ((index, program) in this.withIndex()) if (program.internalId == id) return index
    return -1
}

class ProgramDesc(
        val channelId: Long,
        val id: String,
        val title: String,
        val description: String?,
        val artUri: Uri,
        val duration: Int,
        val time: Int,
        val width: Int,
        val height: Int,
        val appId: String)