/*
 * *************************************************************************
 *  ContextOption.kt
 * **************************************************************************
 *  Copyright © 2024 VLC authors and VideoLAN
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
 *  ***************************************************************************
 */
package org.videolan.vlc.util

enum class ContextOption : Flag {
    CTX_ADD_FOLDER_AND_SUB_PLAYLIST,
    CTX_ADD_FOLDER_PLAYLIST,
    CTX_ADD_GROUP,
    CTX_ADD_SCANNED,
    CTX_ADD_SHORTCUT,
    CTX_ADD_TO_PLAYLIST,
    CTX_APPEND,
    CTX_BAN_FOLDER,
    CTX_COPY,
    CTX_CUSTOM_REMOVE,
    CTX_DELETE,
    CTX_DOWNLOAD_SUBTITLES,
    CTX_FAV_ADD,
    CTX_FAV_EDIT,
    CTX_FAV_REMOVE,
    CTX_FIND_METADATA,
    CTX_GO_TO_FOLDER,
    CTX_GROUP_SIMILAR,
    CTX_INFORMATION,
    CTX_ITEM_DL,
    CTX_MARK_ALL_AS_PLAYED,
    CTX_MARK_ALL_AS_UNPLAYED,
    CTX_MARK_AS_PLAYED,
    CTX_MARK_AS_UNPLAYED,
    CTX_PLAY,
    CTX_PLAY_ALL,
    CTX_PLAY_AS_AUDIO,
    CTX_PLAY_FROM_START,
    CTX_PLAY_NEXT,
    CTX_PLAY_SHUFFLE,
    CTX_REMOVE_FROM_PLAYLIST,
    CTX_REMOVE_GROUP,
    CTX_RENAME,
    CTX_RENAME_GROUP,
    CTX_SET_RINGTONE,
    CTX_SHARE,
    CTX_STOP_AFTER_THIS,
    CTX_UNGROUP;

    override fun toLong() = 1L shl this.ordinal

    companion object {
        private fun createBaseFlags() = FlagSet(ContextOption::class.java).apply {
            addAll(CTX_ADD_SHORTCUT, CTX_ADD_TO_PLAYLIST, CTX_APPEND)
        }

        fun createCtxVideoFlags() = createBaseFlags().apply {
            addAll(CTX_DELETE, CTX_DOWNLOAD_SUBTITLES, CTX_INFORMATION)
            addAll(CTX_PLAY, CTX_PLAY_ALL, CTX_PLAY_AS_AUDIO, CTX_PLAY_NEXT)
            addAll(CTX_SET_RINGTONE, CTX_SHARE)
        }

        fun createCtxTrackFlags() = createBaseFlags().apply {
            addAll(CTX_DELETE, CTX_GO_TO_FOLDER, CTX_INFORMATION, CTX_PLAY_ALL, CTX_PLAY_NEXT)
            addAll(CTX_SET_RINGTONE, CTX_SHARE)
        }

        fun createCtxAudioFlags() = createBaseFlags().apply {
            addAll(CTX_INFORMATION, CTX_PLAY, CTX_PLAY_NEXT)
        }

        fun createCtxPlaylistAlbumFlags() = createCtxAudioFlags().apply {
            add(CTX_DELETE)
        }

        fun createCtxPlaylistItemFlags() = createBaseFlags().apply {
            addAll(CTX_DELETE, CTX_INFORMATION, CTX_PLAY_NEXT, CTX_SET_RINGTONE)
        }

        fun createCtxVideoGroupFlags() = createBaseFlags().apply {
            remove(CTX_ADD_SHORTCUT)
            addAll(CTX_ADD_GROUP, CTX_MARK_ALL_AS_PLAYED, CTX_MARK_ALL_AS_UNPLAYED, CTX_PLAY_ALL, CTX_RENAME_GROUP, CTX_UNGROUP)
        }

        fun createCtxFolderFlags() = createBaseFlags().apply {
            remove(CTX_ADD_SHORTCUT)
            addAll(CTX_BAN_FOLDER, CTX_MARK_ALL_AS_PLAYED, CTX_MARK_ALL_AS_UNPLAYED, CTX_PLAY_ALL)
        }
    }
}
