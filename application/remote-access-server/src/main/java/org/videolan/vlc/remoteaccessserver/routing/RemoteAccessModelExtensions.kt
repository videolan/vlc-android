/*
 * ************************************************************************
 *  RemoteAccessModelExtensions.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.vlc.remoteaccessserver.routing

import android.content.Context
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.vlc.remoteaccessserver.R
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.util.generateResolutionClass

fun Album.toPlayQueueItem() = RemoteAccessServer.PlayQueueItem(id, title, albumArtist
        ?: "", duration, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun Artist.toPlayQueueItem(appContext: Context) = RemoteAccessServer.PlayQueueItem(id, title, appContext.resources.getQuantityString(R.plurals.albums_quantity, albumsCount, albumsCount), 0, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun Genre.toPlayQueueItem(appContext: Context) = RemoteAccessServer.PlayQueueItem(id, title, appContext.resources.getQuantityString(R.plurals.track_quantity, tracksCount, tracksCount), 0, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun Playlist.toPlayQueueItem(appContext: Context) = RemoteAccessServer.PlayQueueItem(id, title, appContext.resources.getQuantityString(R.plurals.track_quantity, tracksCount, tracksCount), 0, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun MediaWrapper.toPlayQueueItem(defaultArtist: String = "") = RemoteAccessServer.PlayQueueItem(id, title, artistName?.ifEmpty { defaultArtist } ?: defaultArtist, length, artworkMrl
        ?: "", false, generateResolutionClass(width, height) ?: "", progress = time, played = seen > 0, favorite = isFavorite, path = uri.toString())

fun Folder.toPlayQueueItem(context: Context) = RemoteAccessServer.PlayQueueItem(id, title, context.resources.getQuantityString(org.videolan.vlc.R.plurals.videos_quantity, mediaCount(Folder.TYPE_FOLDER_VIDEO), mediaCount(Folder.TYPE_FOLDER_VIDEO))
        ?: "", 0, artworkMrl
        ?: "", false, "", fileType = "video-folder", favorite = isFavorite)

fun VideoGroup.toPlayQueueItem(context: Context) = RemoteAccessServer.PlayQueueItem(id, title, if (this.mediaCount() > 1) context.resources.getQuantityString(org.videolan.vlc.R.plurals.videos_quantity, this.mediaCount(), this.mediaCount()) else "length", 0, artworkMrl
        ?: "", false, "", played = presentSeen == presentCount && presentCount != 0, fileType = "video-group", favorite = isFavorite)
