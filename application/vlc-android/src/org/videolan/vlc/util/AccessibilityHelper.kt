/*
 * ************************************************************************
 *  AccessibilityHelper.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import android.app.Activity
import android.content.Context.ACCESSIBILITY_SERVICE
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.databinding.BindingAdapter
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.medialibrary.media.HistoryItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.R
import org.videolan.vlc.gui.helpers.TalkbackUtil

fun Activity.isTalkbackIsEnabled() = (getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager?)?.isTouchExplorationEnabled
        ?: false

@BindingAdapter("mediaContentDescription")
fun mediaDescription(v: View, media: MediaLibraryItem?) {
    if (media == null) return
    v.contentDescription = when (media) {
        is VideoGroup -> TalkbackUtil.getVideoGroup(v.context, media)
        is Album -> TalkbackUtil.getAlbum(v.context, media)
        is Artist -> TalkbackUtil.getArtist(v.context, media)
        is Folder -> TalkbackUtil.getFolder(v.context, media)
        is Genre -> TalkbackUtil.getGenre(v.context, media)
        is HistoryItem -> v.context.getString(R.string.talkback_history_item)
        is Playlist -> TalkbackUtil.getPlaylist(v.context, media)
        is MediaWrapper -> when (media.type) {
            MediaWrapper.TYPE_VIDEO -> TalkbackUtil.getVideo(v.context, media)
            MediaWrapper.TYPE_AUDIO -> TalkbackUtil.getAudioTrack(v.context, media)
            MediaWrapper.TYPE_STREAM -> TalkbackUtil.getStream(v.context, media)
            MediaWrapper.TYPE_DIR, MediaWrapper.TYPE_SUBTITLE, MediaWrapper.TYPE_PLAYLIST-> TalkbackUtil.getDir(v.context, media, false)
                MediaWrapper.TYPE_ALL -> TalkbackUtil.getAll(media)
            else -> throw NotImplementedError("Media type not found: ${media.type}")
        }
        else -> throw NotImplementedError("Unknown item type")
    }
}
