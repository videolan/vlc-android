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

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.tvprovider.media.tv.*
import org.videolan.tools.getResourceUri
import org.videolan.tools.putSingle

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
val WATCH_NEXT_MAP_PROJECTION = arrayOf(
        TvContractCompat.PreviewPrograms._ID,
        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE,
        TvContractCompat.WatchNextPrograms.COLUMN_CONTENT_ID)

@RequiresApi(Build.VERSION_CODES.O)
fun createOrUpdateChannel(prefs: SharedPreferences, context: Context, name: String, icon: Int): Long {
    var channelId = prefs.getLong(KEY_TV_CHANNEL_ID, -1L)
    val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(name)
            .setAppLinkIntentUri(createUri(context.packageName))
    if (channelId == -1L) {
        val channelUri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues()) ?: return -1L
        channelId = ContentUris.parseId(channelUri)
        prefs.putSingle(KEY_TV_CHANNEL_ID, channelId)
        TvContractCompat.requestChannelBrowsable(context, channelId)
        val uri = context.resources.getResourceUri(icon)
        ChannelLogoUtils.storeChannelLogo(context, channelId, uri)
    } else {
        context.contentResolver.update(TvContractCompat.buildChannelUri(channelId),
                builder.build().toContentValues(), null, null)
    }
    return channelId
}

@WorkerThread
fun deleteChannel(context: Context, id: Long) = try {
    context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null)
} catch (exception: Exception) {Log.e(TAG, "faild to delete channel $id", exception)}

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

fun buildProgram(cn: ComponentName, program: ProgramDesc) : PreviewProgram {
    val previewProgramVideoUri = TvContractCompat.buildPreviewProgramUri(program.id)
            .buildUpon()
            .appendQueryParameter("input", TvContractCompat.buildInputId(cn))
            .build()
    val stringId = program.id.toString()
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
            .setIntentUri(createUri(program.appId, stringId))
            .setInternalProviderId(stringId)
            .setContentId(program.contentId)
            .setPreviewVideoUri(previewProgramVideoUri)
            .build()
}

fun buildWatchNextProgram(cn: ComponentName, program: ProgramDesc) : WatchNextProgram {
    val previewProgramVideoUri = TvContractCompat.buildPreviewProgramUri(program.id)
            .buildUpon()
            .appendQueryParameter("input", TvContractCompat.buildInputId(cn))
            .build()
    val stringId = program.id.toString()
    return WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
            .setTitle(program.title)
            .setDurationMillis(program.duration)
            .setLastPlaybackPositionMillis(program.time)
            .setVideoHeight(program.height)
            .setVideoWidth(program.width)
            .setDescription(program.description)
            .setPosterArtUri(program.artUri)
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            .setIntentUri(createUri(program.appId, stringId))
            .setInternalProviderId(stringId)
            .setContentId(program.contentId)
            .setPreviewVideoUri(previewProgramVideoUri)
            .build()
}

fun updateWatchNext(context: Context, program: WatchNextProgram, pDesc: ProgramDesc, watchNextProgramId: Long) {
    val values = WatchNextProgram.Builder(program)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .setLastPlaybackPositionMillis(pDesc.time)
            .setContentId(pDesc.contentId)
            .setPosterArtUri(pDesc.artUri)
            .build().toContentValues()
    val watchNextProgramUri = TvContractCompat.buildWatchNextProgramUri(watchNextProgramId)
    val rowsUpdated = context.contentResolver.update(watchNextProgramUri,values, null, null)
    if (rowsUpdated < 1) Log.e(TAG, "Update program failed")
}

fun deleteWatchNext(context: Context, id: Long) = try {
    context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
} catch (exception: Exception) {
    Log.e(TAG, "faild to delete program $id", exception)
    -42
}

class TvPreviewProgram(val internalId: Long, val programId: Long, val title: String)

fun ProgramsList.indexOfId(id: Long) : Int {
    for ((index, program) in this.withIndex()) if (program.internalId == id) return index
    return -1
}

data class ProgramDesc(
        val channelId: Long,
        val id: Long,
        val title: String,
        val description: String?,
        val artUri: Uri,
        val duration: Int,
        val time: Int,
        val width: Int,
        val height: Int,
        val appId: String,
        val contentId: String,
        val previewVideoUri: Uri? = null)